package com.example.safetymap.ui

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.safetymap.ui.theme.SafetyMapTheme
import com.google.android.libraries.places.api.Places


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the Places API
        if (!Places.isInitialized()) {
            val applicationInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            val apiKey = applicationInfo.metaData.getString("com.google.android.geo.API_KEY")
            if (apiKey != null) {
                Places.initialize(applicationContext, apiKey)
            }
        }

        setContent {
            SafetyMapTheme {
                // opening screen
                MapScreen()
            }
        }
    }
}
