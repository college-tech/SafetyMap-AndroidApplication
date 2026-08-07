package com.example.safetymap.data.api

//import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
//import java.util.concurrent.TimeUnit
object RetrofitClient {
    // The modern Routes API base URL
//    private val okHttpClient = OkHttpClient.Builder()
//        .connectTimeout(60, TimeUnit.SECONDS) // Time to connect to server
//        .readTimeout(60, TimeUnit.SECONDS)    // Time to wait for the AI to finish!
//        .writeTimeout(60, TimeUnit.SECONDS)
//        .build()
    private const val BASE_URL = "https://routes.googleapis.com/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: RoutesApi = retrofit.create(RoutesApi::class.java)
}