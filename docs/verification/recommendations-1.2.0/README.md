# Song-seeded discovery and loading cache, v1.2.0

Baseline: v1.1.4 / d77f994. The baseline tag/release/APK is retained unchanged.

## What changed

- Home's first discovery shelf and Your mood interleave anonymous YouTube Music radio candidates with taste-scoped keyword search. At most three distinct liked/playlist song IDs seed each refresh; seeds rotate. No claim that recent playback means completion or approval.
- Requests run in parallel with keyword loading, with an 8-second radio call timeout and search fallback. No account, backend, paid service, embeddings or collaborative model added.
- Local radio cache: 24-hour freshness, at most 64 seed pools of 50 tracks. Search cache: 6-hour freshness, at most 64 pools; explicit New picks still refreshes keyword pools. All current seed-query pools restore before refresh, not only named shelves. Stale radio remains usable during an outage.
- Known-song exclusion runs AFTER cache restore/blending, every composition, so played/liked/saved tracks never become discovery padding.
- Recording filters reject explicit spoken/reaction/review/tutorial/compilation/jukebox/non-stop/full-album labels. No duration-only or live-music ban. English-titled Tamil songs remain eligible. Explicit language labels/Tamil script are soft ordering hints, not language detection. Unknown titles stay eligible.
- Coil artwork disk cache capped at 256 MiB; downloads remain separate. Home's Loading cache control explains limits and clears disposable artwork/pools/streamed audio only, not taste, library, played ledger or explicit downloads. Artwork will load again over the network after clearing.
- Existing next-track resolution/ExoPlayer append/prebuffer is unchanged. Added a 512 MiB LRU read-through playback cache, storing only bytes Media3 requests, not new whole-song predownloads. Cache keys distinguish encoding/length; signed URL expiry does not invalidate the same identified media bytes. Cache errors bypass to network. Stream fallback now preserves position and play/pause intent, is bounded to five recoveries per track, and can refresh a stale stream URL once. No audio quality downgrade. No arbitrary larger buffer setting: Media3 load control remains default until evidence warrants tuning.
- Existing application ID, library formats and persistent CI signing configuration are unchanged. No data migration/reset needed.

## Privacy and costs

Taste/history/profile remain on the device. Radio necessarily sends selected seed IDs to YouTube, anonymously, and normal network metadata is visible to it. This is not a zero-disclosure/offline system. Artwork/pool reuse saves repeat requests; a first radio refresh adds up to three calls. No promised quality or phone speed increase.

## Measured checks

`LiveSongRadioTest` runs the actual Kotlin extractor/parser/cache/ranking code against public example song seeds, not private listening data. On 2026-11-04 each of three seed radios returned 49 candidates, baseline query 30. Cold requests took 775-813 ms in this test environment; cached second reads 0-10 ms, with exactly one fetch for two reads. New top-20 lists contained 12-13 items outside baseline top-20. Simulated known-song overlap was zero. Full lists and IDs are in `public-catalog-comparison.json`.

These establish functioning radio, changed candidate pools, cache reuse and no-repeat filtering. They do NOT establish better personal relevance, lower real early-skip rate, audible quality, or phone UI/network latency. Example radios include non-Tamil tracks with unlabelled English titles; metadata cannot reliably identify the sung language. Popularity bias and unofficial endpoint breakage remain risks.

Automated regression covers seed rotation/bounds, metadata parser, conservative exceptions, interleaving/fallback, local TTL/offline behavior/cancellation, actual Compose radio-to-queue flow, warm saved Home pools, cache clear preserving liked/played data, discovery repeats, queue reconnect and persistence. CI repeats these before publishing the signed APK.

## Compare or roll back

Keep the v1.1.4 release for source/candidate comparison. Do not uninstall/clear app data to compare. Android can reject an older versionCode; a future baseline-mode build can be signed as an update if needed. The new build does not alter the saved library schema. Export playlist backup before any manual downgrade.

## Remaining validation

No physical-phone test of this build. No claim of better musical similarity, sound quality or real-world skip rate. UI rendering is actual Compose/Robolectric with synthetic fixture tracks. Real YouTube network conditions vary by region and device.

## Mobile buffering report

User reported periodic stalls on mobile data (Wi-Fi not established). Source audit found no prior audio disk cache and backup-stream recovery restarting from zero. These are concrete gaps, not proof of the cause of the reported stalls. Network throughput variation, CDN stalls and URL expiry remain hypotheses. Added local-only rebuffer/format diagnostics. Generated PCM interruption/range-retry and offline-cache replay is tested in the playback workflow; see its result before treating this as ready. No claim that this eliminates first-listen network stalls.
