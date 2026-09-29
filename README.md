# 🎵 VibeStream — Standalone Android Music App & Web Player

[![Android Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--34)-3DDC84.svg?logo=android&logoColor=white)](android/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF.svg?logo=kotlin&logoColor=white)](android/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-4285F4.svg?logo=jetpackcompose&logoColor=white)](android/)
[![Media3 ExoPlayer](https://img.shields.io/badge/Audio-AndroidX%20Media3%20ExoPlayer-FF6F00.svg)](android/)
[![Python](https://img.shields.io/badge/Web%20Companion-Python%203.10%2B-3776AB.svg?logo=python&logoColor=white)](web/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

An autonomous, Spotify-inspired music ecosystem featuring a **100% native serverless Android app** and a companion **responsive Web player & streaming server**. Browse, stream, search with live autocomplete recommendations, and download tracks offline directly to your device storage with zero-latency playback.

---

## 🌟 Key Highlights

* **📱 100% Serverless Android Mode**: Streams YouTube audio directly on-device using decentralized endpoints with automated multi-mirror failover. No personal server or PC required to run the app on your phone.
* **⚡ Smart Playback Routing**: When picking a track, the app checks if it is saved in local device storage (`file://`). If present, it starts in **0ms without consuming any mobile data**; otherwise, it streams directly from the CDN.
* **🔍 Search Engine Recommendations**: Instant autocomplete suggestions dynamically populate as you type in the search bar (debounced at 220ms), just like real search engines.
* **🔒 Lock Screen & Media Controls**: Built with AndroidX Media3 `PlaybackService` and `MediaSessionService`. Keeps audio playing smoothly in the background when the phone is locked or while using other apps.
* **💾 One-Tap Offline Download Pipeline**: Direct M4A/Opus audio caching to device storage with real-time progress indicators.
* **🛡️ Hardened Security & Privacy**: Zero invasive permissions (no contacts, microphone, camera, or location). Uses Android Scoped Storage and sanitizes stream proxy inputs against SSRF.
* **🌐 Web Companion & Wi-Fi Portal**: Includes a browser player with HTTP Range-streaming proxy and mobile device download landing page.

---

## 🏗 System Architecture

```
                                 VIBESTREAM ECOSYSTEM
                                          │
            ┌─────────────────────────────┴─────────────────────────────┐
            ▼                                                           ▼
   [ Native Android App ]                                     [ Web Companion Server ]
   (Jetpack Compose UI)                                       (Pure Python + yt-dlp)
            │                                                           │
   ┌────────┴────────┐                                         ┌────────┴────────┐
   ▼                 ▼                                         ▼                 ▼
[Online Streaming] [Offline Downloads]                   [Browser Web Player] [APK Portal]
(Media3 ExoPlayer) (Scoped Storage)                      (HTTP Range Proxy)   (Wi-Fi Install)
   │                 │                                         │
   ▼                 ▼                                         ▼
YouTube CDN      Local Device FS                          YouTube CDN
```

### Component Structure

```
Local_Music_app/
├── android/                         # Complete Native Android Project
│   ├── app/
│   │   ├── src/main/java/com/contentagent/vibestream/
│   │   │   ├── data/
│   │   │   │   ├── extractor/       # YouTube decentralized audio stream & search resolver
│   │   │   │   └── local/           # Scoped storage & download manager
│   │   │   ├── player/              # AndroidX Media3 ExoPlayer & background PlaybackService
│   │   │   └── ui/
│   │   │       ├── components/      # MiniPlayer, FullPlayerSheet, SongCards
│   │   │       └── screens/         # HomeScreen, SearchScreen, DownloadsScreen
│   │   ├── build.gradle.kts         # App dependencies & SDK versions
│   │   └── proguard-rules.pro       # R8 / ProGuard rules
│   ├── gradle/wrapper/              # Gradle Wrapper binaries
│   ├── build.gradle.kts             # Root Gradle build script
│   ├── gradlew / gradlew.bat        # Gradle build scripts
│   └── settings.gradle.kts
│
├── web/                             # Web Player & Streaming Server
│   ├── index.html                   # Spotify Dark theme responsive web player
│   ├── download.html                # Mobile phone preview & APK download landing page
│   ├── server.py                    # Multi-threaded audio proxy with HTTP Range support
│   └── requirements.txt             # Python dependencies (yt-dlp)
│
├── .gitignore                       # Multi-platform exclusions (Gradle, Python, OS)
└── README.md                        # Documentation & setup guide
```

---

## 🚀 Getting Started

### 1. Native Android App (`android/`)

#### Requirements:
* Android Studio (Hedgehog, Iguana, Jellyfish, or newer)
* Android SDK 34 (Android 14)
* JDK 17
* Android Phone or Emulator (Android 8.0+ / API 26+)

#### Build with Gradle CLI:
```bash
cd android
./gradlew assembleDebug
```
The output APK is generated at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

#### Run in Android Studio:
1. Open Android Studio and select **Open** -> choose the `android` folder.
2. Let Gradle sync dependencies.
3. Connect your Android phone with **USB Debugging** enabled.
4. Click **Run 'app'** (`Shift + F10`).

---

### 2. Web Companion & Wi-Fi Server (`web/`)

The web companion allows playing music directly in any browser and hosts the Wi-Fi APK download page for your home network.

#### Installation:
```bash
cd web
pip install -r requirements.txt
```

#### Run Server:
```bash
python server.py
```

* **Web Music Player**: `http://localhost:5173`
* **Mobile APK Download & Inspection Page**: `http://localhost:5173/download`

---

## 🛡️ Security Audit & Privacy Design

1. **Permissions**: Requires only standard `INTERNET` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. No access to user contacts, location, photos, camera, or identifiers.
2. **Offline Isolation**: Audio tracks are saved into app-specific scoped storage (`context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)`), preventing file collisions with other apps.
3. **SSRF Mitigation**: The companion streaming proxy strictly restricts extraction queries to clean YouTube video IDs or sanitized search queries, rejecting unauthorized URLs or protocols.
4. **DoS Protection**: Concurrency limiter (`threading.BoundedSemaphore`) caps simultaneous extraction threads to prevent host resource exhaustion.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
Audio and video metadata are sourced for personal educational and demonstration purposes.
