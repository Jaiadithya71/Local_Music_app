# VibeStream: Autonomous On-Device Android Music Pipeline

A standalone, modern Android music application that enables users to browse, search, stream, and save songs from YouTube directly on their phone—**100% serverless with zero backend dependency**.

---

## 🌟 Key Features

* **100% On-Device (No Server Needed):** All search queries, audio stream extraction, and downloads happen directly on the Android phone.
* **Browse & Instant Search:** Type any track, artist, album, or genre chip (Lo-Fi, Synthwave, Acoustic, EDM) to find songs with thumbnails, duration, and channel metadata.
* **High-Fidelity Audio Streaming:** Direct M4A/Opus audio streaming powered by **AndroidX Media3 (ExoPlayer)** with low buffering latency.
* **Background Playback & Lock-Screen Controls:** Integrated with `PlaybackService` (`MediaSessionService`) so music keeps playing when screen is locked or while multitasking.
* **One-Tap Offline Download Pipeline:** Saves full audio files directly into local device storage (`Environment.DIRECTORY_MUSIC`) with live progress indication.
* **Offline Downloads Library:** Dedicated offline tab displaying all saved tracks. Offline playback requires **zero internet or cellular data**.
* **Modern Material 3 UI:** Fluid Jetpack Compose interface with dark theme, dynamic mini-player, and expandable full-screen player sheet with scrubber and ±10s controls.

---

## 🏗 Architecture & Data Flow

```
                      +---------------------------------------+
                      |           Android Device              |
                      |                                       |
                      |   +-------------------------------+   |
                      |   |     Jetpack Compose UI        |   |
                      |   |  - Discover & Search Tab      |   |
                      |   |  - Offline Downloads Tab      |   |
                      |   |  - Mini & Full Player Sheet   |   |
                      |   +---------------+---------------+   |
                      |                   |                   |
                      |         +---------+---------+         |
                      |         |                   |         |
                      |         v                   v         |
                      |  [YouTubeExtractor]  [DownloadManager]|
                      |         |                   |         |
                      |         v                   v         |
                      |  [Media3 Player]    [OfflineStorage]  |
                      |    (ExoPlayer)      (Local Device FS) |
                      +---------+-------------------+---------+
                                |                   |
                      (Online Stream)        (Offline File)
                                |                   |
                                v                   v
                          YouTube CDN          Local Flash
```

### Component Breakdown

| Component | File | Responsibility |
| :--- | :--- | :--- |
| **Extractor** | [`YouTubeExtractor.kt`](app/src/main/java/com/contentagent/vibestream/data/extractor/YouTubeExtractor.kt) | Queries decentralized public endpoints to search songs and resolve raw audio CDN streams with automatic mirror failover. |
| **Download Pipeline** | [`DownloadManager.kt`](app/src/main/java/com/contentagent/vibestream/data/local/DownloadManager.kt) | Streams raw audio chunks directly to device storage with real-time byte tracking and progress notification. |
| **Offline Storage** | [`OfflineStorage.kt`](app/src/main/java/com/contentagent/vibestream/data/local/OfflineStorage.kt) | Persists metadata for saved tracks on disk for instant offline access. |
| **Player Controller** | [`MusicPlayerController.kt`](app/src/main/java/com/contentagent/vibestream/player/MusicPlayerController.kt) | Manages ExoPlayer lifecycle, seamlessly switching between remote CDN streams and `file://` local URIs. |
| **Background Service** | [`PlaybackService.kt`](app/src/main/java/com/contentagent/vibestream/player/PlaybackService.kt) | Android `MediaSessionService` ensuring audio continuity when the app is minimized or phone is locked. |

---

## 📱 How to Open and Run

### Requirements
* Android Studio (Hedgehog / Iguana / Jellyfish or newer)
* Android SDK 34 (Android 14) or compatible
* JDK 17 (pre-installed in your environment)
* Android Device or Emulator running Android 8.0 (API 26) or higher

### Steps

1. Launch **Android Studio**.
2. Select **Open** and choose this directory:
   ```
   Personal Projects/android_music_app
   ```
3. Allow Android Studio to automatically sync the Gradle configuration.
4. Connect an Android phone via USB (with USB Debugging enabled) or start an Android Emulator.
5. Click **Run 'app'** (or press `Shift + F10`).
