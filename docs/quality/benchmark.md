# Playback and recommendation regression gates

This checklist is an engineering benchmark, not an audible-quality certificate.

## Identity and listening
- Match title, artist, album/version and duration before comparing recordings. A cover, live take or altered upload is not a codec comparison.
- Compare on the same phone and output route; log app version, quality settings, EQ, normalization and spatial settings. Match perceived loudness, randomize app order and use identical excerpts when legally available.
- Do not equate container, file extension, bitrate across different codecs, or sample rate with perceived quality. Do not label an extracted stream lossless without evidence.
- Keep source metadata and actual decoded format separate from device/output capability. No claim that source code tests certify audible fidelity.

## Suggested pilot gates (targets, not measured results)
- Recording match: 20 known song/version queries, top-result and top-five correctness separately. No wrong versions silently accepted.
- Playback: 30 local fixture starts, repeated next/previous, rapid A/B taps, pause/resume, seek, timer and a 30-minute continuous queue. Record failures, time to ready, rebuffer count/time and transition gaps. Use median and p95, disclose sample sizes.
- Mixes: count fresh-vs-library contributions, unique recordings and artists, consecutive same-artist pairs, blocked artists and video leakage. Keep language/mood adherence separate from personal enjoyment.
- Interaction: taps and elapsed time from launch to music, cold and warm; notification controls, route changes and lock-screen survival.
- Audible comparison is a separate listening gate, with matched recordings and conditions. Unavailable controls mean unverified, not passed.

## Automated coverage
- MixBuilderTest covers source interleaving, recording deduplication, adjacent artist spreading, negative artist signals, video exclusion, empty inputs, source representation and named-mix scope.
- PlaybackSmokeTest uses generated PCM through the actual service/controller, with no commercial audio or account data. It checks clock advancement, audio-session assignment, pause/resume, seek, next and timer pause. Emulator behavior does not establish handset/Bluetooth sound quality.
