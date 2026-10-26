# Recognition provider prototype checkpoint, 2026-10-26

No release or universal accuracy claim. All requests used fingerprints only,
no microphone, account, geographic coordinates, user history or source URL.

## Independent provider probes
- Self-generated8s mono16k chirp/tones: provider returned multiple unrelated
  catalog matches and a named track. Rejected as a false positive, not a success.
  Strict parser rejects ambiguity and time/frequency skew above0.005.
- Public-domain Paul Pitman Beethoven Moonlight III excerpts at40s and70s,
  each12s: same recording key557192260 and correct movement title returned.
  First offsets43.7006s/73.6985s, low skew. The consistent3.7s shift means
  offsets cannot be used blindly to claim precise song starts or exact master.
- Source performance and composition declared public-domain on:
  https://archive.org/details/MoonlightSonata_845
  Downloaded audio used only for private bounded probes, not committed here.

- Independent Kotlin fingerprint of the same public-domain 40s window: correct
  recording key and movement title, offset42.7062s. This independently exercises
  the app fingerprint implementation, but is not another song/catalog sample.
- Generated12s silence with Kotlin fingerprint: no matches and no named track.
  Total bounded requests: five. No user audio used.

These are positive windows in one recording, synthetic rejection and silence,
not a genre/crossfade/catalog benchmark. Tamil catalog coverage unmeasured.
Boundary refinement remains approximate; Unknown segments remain visible.

## Local/Android gates
At d715725,45 JVM tests and4 Android emulator tests passed with zero failures,
errors or skips. Includes independent direct-PCM NumPy FFT signature comparison
(98% minimum peak agreement within2 quantization units, count difference<=2),
AAC/Opus/PCM16k window decoding, unequal/repeated song identity fixtures,
Unknown misses, app/session/media-key chapter Next and queue fallback.

The processor is wired behind a default-off in-app opt-in disclosure. Enabling
covers future eligible recordings; disabling stops further planned requests.
A request already in transit cannot be recalled. Cues are cached locally by
song ID only, not source content hash: replaced local content with reused IDs
can yield stale cues. Duration scope10min-3h,60s discovery grid,240-call ceiling.
Very short songs and crossfades can be missed. No physical headphones tested.
No merge, release, universal naming claim or default recurring disclosure.
