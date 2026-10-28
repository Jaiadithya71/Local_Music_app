# Discovery correction: 28 October 2026

## Diagnosis

The previous mood mix interleaved liked, recent and playlist tracks with search results. Its repeat preference sorted only one source, not the full mix. The Home seed searched exact played titles and the cache could keep replay-heavy candidates. Recent history stored only30 entries. This could look like replay instead of discovery.

## Changed behavior

- Home discovery, Your mood and named mixes exclude retained played IDs, normalized-title matches, liked songs and playlist songs. Recently played remains an explicit replay shelf; Library and search stay available.
- No history/liked fallback if the public catalog fails or has no new-to-you candidates. The UI reports the shortage and offers Refresh/Edit taste.
- Language/mood/selected-artist queries rotate on refresh/re-entry. A depleted result batch gets one bounded expansion. No unrelated video-uploader drift.
- Selection happens after cache restore and again after a play signal. Old shelf cache version and legacy mood cache are no longer used; library data is not cleared.
- A local ID/title ledger remembers new plays beyond the30-item Recent display. It migrates retained records and remembers imported history before trimming. Discarded past history cannot be reconstructed.
- Unicode title normalization keeps combining marks, including Tamil. Common audio/video/lyric label changes and alternate upload IDs are filtered. This is conservative metadata matching, not acoustic fingerprinting; alternate spellings may escape and same-title songs may be excluded.

## Verification

- Deterministic policy tests cover large saved libraries, alternate uploads, exhausted pools, mode filtering, Tamil normalization, query rotation, dedup, immediate post-play removal and zero limit.
- Persistence tests cover ledger lifetime beyond30 plays, migration with liked/playlists intact and imported history. Existing queue/library tests remain included.
- Actual Compose Home test injects a public-catalog-shaped fixture through the production Home search boundary. Taps Your mood, checks all16 are new, records a play, taps again and checks15 remain; Refresh changes queries. Fixture artwork is deliberately not personal media. Render inspected separately.
- Bounded live check uses the app's actual YouTube Music extractor, not a replacement parser. Three Tamil catalog queries each returned30 songs. With five selected catalog tracks treated as known, both first and second50-track pools had zero ID/normalized-title overlap with the simulated played set. This verifies live candidate supply/filtering, not the owner's inaccessible private history or subjective taste.
- Signed release, Android playback checks and exact commit IDs are reported with delivery rather than assumed here.

## Not included

No compilation recognition/chapter prototype merged. No phone data uploaded, accounts added, or public personal screenshots/history committed. No claim of Amazon recommendation parity, universal song identification or subjective musical fit.
