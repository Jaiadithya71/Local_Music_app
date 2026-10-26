# Fingerprint implementation reference

AudioFingerprint.kt is a Kotlin adaptation of the spectral signature algorithm
and packet layout in shazamio-core, copyright 2024 dotX12, MIT. The MIT text is
included here and must travel with any binary distribution containing this code.
Reference pinned to26704891775d872071c4487356cf19a11dee4c59:
https://github.com/shazamio/shazamio-core

The upstream source names SongRec as algorithm research/reference. GPL source has
not been imported into this project. This note records provenance; it is not a
legal opinion about upstream ownership. Any unresolved material provenance issue
must be settled before release. The new generator is unshipped and must pass an
independent generated-audio signature comparison and recognition gates.

Test probe is self-generated, mono16kHz PCM chirp/tones,8seconds. Golden signature
produced separately using installed shazamio-core from the same public project.
No commercial audio is included.
