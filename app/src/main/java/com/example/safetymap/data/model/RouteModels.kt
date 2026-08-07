package com.example.safetymap.data.model


// --- THE REQUEST (What we send to Google) ---
data class ComputeRoutesRequest(
    val origin: Waypoint,
    val destination: Waypoint,
    val travelMode: String = "WALK",
    val computeAlternativeRoutes: Boolean = true
)

data class Waypoint(val location: RouteLocation)
data class RouteLocation(val latLng: RouteLatLng)
data class RouteLatLng(val latitude: Double, val longitude: Double)

// --- THE RESPONSE (What Google sends back) ---
data class ComputeRoutesResponse(val routes: List<Route>?)
data class Route(val polyline: RoutePolyline?)
data class RoutePolyline(val encodedPolyline: String?)
