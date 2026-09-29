# VibeStream Web Preview

A responsive, web version of the VibeStream music pipeline to test the UI/UX, real-time search, streaming, and offline download capabilities directly in your browser.

## 🚀 Quick Start

### Option 1: Python Local Server (Recommended)
From PowerShell / Terminal:
```bash
cd web
python server.py
```
Open `http://127.0.0.1:5173` in your browser. The server is local-only.

### Option 2: Direct File Open (UI only)
Opening the HTML directly cannot use the search/stream/download APIs. Run server.py for those features.

---

## 🎨 UI/UX Highlights

* **📱 Phone View vs. 🖥️ Full View:** Switch seamlessly between a mobile phone mockup frame (to see exactly how it feels on an Android device) and an expansive full-screen mode.
* **Playback & Scrubber:** Click any song to start playback. Includes a docked mini-player at the bottom and an expandable full-screen player sheet with scrubber, ±10s skips, and canvas visualizer.
* **Live Search & Fallback:** Search YouTube for any song or artist, or click the quick genre tags (Lo-Fi, Synthwave, Acoustic, High Energy).
* **💾 Offline Download Pipeline:** Click the download icon on any song to save a verified audio blob in this browser's IndexedDB. View all saved songs in the **Offline Downloads** tab.
