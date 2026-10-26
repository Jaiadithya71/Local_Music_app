package com.contentagent.vibestream.data.analysis

import com.contentagent.vibestream.data.chapters.SongChapter

/** Bounded automatic prototype. Never guesses labels for unrecognized sections. */
class CompilationIndexer(
    private val decode: (String,Long,() -> Boolean) -> ShortArray = { source,start,cancelled -> AudioWindowDecoder().decode(source,start,12,cancelled=cancelled).pcm16k },
    private val recognize: (ShortArray) -> RecognitionMatch.Match? = { FingerprintRecognizer().recognize(it) },
    private val throttle: () -> Unit = { Thread.sleep(1200) }
) {
    data class Result(val chapters: List<SongChapter>, val observations: Int, val approximate: Boolean)
    fun index(source: String, durationMs: Long, cancelled: () -> Boolean = { false },
              progress: (Int, Int) -> Unit = { _, _ -> }): Result {
        require(durationMs in 600000..10800000) { "Automatic analysis supports recordings from 10 minutes to 3 hours" }
        val starts = RecognitionTimeline.windowStarts(durationMs,60000,12000)
        val observations = sortedMapOf<Long,RecognitionTimeline.Observation>()
        var calls = 0
        fun sample(start: Long): RecognitionTimeline.Observation {
            check(!cancelled()) { "Analysis cancelled" }
            observations[start]?.let { return it }
            check(calls < 240) { "Analysis request limit reached" }
            val pcm = decode(source,start,cancelled)
            if (pcm.size < 48000) return RecognitionTimeline.Observation(start,null)
            val match = recognize(pcm)
            calls++
            val point = RecognitionTimeline.Observation(start,match?.id,match?.title,match?.artist,
                match?.let { start - it.offsetMs })
            observations[start]=point
            progress(calls,starts.size)
            // Serialize and cap load. Stop outright on service errors, no retry storm.
            throttle()
            return point
        }
        for (start in starts) {
            val point=sample(start)
            // A second independent nearby window confirms each provisional identity.
            if (point.recordingId != null && start+12000 < durationMs-12000) sample(start+12000)
        }
        // Refine changes, but avoid claiming the sample grid is an exact song start.
        for ((a,b) in starts.zipWithNext()) {
            if (observations[a]?.recordingId == observations[b]?.recordingId) continue
            var lo=a;var hi=b
            while (hi-lo>4000 && calls<240) {
                val middle=(lo+hi)/2
                val point=sample(middle)
                if (point.recordingId == observations[lo]?.recordingId) lo=middle else hi=middle
            }
        }
        val segments=RecognitionTimeline.assemble(observations.values.toList(),durationMs)
        return Result(segments.map { segment ->
            SongChapter(if(segment.recordingId==null) "Unknown" else "${segment.title} (approx.)",segment.startMs)
        },calls,true)
    }
}
