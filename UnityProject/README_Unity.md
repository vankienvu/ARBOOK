# Unity ARTextbookDemo

This folder contains the Unity-side source scripts for the local Vuforia demo.

## Required Editor Setup

1. Open `UnityProject` in Unity 2022.3 LTS.
2. Open `Assets/Scenes/ARTextbookDemo.unity`.
3. Add your personal Vuforia license key in Vuforia Configuration.
4. Press Play and show one of the PNG markers to the webcam.

Vuforia Engine 11.4.4, the AR Camera, UI, API client, placeholder models, and runtime image-target creation are already configured. The three targets are created directly from the included PNG files, so a Target Manager database is not required for this local demo.

## Backend API

Default backend URL: `http://localhost:8080`

Unity calls:

```http
GET http://localhost:8080/api/ar-markers/resolve?code=BIO_CELL_001
```

If the backend is unavailable, `DemoFallbackData` keeps the demo running.
