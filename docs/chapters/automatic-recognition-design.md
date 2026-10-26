# Automatic long-recording analysis: design gate, not a shipped feature

## Goal
Each newly encountered long recording is automatically indexed into named,
selectable song positions, and media Next uses those positions. No per-file
manual annotation. Publisher metadata is one input, not a universal solution.

## Pipeline proposed for an isolated prototype
1. Prefer validated publisher chapters. Decode the current recording with
   Android MediaExtractor/MediaCodec when metadata is absent. No microphone.
2. Decode bounded PCM windows to mono 16 kHz. Retain sample timestamps, trim
   encoder preroll, respect output format changes and codec/channel layout.
   Never load a full 45-minute PCM file into memory.
3. Generate fingerprints on the device. An approved recognition backend may
   receive only fingerprints plus protocol fields, not the user's history,
   account, source URL or complete audio. No geo coordinates or persistent ID.
4. Sample at a bounded interval, cache results by source identity/version, and
   confirm identity changes with neighboring samples. A sampling grid is not
   a song-boundary grid. Refine around changes. Provider recording offsets,
   when available and checked, can estimate true song starts.
5. Preserve separate confidence for identity and boundary. Crossfades can have
   two valid simultaneous songs; unmatched/altered sections stay Unknown.
   Do not invent names or claim every file is recognized.
6. Persist named cues locally and attach them to media metadata, retaining
   existing queue fallback, shuffle, repeat, offline playback and resume.

## Gates before release
- Permission and permitted service route established before any recognition call.
- Fingerprint implementation and dependency provenance/licences reviewed.
- PCM decoding of AAC/Opus/PCM, stereo mixdown, sample rate conversion, seek
  preroll and cancellation checked with generated fixtures.
- Reference signatures match independently generated expected signatures.
- Self-owned sample recognition, catalog miss and silence tested. Licensed or
  user-authorized known recordings required to measure real label accuracy.
- Multi-song identity changes and timestamps checked against known boundaries,
  including fades, continuous medleys, silence inside songs and short tracks.
- Data, CPU, memory, duration and request ceilings measured and exposed.
- No parallel flood, automatic wall evasion, blind retries on throttling,
  copied music, background paid API use or embedded account credentials.

## Investigated routes and constraints (2026-10-26)
- AcoustID https://acoustid.org/faq explicitly excludes arbitrary short snippets;
  https://acoustid.org/webservice requires an application key, non-commercial
  scope and max 3 requests/second. Not a drop-in unknown-mix scanner.
- Official Android ShazamKit https://developer.apple.com/shazamkit/android/
  requires developer token for ShazamCatalog. Media-key setup:
  https://developer.apple.com/help/account/capabilities/create-a-media-identifier-and-private-key/
- AudD https://audd.io/ advertises 300 trial requests then paid use. No account
  or spending is part of this prototype.
- SoundCrowd https://github.com/soundcrowd/soundcrowd/ demonstrates internal
  tagging via SongRec. GPLv3 references are not copied into this MIT project.
- ShazamIO https://github.com/shazamio/ShazamIO is an unofficial endpoint client.
  shazamio-core https://github.com/shazamio/shazamio-core declares MIT but also
  references SongRec algorithm work. Its code has not been imported here.
- No network recognition operation has been run as part of this design.

Android decoder references:
https://developer.android.com/reference/android/media/MediaExtractor
https://developer.android.com/reference/android/media/MediaCodec
