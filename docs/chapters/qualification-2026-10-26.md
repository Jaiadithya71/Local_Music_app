# Qualification checkpoint: not release-ready

## Measured scan failure
A private10-minute compilation was assembled from200s each of public-domain
Paul Pitman Beethoven MoonlightIII, Veena Kinhal Haratanaya Sree, and Paul
Pitman Debussy La puerta del Vino. Kotlin CompilationIndexer, AudioFingerprint,
and RecognitionMatch ran against decoded16k mono PCM with the same1.2second
throttle. Five windows at0,12,60,72,120seconds matched Beethoven correctly.
The sixth request at132seconds returned HTTP429. Testing stopped immediately,
without retries, identity rotation, endpoint changes or timed reattempts.

No second-song, transition, miss-rate or crossfade accuracy claim follows.
No user audio was sent. Across both phases:11 requests total,10 responses and
one429. The earlier synthetic false positive was rejected; silence unmatched.

The60second discovery grid plus12second confirmation makes about21 initial
requests for10min, about121 for1hour, before transition refinement. A3hour file
would exceed the240-call cap even before all initial confirmations. The cap
bounds service load but does not establish scan completeness. The measured429
means1.2seconds is not a qualified durable cadence. No slower cadence has been
validated or promised, and recognition remains stopped.

## Legitimate benchmark sources
- https://archive.org/details/MoonlightSonata_845 : composition/performance PD.
- https://freemusicarchive.org/index.php/music/Veena_Kinhal/Tribute_to_Veena_Raja_Rao/Haratanaya_Sree/ : PD, South Indian instrumental.
- https://freemusicarchive.org/music/Paul_Pitman/Favorite_Works/Preludes_Book_2_-_La_puerta_del_Vino/ : PD.
- https://commons.wikimedia.org/wiki/File:Aasai_mugam.ogg : Bharathjosh, own recording of Indu Vadhani singing Bharathi, CC BY-SA3.0.
- https://commons.wikimedia.org/wiki/File:Ta-Senthamizh_Nadu.ogg : Bharathjosh, Nandhini/SSN recording, CC BY-SA3.0.
- https://commons.wikimedia.org/wiki/File:Ta-Chinanjiru.ogg : Bharathjosh, Harini/SSN recording, CC BY-SA3.0.
Tamil vocal material therefore exists legally for a private future test, but
no requests using it were made after429. These are not a modern film catalog
benchmark. Do not substitute a Tamil-named CC0 beat loop for Tamil song coverage.
No audio distributed with source code or reports.

## Provenance
https://github.com/shazamio/shazamio-core/blob/master/LICENSE declares MIT,
copyright2024 dotX12. The pinned Rust reference has explicit SongRec links in
algorithm.rs and signature_format.rs. Its first commit already has closely
matching ring/spreading/peak/packet structures. The linked SongRec revision is
GPLv3; constants, peak-neighbor sequence and ordering match across sources.

This is material unresolved provenance, not evidence of clean independent
MIT authorship and not a legal finding of infringement. Keeping an MIT notice
alone does not settle it. Release requires qualified provenance/licence review
or replacement with a separately permitted implementation. Do not silently
relicense the entire app or claim algorithm equivalence settles code copyright.
Sources:
- https://github.com/shazamio/shazamio-core/commit/6a3316b77a32541ab6b9e82176e136319a4d591a
- https://github.com/marin-m/SongRec/blob/eaa3ea3e1ce4da90e71881f9853564937db518a7/src/core/fingerprinting/algorithm.rs
- https://github.com/marin-m/SongRec/blob/eaa3ea3e1ce4da90e71881f9853564937db518a7/LICENSE

## Cache hardening
Local automatic cue cache now keys full SHA256 content, not path,size or mtime;
legacy song-ID cache entries are ignored. Rehash after analysis detects source
replacement during analysis. Automatic cues are stripped from persisted queue
and cleared before source restart, so restored cues cannot bypass validation.
Remote sources have no persistent automatic cache without a verifiable content
version. Full-file hashing increases disk I/O and latency; no performance claim.
Local standalone test covers same-size/same-mtime replacement and remote refusal;
Android/queue integration gates remain required for this latest change.

## Recommendation
Do not release universal automatic naming via this unofficial route. Preserve
prototype/default-off disclosure and stablev1.1.2. Independent local chapter
navigation and decoder tests do not establish catalog or service reliability.
An official ShazamKit route requires developer-token/account setup; a paid API
has its own account/usage limits. Neither is authorized in this task. A supported
route would need explicit terms/access, documented capacity for the actual scan
load, privacy consent, and the same real-compilation accuracy benchmark. No
service capacity or price is promised here.
