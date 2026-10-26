# Song navigation inside a compilation

Publisher chapters are stored as absolute millisecond starts on the original
recording. Next first seeks to the next chapter. After the last chapter it uses
the normal queue. The same policy runs in the playback service for both Media3
Next commands and Android media keys, before the prefetched queue item. Previous
restarts the current chapter after three seconds, otherwise goes back a chapter.
Audio remains one stream; no music is copied or split into redistributed files.

## Sources and limits
- Uses NewPipeExtractor 0.26.5 StreamInfo.streamSegments, or a timestamped description.
  API source: https://github.com/TeamNewPipe/NewPipeExtractor/blob/v0.26.5/extractor/src/main/java/org/schabi/newpipe/extractor/stream/StreamInfo.java
- Description fallback requires at least two ascending timestamps on separate lines
  with titles. It rejects lone highlights, ambiguous lines, scrambled lists and
  out-of-duration points. A chapter is a publisher-supplied navigation marker,
  not independently recognized music. Publisher mistakes remain possible.
- No equally spaced guessed cuts or silence-only song recognition. Recordings
  without timestamps retain ordinary Next and the player states that limit.
- Queue saves retain boundaries but still strip expiring stream URLs. Old queues
  without chapter fields remain valid. Existing cached long recordings load
  metadata in the background; playback does not wait for that network request.
- For prefetched recordings the same metadata is carried with the MediaItem.
- No claim of testing a physical Bluetooth accessory, every OEM media stack,
  unknown unmarked compilations, or the listener's exact recording.

## Regression gates
SongChaptersTest covers unequal times, hours, filtering, malformed descriptions,
next/previous and final-boundary fallback. ChapterPersistenceTest covers queue
round-trip, old JSON and media metadata. PlaybackSmokeTest runs generated PCM,
MediaController commands and AudioManager MEDIA_NEXT, with a prefetched next item.
LiveChapterTest is a separate bounded public metadata probe, not music playback.
Its failure does not suppress the offline Android regression run.

Public timestamp cross-check fixture: Bhoomi official jukebox, video ges_8ILnAo4,
0:00, 4:28, 9:03, 13:20, 17:20. Source accessed 2026-10-26:
https://www.studioflicks.com/video/bhoomi-songs-jukebox/

Version/build/test results belong in release evidence, not assumptions here.

Live metadata probe status: anonymous watch access on CI was blocked by YouTube
with LOGIN_REQUIRED / confirm-not-bot. Repeated push-time probes are disabled.
This is a verification blocker, not evidence of working publisher extraction on
that environment. The user's phone/source route has not been tested here.
