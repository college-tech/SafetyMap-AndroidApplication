# Safety Map 🗺️🛡️ Last Mile Route Navigation System

This is the native Android client for the Pedestrian Safety Navigation project. Built entirely with modern Android development tools (Kotlin, Jetpack Compose, and MVVM), this app provides users with interactive maps, alternative route generation, and real-time AI-powered safety analysis for nighttime or high-risk walking.

## Overview

SafetyMap is an AI-powered pedestrian navigation system that recommends the **safest walking route** instead of only the shortest or fastest path. The system combines Computer Vision, contextual geospatial information, and Explainable AI (XAI) to assess route safety and provide users with transparent safety recommendations.

The application analyzes Google Street View images using a deep learning model to estimate environmental safety factors and combines them with nearby emergency facilities and commercial activity to compute an overall route safety score.

## Problem Statement

Traditional navigation applications primarily optimize routes based on distance or travel time while ignoring pedestrian safety. However, factors such as poor lighting, limited visibility, inadequate pedestrian infrastructure, and the absence of nearby emergency facilities can significantly affect the safety of a walking route.

SafetyMap addresses this limitation by incorporating AI-driven environmental analysis and contextual location information into the navigation process, enabling users to make safer route choices.


## Proposed Solution

The proposed system evaluates each candidate route using both visual and contextual information.

### Visual Analysis
Google Street View images are analyzed using an EfficientNet-based deep learning model to estimate:

- Openness
- Lighting
- Visibility
- Walkability

### Contextual Analysis

Additional safety information is collected using Google APIs, including:

- Distance to nearest police station
- Distance to nearest hospital
- Number of nearby open shops

These parameters are combined through a weighted safety scoring algorithm to rank available routes.

To improve transparency, an Explainable AI (XAI) module generates a concise natural-language explanation describing why a route is considered safe or unsafe.

## 📷 Output

The Android application provides:

- Interactive Google Maps interface
- Multiple route visualization
- Route safety scores
- AI-generated route explanations
- Safest route recommendation
- Real-time navigation support

The backend server performs:

- Multiple route generation using Google Routes API
- Street View image acquisition
- EfficientNet-based prediction of visual safety parameters
- Contextual safety analysis using nearby police stations, hospitals, and open shops
- Weighted route safety score calculation
- LLM-based explanation generation
- JSON response containing ranked routes and safety information

## 🛠️ How It Works

1.  **Route Request:** The user selects a destination on the Android app.
2.  **Pathfinding:** The app requests multiple walking paths from the Google Routes API.
3.  **Safety Analysis:** The route coordinates are sent to the Python backend.
4.  **Data Acquisition:** The backend fetches Street View imagery and nearby POI (Points of Interest) data.
5.  **AI Processing:** EfficientNet analyzes visual safety, while a weighted algorithm calculates the safety score.
6.  **Explanation:** Gemini LLM generates a natural language justification for the score.
7.  **Visualization:** The app renders the routes with color-coded safety levels and displays the XAI explanation.

## 🚀 Features

*   **Interactive Mapping:** Utilizes the Google Maps SDK to display user locations, destinations, and custom-styled route polylines.
*   **Alternative Routing:** Fetches multiple walking paths using the Google Routes API.
*   **AI Safety Integration:** Communicates with our custom Python backend via Retrofit to analyze routes for lighting, crowds, and emergency amenities.
*   **Explainable AI (XAI) UI:** Displays human-readable, Gemini-generated explanations of *why* a route received its specific safety score.
*   **Reactive UI:** Built entirely with Jetpack Compose and Kotlin Coroutines for a buttery-smooth, non-blocking user experience.

## 🛠️ Tech Stack

*   **Language:** Kotlin
*   **UI Toolkit:** Jetpack Compose
*   **Architecture:** MVVM (Model-View-ViewModel)
*   **Networking:** Retrofit2, OkHttp3, Kotlin Coroutines
*   **Mapping:** Google Maps SDK for Android, Google Places/Directions API

## 📁 Project Structure

```text
app/src/main/
├── java/com/example/safetymap/
│   ├── data/
│   │   ├── api/
│   │   │   ├── AiBackendApi.kt      # Interface for backend /analyze-route
│   │   │   ├── RetrofitClient.kt    # OkHttp setup with 60s timeouts
│   │   │   └── RouteApi.kt          # Interface for Google Directions API
│   │   ├── model/
│   │   │   └── RouteModels.kt       # Kotlin Data Classes representing JSON schemas
│   │   └── repository/
│   │       └── RouteRepository.kt   # Single source of truth for network calls
│   └── ui/
│       ├── theme/                   # Compose color/typography definitions
│       ├── MainActivity.kt          # Compose entry point
│       ├── MapScreen.kt             # Main UI containing the Google Map and overlays
│       └── MapViewModel.kt          # State management and background processing
└── res/
    └── AndroidManifest.xml          # App permissions and metadata 
```

## ⚙️ Setup & Installation

### 1. Prerequisites
Before you begin, ensure you have the following ready:
* **Android Studio** (Latest stable version recommended, e.g., Koala or Ladybug).
* **Android Device or Emulator** running Android 8.0 (API 26) or higher.
* **Google Maps API Key** (Generated via Google Cloud Console with the *Maps SDK for Android* enabled).

### 2. Clone the Repository
Open your terminal or command prompt and run:
```bash
git clone https://github.com/college-tech/SafetyMap-AndroidApplication.git
```

### 3. Open the Project in Android Studio
1. Launch **Android Studio**.
2. Click **File > Open** and select the folder you just cloned.
3. Wait a few moments for the Gradle sync to complete automatically.

### 4. Configure Your Google Maps API Key
This project uses the [Secrets Gradle Plugin](https://github.com/google/secrets-gradle-plugin) to keep API keys secure.
1. Create a `local.properties` file in your project root.
2. Add your API key:
   ```properties
   MAPS_API_KEY=YOUR_ACTUAL_API_KEY_HERE
   ```

### 5. Connect to Your Python Backend
Your app needs to know where the AI Python server is running.
1. Navigate to `app/src/main/java/com/example/safetymap/data/api/RetrofitClient.kt`.
2. Update the `BASE_URL` depending on how you are testing:
   * **If using the Android Emulator:** Update the `BASE_URL` with your server's IP:
   ```kotlin
   private const val BASE_URL = "http://10.0.2.2:8000/
   ```
   * **If using a Physical Android Phone:** Ensure both your phone and laptop are on the same Wi-Fi network. Find your laptop's IPv4 address and use that.
   ```kotlin
   BASE_URL = "http://YOUR_LOCAL_IP:8000/"
   ```
### 6. Build and Run
1. Select your target device (Emulator or Physical phone) from the dropdown menu at the top of Android Studio.
2. Click the green **▶ Run App** button.
3. The app will compile and launch on your device!

### 7. Clone and Run the Backend Server
The AI analysis features require the Python backend. You'll get this from here:
```bash
https://github.com/college-tech/SafetyMap-backendServer
```
Follow the setup instructions in that repository to start the FastAPI server.

## 👤 Author

* **Subhadip Mondal**
* **Akanksha Singh**


