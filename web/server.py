"""
VibeStream Real YouTube Music Server
Streams available YouTube audio through a local proxy; upstream failures remain possible.
Provides search, live stream proxying with HTTP Range support, and format-aware download.
"""

import http.server
import socketserver
import urllib.request
import urllib.parse
import json
import os
import sys
import re
import threading
import tempfile
import yt_dlp

# Force UTF-8 on Windows
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
    except Exception:
        pass

PORT = 5173

# Memory cache for resolved direct stream URLs (video_id -> {url, title, mime})
STREAM_CACHE = {}

# Concurrency limiter to protect host from DoS / thread exhaustion (max 6 simultaneous yt-dlp extractions)
EXTRACTION_SEMAPHORE = threading.BoundedSemaphore(value=6)

def get_mime_type(ext: str) -> str:
    ext = (ext or '').lower()
    if ext in ['m4a', 'mp4']:
        return 'audio/mp4'
    elif ext in ['webm', 'opus']:
        return 'audio/webm'
    elif ext == 'ogg':
        return 'audio/ogg'
    elif ext == 'mp3':
        return 'audio/mpeg'
    elif ext in ['flac', 'wav']:
        return f'audio/{ext}'
    return 'application/octet-stream'

def get_real_stream_url(video_id_or_query: str, force_refresh: bool = False):
    """Uses yt-dlp to extract the real direct audio stream URL and metadata with strict input validation."""
    if not force_refresh and video_id_or_query in STREAM_CACHE:
        return STREAM_CACHE[video_id_or_query]

    # Validate target: only allow clean YouTube IDs or ytsearch queries (prevent SSRF)
    if video_id_or_query.startswith("ytsearch"):
        # Strip scheme injection from search query
        query_part = video_id_or_query[len("ytsearch1:"):].strip()
        if re.search(r'^[a-zA-Z0-9+.-]+://', query_part):
            print(f"Rejected SSRF scheme in query: {query_part}")
            return None
        safe_query = re.sub(r'[\r\n\t]', ' ', query_part)[:100]
        url_to_extract = f"ytsearch1:{safe_query}"
    elif re.match(r'^[a-zA-Z0-9_-]{8,16}$', video_id_or_query):
        url_to_extract = f"https://www.youtube.com/watch?v={video_id_or_query}"
    else:
        print(f"Rejected invalid extraction target: {video_id_or_query}")
        return None

    acquired = EXTRACTION_SEMAPHORE.acquire(timeout=25)
    if not acquired:
        print(f"Extraction semaphore busy for target: {video_id_or_query}")
        return None

    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'format': 'bestaudio[ext=m4a]/bestaudio/best',
        'noplaylist': True,
        'extract_flat': False,
    }

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url_to_extract, download=False)
            if 'entries' in info:
                info = info['entries'][0]

            stream_url = info.get('url')
            title = info.get('title', 'Unknown Track')
            duration = info.get('duration', 0)
            thumbnail = info.get('thumbnail', '')
            ext = info.get('ext') or 'bin'
            mime = get_mime_type(ext)

            data = {
                'id': info.get('id', video_id_or_query),
                'stream_url': stream_url,
                'title': title,
                'duration': duration,
                'thumbnail': thumbnail,
                'ext': ext,
                'mime': mime
            }
            if stream_url:
                STREAM_CACHE[video_id_or_query] = data
                STREAM_CACHE[data['id']] = data
            return data
    except Exception as e:
        print(f"Error extracting stream for {video_id_or_query}: {e}")
        return None
    finally:
        EXTRACTION_SEMAPHORE.release()

def search_youtube(query: str, limit: int = 10):
    """Searches YouTube for tracks using yt-dlp flat playlist extraction."""
    # Disallow URL scheme injection and limit length
    safe_query = re.sub(r'^[a-zA-Z0-9+.-]+://', '', query.strip())
    safe_query = re.sub(r'[\r\n\t]', ' ', safe_query)[:100]
    if not safe_query:
        return []

    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'extract_flat': True,
        'noplaylist': True,
    }
    search_target = f"ytsearch{limit}:{safe_query}"
    results = []

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            res = ydl.extract_info(search_target, download=False)
            entries = res.get('entries', []) if res else []
            for item in entries:
                vid = item.get('id')
                title = item.get('title')
                if not vid or not title:
                    continue

                duration_sec = item.get('duration') or 0
                mins = int(duration_sec // 60)
                secs = int(duration_sec % 60)
                dur_str = f"{mins}:{secs:02d}" if duration_sec else "Unknown"

                uploader = item.get('uploader') or item.get('channel') or "YouTube Music"

                # Pick thumbnail
                thumb = ""
                thumbnails = item.get('thumbnails')
                if thumbnails and len(thumbnails) > 0:
                    thumb = thumbnails[-1].get('url', '')
                if not thumb:
                    thumb = f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"

                results.append({
                    'id': vid,
                    'title': title,
                    'artist': uploader,
                    'duration': dur_str,
                    'durationSec': duration_sec,
                    'thumbnail': thumb,
                    'streamUrl': f"/api/stream?id={vid}"
                })
    except Exception as e:
        print(f"Search error for '{query}': {e}")

    return results

def get_search_suggestions(query: str):
    """Fetches real-time search recommendations / autocomplete from YouTube."""
    if not query:
        return []
    url = f"https://suggestqueries-clients6.youtube.com/complete/search?client=firefox&ds=yt&q={urllib.parse.quote(query)}"
    headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=5) as res:
            data = json.loads(res.read().decode('utf-8'))
            if len(data) > 1 and isinstance(data[1], list):
                return data[1][:8]
    except Exception as e:
        pass
    return []

class RealMusicHandler(http.server.SimpleHTTPRequestHandler):

    def end_headers(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Headers', 'Range, Content-Type, Accept')
        self.send_header('Access-Control-Expose-Headers', 'Content-Range, Content-Length, Accept-Ranges')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('X-Frame-Options', 'SAMEORIGIN')
        super().end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        params = urllib.parse.parse_qs(parsed.query)

        # 0. API: Real-time Search Suggestions / Autocomplete
        if path == '/api/suggestions':
            query = params.get('q', [''])[0].strip()
            # Sanitize search query input
            safe_q = re.sub(r'^[a-zA-Z0-9+.-]+://', '', query)[:80]
            suggestions = get_search_suggestions(safe_q)
            self.send_json(suggestions)
            return

        # 1. API: Search YouTube
        if path == '/api/search':
            query = params.get('q', [''])[0].strip()
            if not query:
                self.send_json({'error': 'Missing query param q'}, 400)
                return

            results = search_youtube(query, limit=12)
            self.send_json(results)
            return

        # 2. API: Live Stream Audio Proxy (with HTTP 206 Partial Content Range support)
        if path == '/api/stream':
            video_id = params.get('id', [''])[0].strip()
            query = params.get('q', [''])[0].strip()

            if video_id:
                if not re.match(r'^[a-zA-Z0-9_-]{8,16}$', video_id):
                    self.send_error(400, "Invalid YouTube video ID format")
                    return
                target = video_id
            elif query:
                if re.search(r'^[a-zA-Z0-9+.-]+://', query):
                    self.send_error(400, "URL schemes not permitted in search query")
                    return
                safe_q = re.sub(r'[\r\n\t]', ' ', query)[:100]
                target = f"ytsearch1:{safe_q} audio"
            else:
                self.send_error(400, "Missing id or q parameter")
                return

            info = get_real_stream_url(target)
            if not info or not info.get('stream_url'):
                self.send_error(404, "Stream not found")
                return

            self.proxy_audio_stream(
                info['stream_url'],
                info.get('mime', 'application/octet-stream'),
                video_id=info.get('id', video_id)
            )
            return

        # 3. API: Direct Download Track (Attachment)
        if path == '/api/download':
            video_id = params.get('id', [''])[0].strip()
            if not video_id or not re.match(r'^[a-zA-Z0-9_-]{8,16}$', video_id):
                self.send_error(400, "Missing or invalid YouTube video ID")
                return

            info = get_real_stream_url(video_id)
            if not info or not info.get('stream_url'):
                self.send_error(404, "Audio not found")
                return

            safe_filename = re.sub(r'[^a-zA-Z0-9_\-.]', '_', info.get('title', 'song'))[:50]
            ext = info.get('ext') or 'bin'

            self.proxy_audio_stream(
                info['stream_url'],
                info.get('mime', 'application/octet-stream'),
                download_filename=f"{safe_filename}.{ext}",
                video_id=info.get('id', video_id)
            )
            return

        # 4. App Preview & Android APK Download
        if path in ('/download', '/preview', '/apk'):
            preview_html = os.path.join(os.path.dirname(__file__), 'download.html')
            if os.path.exists(preview_html):
                with open(preview_html, 'rb') as f:
                    content = f.read()
                self.send_response(200)
                self.send_header('Content-Type', 'text/html; charset=utf-8')
                self.send_header('Content-Length', str(len(content)))
                self.end_headers()
                self.wfile.write(content)
                return

        if path in ('/download/apk', '/app.apk', '/vibestream.apk'):
            accept = self.headers.get('Accept', '')
            is_browser_view = 'text/html' in accept and params.get('file') != ['1'] and params.get('direct') != ['1']
            if is_browser_view:
                preview_html = os.path.join(os.path.dirname(__file__), 'download.html')
                if os.path.exists(preview_html):
                    with open(preview_html, 'rb') as f:
                        content = f.read()
                    self.send_response(200)
                    self.send_header('Content-Type', 'text/html; charset=utf-8')
                    self.send_header('Content-Length', str(len(content)))
                    self.end_headers()
                    self.wfile.write(content)
                    return

            candidate_apk_paths = [
                os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')),
                os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'android_music_app', 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')),
                os.path.abspath(os.path.join(os.path.dirname(__file__), 'app-debug.apk')),
            ]
            apk_path = next((p for p in candidate_apk_paths if os.path.exists(p)), None)
            if apk_path:
                self.send_response(200)
                self.send_header('Content-Type', 'application/vnd.android.package-archive')
                self.send_header('Content-Disposition', 'attachment; filename="VibeStream.apk"')
                self.send_header('Content-Length', str(os.path.getsize(apk_path)))
                self.end_headers()
                with open(apk_path, 'rb') as f:
                    while True:
                        chunk = f.read(64 * 1024)
                        if not chunk:
                            break
                        self.wfile.write(chunk)
                return
            else:
                self.send_error(404, "VibeStream APK not found on server")
                return

        # Default: Serve static files
        super().do_GET()

    def proxy_audio_stream(self, cdn_url: str, mime_type: str, download_filename: str = None, video_id: str = None):
        """Streams audio bytes directly from YouTube CDN with HTTP Range headers."""
        headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        }
        client_range = self.headers.get('Range')
        if client_range and not download_filename:
            headers['Range'] = client_range

        req = urllib.request.Request(cdn_url, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=15) as res:
                if download_filename:
                    # Stage the complete response before claiming it is a downloadable file.
                    # A truncated upstream response must not become a saved offline track.
                    if res.status != 200 or mime_type not in ('audio/mp4', 'audio/webm', 'audio/ogg', 'audio/mpeg'):
                        self.send_error(502, "Unsupported audio response")
                        return
                    declared_length = res.headers.get('Content-Length')
                    expected = int(declared_length) if declared_length is not None else None
                    with tempfile.SpooledTemporaryFile(max_size=16 * 1024 * 1024) as staged:
                        actual = 0
                        while True:
                            chunk = res.read(64 * 1024)
                            if not chunk:
                                break
                            staged.write(chunk)
                            actual += len(chunk)
                        if not actual or (expected is not None and actual != expected):
                            self.send_error(502, "Incomplete upstream audio")
                            return
                        staged.seek(0)
                        self.send_response(200)
                        self.send_header('Content-Type', mime_type)
                        self.send_header('Content-Length', str(actual))
                        self.send_header('Content-Disposition', f'attachment; filename="{download_filename}"')
                        self.end_headers()
                        while True:
                            chunk = staged.read(64 * 1024)
                            if not chunk:
                                break
                            self.wfile.write(chunk)
                    return
                status_code = res.status
                self.send_response(status_code)
                self.send_header('Content-Type', mime_type)
                self.send_header('Accept-Ranges', 'bytes')

                content_range = res.headers.get('Content-Range')
                if content_range:
                    self.send_header('Content-Range', content_range)

                content_length = res.headers.get('Content-Length')
                if content_length:
                    self.send_header('Content-Length', content_length)

                if download_filename:
                    self.send_header('Content-Disposition', f'attachment; filename="{download_filename}"')

                self.end_headers()

                # Stream bytes to client
                while True:
                    chunk = res.read(64 * 1024)
                    if not chunk:
                        break
                    try:
                        self.wfile.write(chunk)
                    except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
                        break
        except urllib.error.HTTPError as he:
            if video_id and video_id in STREAM_CACHE and he.code in (403, 410):
                STREAM_CACHE.pop(video_id, None)
                fresh_info = get_real_stream_url(video_id, force_refresh=True)
                if fresh_info and fresh_info.get('stream_url'):
                    new_name = download_filename
                    if download_filename:
                        safe_title = re.sub(r'[^a-zA-Z0-9_\-.]', '_', fresh_info.get('title', 'song'))[:50]
                        new_name = f"{safe_title}.{fresh_info.get('ext') or 'bin'}"
                    return self.proxy_audio_stream(
                        fresh_info['stream_url'],
                        fresh_info.get('mime', 'application/octet-stream'),
                        download_filename=new_name
                    )
            try:
                self.send_error(he.code, f"Stream error: {he.reason}")
            except Exception:
                pass
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            pass
        except Exception as e:
            print(f"Audio proxy failed: {e}")

    def send_json(self, data, status: int = 200):
        body = json.dumps(data).encode('utf-8')
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

def run():
    web_dir = os.path.dirname(os.path.abspath(__file__))
    os.chdir(web_dir)

    socketserver.TCPServer.allow_reuse_address = True
    class ThreadedHTTPServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
        daemon_threads = True

    try:
        server = ThreadedHTTPServer(("127.0.0.1", PORT), RealMusicHandler)
        print("=" * 65)
        print(">> VIBESTREAM MUSIC PIPELINE LIVE")
        print("=" * 65)
        print(f"-> Web Player: http://127.0.0.1:{PORT}")
        print("-> Local-only server; it is not reachable from another device.")
        print("=" * 65)
        print("Ready for continuous play & downloads. Press Ctrl+C to stop.\n")
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping server...")
        sys.exit(0)

if __name__ == "__main__":
    run()
