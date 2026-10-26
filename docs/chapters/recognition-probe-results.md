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

These are two positive windows in one recording and one synthetic rejection,
not a genre/crossfade/catalog benchmark. Tamil catalog coverage unmeasured.
Boundary refinement remains approximate; Unknown segments remain visible.
Android generator's independent golden test and decoder resampling gates are
separate from these Python reference-client provider checks. Automatic processor
is isolated, not wired to user playback or enabled for background disclosures.
