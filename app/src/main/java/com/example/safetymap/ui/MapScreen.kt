package com.example.safetymap.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import android.os.Looper
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
//import androidx.privacysandbox.tools.core.generator.build
import com.google.android.gms.maps.model.LatLngBounds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(viewModel: MapViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appInfo = context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
    val apiKey = appInfo.metaData.getString("com.google.android.geo.API_KEY") ?: ""

    // --- NAVIGATION STATE ---
    // (State moved to ViewModel)

    // State Collections
    val isRoutingMode by viewModel.isRoutingMode.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val allRoutes by viewModel.routePoints.collectAsState()
    val routeList by viewModel.routePoints.collectAsState() // Renamed to routeList
    val isRouteDrawn by viewModel.isRouteDrawn.collectAsState() // The new visibility toggle
    val showExploreCard by viewModel.showExploreCard.collectAsState()
    val exploreName by viewModel.selectedExploreName.collectAsState()

    val isAnalyzing by viewModel.isAnalyzing.collectAsState()

    // Text Query States
    val exploreQuery by viewModel.exploreQuery.collectAsState()
    val sourceQuery by viewModel.sourceQuery.collectAsState()
    val destQuery by viewModel.destQuery.collectAsState()
    val exploreViewport by viewModel.selectedExploreViewport.collectAsState()
    // Map State
    val destLocation by viewModel.destLocation.collectAsState()

    // Permissions & GPS Location
    var hasLocationPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasLocationPermission = it }
    val fusedLocationClient = remember { com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context) }

    val cameraPositionState = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(LatLng(22.5726, 88.3639), 12f) }

    // continuous navigation
    val isNavigating by viewModel.isNavigating.collectAsState()
    val liveLocation by viewModel.liveLocation.collectAsState()

    // Priority 1: If navigating, back button safely stops tracking and clears maps
    BackHandler(enabled = isNavigating) {
        viewModel.stopNavigation()
    }

    // Priority 2: If a route layout is drawn, back button clears lines and opens input panel
    BackHandler(enabled = isRouteDrawn && !isNavigating) {
        viewModel.clearRoute()
    }

    // Priority 3: If in Routing Mode, back button drops back to Single Search Mode
    BackHandler(enabled = isRoutingMode && !isRouteDrawn) {
        viewModel.toggleRoutingMode(false)
    }

    // Priority 4: If an independent place search card is open, back button clears the marker
    BackHandler(enabled = showExploreCard && !isRoutingMode) {
        viewModel.clearExploreState()
    }
    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    // 1. Set the routing default source
                    viewModel.sourceLocation.value = currentLatLng

                    // 2. NEW: Immediately animate the camera to the user's location on startup!
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    }
                }
            }
        }
    }

    // CONTINUOUS GPS TRACKING
    DisposableEffect(isNavigating) {
        var locationCallback: LocationCallback? = null

        if (isNavigating && hasLocationPermission) {
            // 1. Tell Android we want high accuracy updates every 3 seconds
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1000)
                .build()

            // 2. Define what happens every time the GPS gets a new coordinate
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { loc ->
                        val newPos = LatLng(loc.latitude, loc.longitude)
                        // Pass the location AND the apiKey so the ViewModel can reroute if needed!
                        viewModel.updateLiveLocation(newPos, apiKey)
                    }
                }
            }

            // 3. Start the stream!
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }

        // 4. This runs when navigation stops, killing the stream to save battery
        onDispose {
            locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        }
    }
    // FOLLOW CAMERA DURING NAVIGATION
    LaunchedEffect(liveLocation) {
        if (isNavigating && liveLocation != null) {
            // Animate the camera to follow the live location, keeping zoom level at 18f (street level)
            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(liveLocation!!, 18f))
        }
    }

    // ANIMATE CAMERA TO NEW DESTINATION
    // Animate camera to destination ONLY during standalone search mode
    LaunchedEffect(destLocation, isRoutingMode) {
        if (!isRoutingMode) {
            destLocation?.let { dest ->
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(dest, 15f))
            }
        }
    }

    // Animate camera to exploration target instantly
    val exploreLocation by viewModel.selectedExploreLocation.collectAsState()
    // ✅ PASTE THIS NEW BLOCK:
    LaunchedEffect(exploreLocation, exploreViewport) {
        if (!isRoutingMode) {
            if (exploreViewport != null) {
                // If it's a large area (like a city), frame the whole bounding box with 100px padding
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(exploreViewport!!, 100))
            } else if (exploreLocation != null) {
                // Fallback: If it's a specific tiny point, just zoom to 15f
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(exploreLocation!!, 15f))
            }
        }
    }

    val routePoints by viewModel.routePoints.collectAsState()

    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            val allPoints = routePoints.flatten() // Combine all paths into one list
            if (allPoints.isNotEmpty()) {
                val builder = LatLngBounds.builder()
                allPoints.forEach { builder.include(it) }
                val bounds = builder.build()

                // Animate camera to fit the whole route with some padding (e.g., 100px)
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 100))
            }
        }
    }

    // Put this right below your other 'collectAsState' variables
    val errorMessage by viewModel.errorMessage.collectAsState()

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            // Show the popup
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()

            // Use the public function to clear it! (NO MORE RED LINE)
            viewModel.clearError()
        }
    }

    // Map UI
    Box(modifier = Modifier.fillMaxSize()) {

        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false)
        ) {
            // MODULE 1: Standalone search marker (Only shows when explicitly exploring a single spot)
            val exploreLocation by viewModel.selectedExploreLocation.collectAsState()
            val exploreName by viewModel.selectedExploreName.collectAsState()
            if (exploreLocation != null && !isRoutingMode) {
                Marker(state = MarkerState(position = exploreLocation!!), title = exploreName)
            }

            // MODULE 2: Routing destination marker
            if (destLocation != null && isRoutingMode) {
                Marker(state = MarkerState(position = destLocation!!), title = "Destination")
            }

            // XAI Clickable Routes Polyline Loop
            val allRoutes by viewModel.routePoints.collectAsState()
            val analyzedRoutesList by viewModel.analyzedRoutes.collectAsState()
            val selectedRouteIdx by viewModel.selectedRouteIndex.collectAsState()

            allRoutes.forEachIndexed { index, pathPoints ->
                val metricData = analyzedRoutesList.find { it.route_index == index }
                val lineBaseColor = when {
                    metricData == null -> Color.Gray
                    metricData.safety_score > 3.5 -> Color(0xFF4CAF50)
                    metricData.safety_score > 2.5 -> Color(0xFFFFB300)
                    else -> Color(0xFFE53935)
                }

                val isCurrentSelection = selectedRouteIdx == index
                val lineAlpha = if (isCurrentSelection) 1.0f else 0.35f
                val lineThickness = if (isCurrentSelection) 14f else 8f

                Polyline(
                    points = pathPoints,
                    clickable = true,
                    color = lineBaseColor.copy(alpha = lineAlpha),
                    width = lineThickness,
                    onClick = { viewModel.selectRoute(index) }
                )
            }
        }

        // --- TOP UI: SEARCH BARS & CLEAR BUTTON ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .padding(top = 32.dp)
        ) {

            // 1. THE SEARCH BARS (Hidden when a route is on the screen)
            AnimatedVisibility(
                visible = !isRouteDrawn,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Crossfade(targetState = isRoutingMode, label = "ModuleToggle") { routingActive ->
                        if (!routingActive) {
                            // MODULE 1: Single clean explorer bar
                            SearchBarComponent(
                                query = exploreQuery,
                                placeholder = "Search places...",
                                icon = Icons.Default.Search,
                                onQueryChange = { viewModel.searchPlaces(it, SearchFocus.EXPLORE) }
                            )
                        } else {
                            // MODULE 2: Dedicated dual routing inputs panel
                            Card(
                                modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color.White)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { viewModel.toggleRoutingMode(false) }) {
                                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                        }
                                        SearchBarComponent(
                                            query = sourceQuery,
                                            placeholder = "Choose starting point",
                                            icon = Icons.Default.MyLocation,
                                            onQueryChange = { viewModel.searchPlaces(it, SearchFocus.SOURCE) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Spacer(modifier = Modifier.width(48.dp))
                                        SearchBarComponent(
                                            query = destQuery,
                                            placeholder = "Choose destination",
                                            icon = Icons.Default.LocationOn,
                                            onQueryChange = { viewModel.searchPlaces(it, SearchFocus.DESTINATION) }
                                        )
                                    }
                                    Button(
                                        onClick = { viewModel.fetchRoute(apiKey) },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 48.dp, end = 8.dp)
                                    ) {
                                        Text("Calculate Safe Routes")
                                    }
                                }
                            }
                        }
                    }

                    // Shared Autocomplete Dropdown List
                    if (searchResults.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp).shadow(4.dp)) {
                            LazyColumn(modifier = Modifier.background(Color.White)) {
                                items(searchResults) { prediction ->
                                    val text = prediction.getFullText(null).toString()
                                    Text(
                                        text = text,
                                        color = Color.Black,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.getPlaceCoordinates(prediction.placeId, text) }
                                            .padding(16.dp)
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            } // End of Search Bars AnimatedVisibility

            // 2. THE "X" BUTTON (Appears ONLY when the route is drawn)
            AnimatedVisibility(
                visible = isRouteDrawn,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FloatingActionButton(
                    onClick = { viewModel.clearRoute() }, // Tells the ViewModel to erase the route
                    containerColor = Color.White,
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Route", tint = Color.Black)
                }
            }
        }
        val analyzedRoutesList by viewModel.analyzedRoutes.collectAsState()
        // --- FINAL TOUCH: AI PROCESSING OVERLAY ---
        if (isAnalyzing && analyzedRoutesList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)) // Dims the background
                    .clickable(enabled = false) {}, // Prevents random clicks while loading
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.padding(32.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = Color(0xFF4CAF50)) // Green spinner
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "AI is analyzing street safety...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black
                        )
                    }
                }
            }
        }

//        // --- MODULE 1: PLACE DETAIL BOTTOM CARD OVERLAY ---
//        val showExploreCard by viewModel.showExploreCard.collectAsState()
//        val exploreName by viewModel.selectedExploreName.collectAsState()

        AnimatedVisibility(
            visible = showExploreCard && !isRoutingMode && !isRouteDrawn,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).padding(bottom = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().shadow(12.dp, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = exploreName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Black
                    )
                    Text(
                        text = "Location selected on map",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearExploreState() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Clear")
                        }
                        Button(
                            onClick = { viewModel.transitionExploreToDirections() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Directions")
                        }
                    }
                }
            }
        }

        // --- XAI TEXT EXPLANATION OVERLAY ---
        val isAnalyzingXai by viewModel.isAnalyzing.collectAsState()
        val currentAnalysisList by viewModel.analyzedRoutes.collectAsState()
        val highlightedIndex by viewModel.selectedRouteIndex.collectAsState()
        val activeRouteMetric = currentAnalysisList.find { it.route_index == highlightedIndex }

        Box(
            modifier = Modifier.fillMaxWidth().padding(top = 100.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // SHOW LOADING SPINNER
            AnimatedVisibility(
                visible = isRouteDrawn && isAnalyzingXai,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Analyzing alternative paths with AI...", color = Color.DarkGray)
                    }
                }
            }

            // SHOW ACTUAL XAI TEXT CARD WHEN DONE
            AnimatedVisibility(
                visible = isRouteDrawn && !isAnalyzingXai && activeRouteMetric != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if ((activeRouteMetric?.safety_score ?: 0.0) > 3.5) Color(0xFFF1F8E9) else Color(0xFFFFF3E0)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "Safety Score",
                                tint = if ((activeRouteMetric?.safety_score ?: 0.0) > 3.5) Color(0xFF4CAF50) else Color(0xFFF57C00)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Route Option #${highlightedIndex + 1} (Score: ${String.format("%.2f", activeRouteMetric?.safety_score)}/5)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Black
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = activeRouteMetric?.xai_explanation ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.DarkGray
                        )
                    }
                }
            }
        }

        // BOTTOM RIGHT UI
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.End, // Aligns both buttons to the right edge
            verticalArrangement = Arrangement.spacedBy(16.dp) // Adds a nice gap between the buttons
        ) {

            // 1. THE NEW "LOCATE ME" BUTTON
            // We make this visible only when we have permission so it doesn't crash!
            if (hasLocationPermission) {
                FloatingActionButton(
                    onClick = {
                        // Ask the phone for the latest GPS ping, then animate the camera there!
                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                            if (location != null) {
                                scope.launch {
                                    val currentLatLng = LatLng(location.latitude, location.longitude)
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                                }
                            }
                        }
                    },
                    // Give it a distinct white color so it doesn't clash with the primary button
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.primary,
                    // Make it slightly smaller than the main directions button
                    modifier = Modifier.size(50.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Locate Me")
                }
            }

            // 2. THE DIRECTIONS BUTTON
            AnimatedVisibility(
                visible = !isRoutingMode,
                enter = scaleIn(),
                exit = scaleOut()
            ) {
                FloatingActionButton(
                    onClick = { viewModel.toggleRoutingMode(true) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.Black
                ) {
                    Icon(Icons.Default.Directions, contentDescription = "Directions")
                }
            }
        }

        // --- BOTTOM UI: START/STOP NAVIGATION ---
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isRouteDrawn && !isNavigating,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.startNavigation() },
                    containerColor = Color(0xFF4CAF50), // Green Start color
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    text = { Text("Start Journey") }
                )
            }

            AnimatedVisibility(
                visible = isNavigating,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.stopNavigation() },
                    containerColor = Color.Red, // Red Stop color
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.Stop, contentDescription = null) },
                    text = { Text("End Journey") }
                )
            }
        }
    }
}

//Reusable Search Bar Component
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBarComponent(
    query: String,
    placeholder: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onQueryChange: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Color.Black) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.Black)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation = if (placeholder == "Search places...") 8.dp else 0.dp, shape = RoundedCornerShape(24.dp))
            .background(Color.White, RoundedCornerShape(24.dp)),
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedContainerColor = if (placeholder == "Search places...") Color.White else Color(0xFFF1F3F4),
            unfocusedContainerColor = if (placeholder == "Search places...") Color.White else Color(0xFFF1F3F4)
        ),
        shape = RoundedCornerShape(24.dp),
        singleLine = true
    )
}