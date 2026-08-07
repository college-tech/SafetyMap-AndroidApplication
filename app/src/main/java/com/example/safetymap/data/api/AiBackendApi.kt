package com.example.safetymap.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

data class RouteSegment(val latitude: Double, val longitude: Double)

// Payload wrapping all coordinates of a single route option
data class AnalyzeRouteRequest(val route_points: List<RouteSegment>)

// Server response structure for an individual alternative route path
data class AnalyzedRoute(
    val route_index: Int,
    val safety_score: Double,
//    val open_shops_count: Int,
//    val nearest_hospital_km: Double,
//    val nearest_police_km: Double,
    val xai_explanation: String
)

// Master payload response containing an unpredictable number of analyzed options
data class AnalyzeRouteResponse(
    val status: String,
    val routes_analyzed: List<AnalyzedRoute>
)

interface AiBackendApi {
    @POST("api/analyze-route")
    suspend fun analyzeRoute(@Body request: AnalyzeRouteRequest): AnalyzeRouteResponse
}

object AiRetrofitClient {
    private const val BASE_URL = "http://10.233.176.58:8000/"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS) // Time to establish connection
        .readTimeout(60, TimeUnit.SECONDS)    // Time to wait for server response
        .writeTimeout(60, TimeUnit.SECONDS)   // Time to send data to server
        .build()

    val api: AiBackendApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient) // Add the custom client here
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AiBackendApi::class.java)
    }
}