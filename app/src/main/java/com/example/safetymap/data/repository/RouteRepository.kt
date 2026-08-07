package com.example.safetymap.data.repository

import com.example.safetymap.data.api.RetrofitClient
import com.example.safetymap.data.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil

class RouteRepository {

    // Notice the return type is now a List of paths (List<List<LatLng>>)
    suspend fun fetchRoute(origin: LatLng, destination: LatLng, apiKey: String): List<List<LatLng>>? {
        return try {
            val requestBody = ComputeRoutesRequest(
                origin = Waypoint(RouteLocation(RouteLatLng(origin.latitude, origin.longitude))),
                destination = Waypoint(RouteLocation(RouteLatLng(destination.latitude, destination.longitude)))
            )

            val response = RetrofitClient.api.computeRoutes(
                apiKey = apiKey,
                request = requestBody
            )

            // Map over ALL returned routes and decode them!
            val allRoutes = response.routes?.mapNotNull { route ->
                route.polyline?.encodedPolyline?.let { PolyUtil.decode(it) }
            }

            allRoutes
        } catch (e: Exception) {
            android.util.Log.e("MAP_DEBUG", "Routes API failed: ${e.message}")
            null
        }
    }
}