package com.example.safetymap.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.safetymap.data.api.AiRetrofitClient
import com.example.safetymap.data.api.AnalyzeRouteRequest
import com.example.safetymap.data.api.RouteSegment
import com.example.safetymap.data.repository.RouteRepository
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.maps.android.PolyUtil
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.safetymap.data.api.AnalyzedRoute
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 1. Create a Traffic Cop to know which box the user is typing in
enum class SearchFocus { NONE, EXPLORE, SOURCE, DESTINATION }

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val placesClient = Places.createClient(application)

    // --- UI STATE ---
    // Toggle between single search (false) and dual search (true)
    val isRoutingMode = MutableStateFlow(false)
    val currentFocus = MutableStateFlow(SearchFocus.NONE)

    // Text box contents
    val exploreQuery = MutableStateFlow("")
    val sourceQuery = MutableStateFlow("My Location")
    val destQuery = MutableStateFlow("")

    // Shared list of dropdown suggestions
    val searchResults = MutableStateFlow<List<AutocompletePrediction>>(emptyList())

    // Final coordinates
    val sourceLocation = MutableStateFlow<LatLng?>(null)
    val destLocation = MutableStateFlow<LatLng?>(null)

    //error when server not on
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    // --- PLACES API
    fun searchPlaces(query: String, focus: SearchFocus) {
        currentFocus.value = focus
        if (focus == SearchFocus.SOURCE && query == "My Location") {
            sourceQuery.value = ""
            return
        }
        // Update the correct text box
        when(focus) {
            SearchFocus.EXPLORE -> exploreQuery.value = query
            SearchFocus.SOURCE -> sourceQuery.value = query
            SearchFocus.DESTINATION -> destQuery.value = query
            else -> return
        }

        if (query.isEmpty() || query == "My Location") {
            searchResults.value = emptyList()
            return
        }

        val request = FindAutocompletePredictionsRequest.builder().setQuery(query).build()
        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response -> searchResults.value = response.autocompletePredictions }
            .addOnFailureListener { e -> android.util.Log.e("MAP_DEBUG", "Search Error: ${e.message}") }
    }


    // --- MODULE 1: INDEPENDENT EXPLORE STATE ---
    val selectedExploreLocation = MutableStateFlow<LatLng?>(null)
    val selectedExploreName = MutableStateFlow<String>("")
    val showExploreCard = MutableStateFlow(false)
    val selectedExploreViewport = MutableStateFlow<com.google.android.gms.maps.model.LatLngBounds?>(null)


    fun getPlaceCoordinates(placeId: String, textToDisplay: String) {
        val request = FetchPlaceRequest.newInstance(placeId, listOf(Place.Field.LAT_LNG, Place.Field.VIEWPORT))

        placesClient.fetchPlace(request).addOnSuccessListener { response ->
            val latLng = response.place.latLng
            val viewport = response.place.viewport // 2. Extract the viewport box

            when(currentFocus.value) {
                SearchFocus.EXPLORE -> {
                    selectedExploreLocation.value = latLng
                    selectedExploreViewport.value = viewport // 3. Save it to our new state!
                    selectedExploreName.value = textToDisplay
                    exploreQuery.value = textToDisplay
                    showExploreCard.value = true
                }
                SearchFocus.SOURCE -> {
                    // MODULE 2: Save silently in background, NO camera jumps or markers
                    sourceLocation.value = latLng
                    sourceQuery.value = textToDisplay
                    selectedExploreLocation.value = latLng
                }
                SearchFocus.DESTINATION -> {
                    // MODULE 2: Save silently in background, NO camera jumps or markers
                    destLocation.value = latLng
                    destQuery.value = textToDisplay
                }
                else -> {}
            }

            // Clean up UI search focus
            searchResults.value = emptyList()
            currentFocus.value = SearchFocus.NONE
        }
    }

    fun transitionExploreToDirections() {
        // 1. Pre-populate Destination from the active exploration marker
        destLocation.value = selectedExploreLocation.value
        destQuery.value = selectedExploreName.value

        // 2. Open the Module 2 Routing Panel
        isRoutingMode.value = true

        // 3. Clear Module 1 standalone states
        selectedExploreLocation.value = null
        selectedExploreName.value = ""
        showExploreCard.value = false
    }

    fun clearExploreState() {
        selectedExploreLocation.value = null
        selectedExploreName.value = ""
        exploreQuery.value = ""
        showExploreCard.value = false
        selectedExploreViewport.value = null
    }

    // --- ROUTES API LOGIC ---
    private val routeRepository = RouteRepository()
    val routePoints = MutableStateFlow<List<List<LatLng>>>(emptyList())
    val isRouteDrawn = MutableStateFlow(false)

    // --- NAVIGATION STATE ---
    val isNavigating = MutableStateFlow(false)
    val liveLocation = MutableStateFlow<LatLng?>(null)
    private var isRecalculating = false

    fun startNavigation() {
        isNavigating.value = true
    }

    fun stopNavigation() {
        isNavigating.value = false
        clearRoute()
    }

    fun updateLiveLocation(location: LatLng, apiKey: String) {
        liveLocation.value = location

        if (isNavigating.value && routePoints.value.isNotEmpty() && !isRecalculating) {
            val primaryRoute = routePoints.value[0]
            val isStillOnRoute = PolyUtil.isLocationOnPath(location, primaryRoute, true, 50.0)

            if (!isStillOnRoute) {
                recalculateRoute(location, apiKey)
            }
        }
    }

    private fun recalculateRoute(newOrigin: LatLng, apiKey: String) {
        isRecalculating = true
        val dest = destLocation.value

        if (dest != null) {
            viewModelScope.launch {
                val newRoutes = routeRepository.fetchRoute(newOrigin, dest, apiKey)
                if (!newRoutes.isNullOrEmpty()) {
                    routePoints.value = newRoutes
                    sourceLocation.value = newOrigin
                }
                isRecalculating = false
            }
        } else {
            isRecalculating = false
        }
    }

    fun fetchRoute(apiKey: String) {
        val origin = sourceLocation.value
        val dest = destLocation.value

        if (origin != null && dest != null) {
            viewModelScope.launch {
                val routes = routeRepository.fetchRoute(origin, dest, apiKey)
                if (routes != null) {
                    routePoints.value = routes
                    isRouteDrawn.value = true
                    // Trigger AI analysis for the primary route
                    if (routes.isNotEmpty()) {
                        analyzeAllAlternativeRoutes(routes)
                    }
                }
            }
        }
    }
    fun clearRoute() {
        isRouteDrawn.value = false
        routePoints.value = emptyList()
    }

    // Helper to reset the UI
    fun toggleRoutingMode(enable: Boolean) {
        isRoutingMode.value = enable
        searchResults.value = emptyList()
        if (!enable) {
            clearRoute()
            destQuery.value = ""
        }
    }

    // --- AI ANALYSIS STATE ---
    // --- XAI ANALYSIS STATE ---
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing = _isAnalyzing.asStateFlow()

    // Holds safety metrics and XAI explanations for all returned paths
    private val _analyzedRoutes = MutableStateFlow<List<AnalyzedRoute>>(emptyList())
    val analyzedRoutes = _analyzedRoutes.asStateFlow()

    // Tracks which route the user clicked on the map
    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex = _selectedRouteIndex.asStateFlow()

    fun selectRoute(index: Int) {
        _selectedRouteIndex.value = index
    }

    // Call this RIGHT AFTER Google gives you the route in fetchRoute()
    // Call this inside your fetchRoute function right after Google gives you the routes
    fun analyzeAllAlternativeRoutes(allGoogleRoutes: List<List<LatLng>>) {
        viewModelScope.launch {
            _isAnalyzing.value = true
            _analyzedRoutes.value = emptyList() // Clear previous search
            _selectedRouteIndex.value = 0       // Reset click to first route

            try {
                val temporaryList = mutableListOf<AnalyzedRoute>()

                // Process each route option one by one
                for (index in allGoogleRoutes.indices) {
                    val pathCoordinates = allGoogleRoutes[index]
                    val segments = pathCoordinates.map { RouteSegment(it.latitude, it.longitude) }
                    val request = AnalyzeRouteRequest(segments)

                    // Send to your Python Server
                    val response = AiRetrofitClient.api.analyzeRoute(request)

                    if (response.status == "success") {
                        // The server now sends back an array of analyzed routes
                        // We grab the first one because our server currently processes one request per loop
                        val routeData = response.routes_analyzed.firstOrNull()
                        if (routeData != null) {
//                            temporaryList.add(routeData.copy(route_index = index))
                            _analyzedRoutes.value = _analyzedRoutes.value + routeData.copy(route_index = index)
                            kotlinx.coroutines.yield()
                        }
                    }
                }
//                _analyzedRoutes.value = temporaryList

            } catch (e: Exception) {
                android.util.Log.e("XAI_PIPELINE", "AI Backend Failed: ${e.message}")
                _errorMessage.value = "Cannot connect to AI Server. Please check your internet."
            } finally {
                _isAnalyzing.value = false
            }
        }
    }
    fun clearError() {
        _errorMessage.value = null
    }
}


