package com.example.safetymap.data.api

import com.example.safetymap.data.model.ComputeRoutesRequest
import com.example.safetymap.data.model.ComputeRoutesResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface RoutesApi {
    @POST("directions/v2:computeRoutes")
    suspend fun computeRoutes(
        @Header("X-Goog-Api-Key") apiKey: String,
        // The Field Mask tells Google ONLY to return the polyline, saving data and time!
        @Header("X-Goog-FieldMask") fieldMask: String = "routes.polyline.encodedPolyline",
        @Body request: ComputeRoutesRequest
    ): ComputeRoutesResponse
}