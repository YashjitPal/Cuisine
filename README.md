# Cuisine 🍽️✨

> **Your gallery, served fresh.**  
> A private-by-design Android gallery and social feed that transforms your camera roll into an immersive feed, stories, reels, and pinboard — entirely on-device with zero cloud uploads.

[![Android](https://img.shields.io/badge/Platform-Android%2010%2B%20(API%2029%E2%80%9337)-3DDC84?style=flat&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Expressive-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![ML](https://img.shields.io/badge/On--Device%20ML-ML%20Kit%20%2B%20LiteRT-FF6F00?style=flat&logo=google&logoColor=white)](https://developers.google.com/ml-kit)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## ✨ Features

- **📱 Local Social Feed**: Reimagines your camera roll as a rich, chronological social feed with likes, bookmarks, comments, and date-based stories.
- **🎬 Reels**: Smooth vertical short-form video player powered by AndroidX Media3 ExoPlayer with gesture scrub, tap-to-pause, and volume toggle.
- **👤 On-Device Face Recognition**: Automatically tags and clusters people across your photos using Google ML Kit Face Detection and a lightweight LiteRT MobileFaceNet embedding model. All inference runs locally on your hardware.
- **🎨 Material You & M3 Expressive**:
  - Full support for Android 12+ dynamic color extraction from your wallpaper.
  - 5 custom handcrafted palette flavors: **Paprika**, **Saffron**, **Basil**, **Blueberry**, and **Plum**.
  - Animated color cross-fading and Material 3 Expressive motion schemes.
  - Native adaptive launcher icon and Android 13+ themed icon support.
- **📌 Spatial Pinboard & Library**: Organize your favorite photos and videos onto an interactive pinboard canvas.
- **🔒 Zero Cloud Uploads**: Complete privacy by design. There is no backend server, no tracking, and no internet network traffic.
- **💾 Portable JSON Backups**: Easily export and import your tags, likes, people mappings, and profile metadata.

---

## 📥 Download APK (v1.0)

You can grab the latest compiled release APK directly from the [Releases](https://github.com/) section:

- **[Download Cuisine v1.0 APK](../../releases/latest)**

*Compatibility: Android 10 (API 29) through Android 16 (API 37+). Supported architectures: `arm64-v8a`, `x86_64`.*

---

## 🛠️ Architecture & Tech Stack

Cuisine is built using modern Android engineering practices:

| Component | Technology |
|---|---|
| **UI Framework** | [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 Expressive |
| **Navigation** | [Navigation 3](https://developer.android.com/guide/navigation) (`androidx.navigation3`) |
| **Video Playback** | [AndroidX Media3 ExoPlayer](https://developer.android.com/media/media3) |
| **Image & Video Loading** | [Coil 3](https://coil-kt.github.io/coil/) |
| **Machine Learning** | [Google ML Kit Face Detection](https://developers.google.com/ml-kit) + [LiteRT](https://ai.google.dev/edge/litert) (MobileFaceNet) |
| **Vector Geometry** | [AndroidX Graphics Shapes](https://developer.android.com/reference/androidx/graphics/shapes/package-summary) |
| **System Integration** | Splash Screen API, Edge-to-Edge window insets, Predictive Back navigation |

---

## 🔨 Building from Source

### Prerequisites
1. **JDK 17 or 21**
2. **Android SDK Platform 37** (installed via Android Studio or command-line tools)
3. **Gradle 9+** (managed via `./gradlew`)

### Build Steps

Clone the repository and compile the release APK:

```bash
git clone https://github.com/<your-username>/Cuisine.git
cd Cuisine

# Build the release APK
./gradlew assembleRelease
```

The compiled release APK will be located at:
```
app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License

```
Copyright 2026 Cuisine Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
