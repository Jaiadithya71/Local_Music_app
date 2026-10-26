package com.contentagent.vibestream.data.analysis

/** Pure, backend-independent prototype. Observations are not trusted song cuts. */
object RecognitionTimeline {
    data class Observation(val positionMs: Long, val recordingId: String?, val title: String? = null,
                           val artist: String? = null, val candidateStartMs: Long? = null)
    data class Segment(val startMs: Long, val endMs: Long, val recordingId: String?, val title: String,
                       val boundaryUncertaintyMs: Long)

    fun windowStarts(durationMs: Long, stepMs: Long = 30000, windowMs: Long = 12000): List<Long> {
        require(durationMs > 0 && stepMs >= windowMs && windowMs > 0)
        val last = (durationMs - windowMs).coerceAtLeast(0)
        val points = generateSequence(0L) { it + stepMs }.takeWhile { it <= last }.toMutableList()
        if (points.last() != last) points.add(last)
        return points
    }

    /** Two neighboring matches confirm a name. Isolated matches stay Unknown.
     * No global ID deduplication: A/B/A is three songs, not two.
     * Provider start offsets are deliberately not used until independently verified.
     */
    fun assemble(observations: List<Observation>, durationMs: Long): List<Segment> {
        require(durationMs > 0)
        val points = observations.filter { it.positionMs in 0 until durationMs }
            .sortedBy { it.positionMs }.distinctBy { it.positionMs }
        if (points.isEmpty()) return listOf(Segment(0, durationMs, null, "Unknown", durationMs))
        val groups = mutableListOf<MutableList<Observation>>()
        points.forEach { point ->
            if (groups.isEmpty() || groups.last().last().recordingId != point.recordingId)
                groups.add(mutableListOf(point)) else groups.last().add(point)
        }
        val result = mutableListOf<Segment>()
        groups.forEachIndexed { i, group ->
            val identified = group.size >= 2 && !group[0].recordingId.isNullOrBlank() && !group[0].title.isNullOrBlank()
            val start = if (i == 0) 0 else (groups[i - 1].last().positionMs + group.first().positionMs) / 2
            val end = if (i == groups.lastIndex) durationMs else (group.last().positionMs + groups[i + 1].first().positionMs) / 2
            val uncertainty = maxOf(
                if (i == 0) group.first().positionMs else group.first().positionMs - groups[i - 1].last().positionMs,
                if (i == groups.lastIndex) durationMs - group.last().positionMs else groups[i + 1].first().positionMs - group.last().positionMs)
            val segment = Segment(start, end, group[0].recordingId.takeIf { identified },
                if (identified) group[0].title!! else "Unknown", uncertainty)
            if (result.lastOrNull()?.recordingId == null && segment.recordingId == null && result.isNotEmpty()) {
                val previous = result.removeAt(result.lastIndex)
                result.add(previous.copy(endMs = end, boundaryUncertaintyMs = maxOf(previous.boundaryUncertaintyMs, uncertainty)))
            } else result.add(segment)
        }
        return result
    }
}
