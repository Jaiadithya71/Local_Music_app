package com.contentagent.vibestream.data.recommendation

import com.contentagent.vibestream.data.model.Song
import java.util.Locale
import kotlin.math.max
import kotlin.math.pow

enum class Mood(val label: String) {
    CHILL("Chill"), FOCUS("Focus"), ENERGETIC("Energetic"), MELANCHOLY("Melancholy"), DEVOTIONAL("Devotional")
}

/** One logged play. Stored on the device only. */
data class PlayEvent(
    val songId: String = "",
    val artist: String = "",
    val title: String = "",
    val startMs: Long = 0L,
    val listenedSec: Int = 0,
    val durationSec: Int = 0,
    val outcome: String = MoodEngine.FINISHED,
    val source: String = "",
    val liked: Boolean = false,
    val hour: Int = 12,
    val weekend: Boolean = false,
    val route: String = "speaker",
    val charging: Boolean = false,
    val motion: String = "unknown"
)

/** Time bucket + audio route + motion: the "state" a song is judged in. */
data class MoodState(val bucket: String, val route: String, val motion: String) {
    val key: String get() = "$bucket|$route|$motion"
    val label: String get() = bucket.replace('_', ' ') + " · " + route + " · " + motion
}

class MoodStat {
    var plays = 0.0
    var fin = 0.0
    var rep = 0.0
    var skip = 0.0
    var like = 0.0
    fun add(outcome: String, liked: Boolean, w: Double) {
        plays += w
        if (outcome == MoodEngine.FINISHED || outcome == MoodEngine.REPLAYED) fin += w
        if (outcome == MoodEngine.REPLAYED) rep += w
        if (outcome == MoodEngine.SKIP_EARLY) skip += w
        if (liked) like += w
    }
}

class MoodModel(val global: Map<String, MoodStat>, val byState: Map<String, Map<String, MoodStat>>, val count: Int)

/** Pure Kotlin, no Android types. Learns from his own behaviour, never from audio. */
object MoodEngine {
    const val FINISHED = "finished"
    const val SKIP_EARLY = "skip_early"
    const val SKIP_LATE = "skip_late"
    const val REPLAYED = "replayed"
    const val MIN_PLAYS = 30
    const val HALF_LIFE_DAYS = 21.0
    const val GLIDE_GAP_MS = 20 * 60_000L
    const val GLIDE_SONGS = 6
    val CALM_TAGS: Set<String> = setOf("chill", "lofi", "slow", "melody", "melodies", "acoustic", "soft", "relax", "relaxing", "calm", "soothing", "unplugged", "sleep", "instrumental", "piano", "ambient", "flute", "veena")

    val MOOD_TAGS: Map<Mood, Set<String>> = mapOf(
        Mood.CHILL to setOf("chill", "lofi", "slow", "melody", "melodies", "acoustic", "soft", "relax", "relaxing", "calm", "soothing", "unplugged", "love", "romantic", "night", "sleep"),
        Mood.FOCUS to setOf("focus", "study", "instrumental", "piano", "ambient", "concentration", "work", "deep", "bgm", "classical", "flute", "veena"),
        Mood.ENERGETIC to setOf("workout", "gym", "dance", "party", "mass", "beat", "beats", "remix", "fast", "energetic", "pump", "hype", "dj", "folk", "kuthu"),
        Mood.MELANCHOLY to setOf("sad", "heartbreak", "break", "breakup", "pain", "melancholy", "emotional", "tears", "cry", "lonely", "miss", "sorrow"),
        Mood.DEVOTIONAL to setOf("devotional", "bhakti", "bhajan", "suprabhatam", "murugan", "krishna", "perumal", "ganesh", "ganapathi", "shiva", "amman", "hanuman", "mantra", "aarti", "stotram", "ayyappan", "sai", "god", "temple")
    )
    private val LANGUAGE_TAGS = setOf("tamil", "hindi", "telugu", "english", "malayalam", "kannada", "punjabi", "retro", "old", "90s", "80s", "2000s")
    private val VOCAB: Set<String> = MOOD_TAGS.values.flatten().toSet() + LANGUAGE_TAGS
    private val SPLIT = Regex("[^\\p{L}\\p{N}]+")

    fun norm(artist: String): String {
        val a = artist.trim().lowercase(Locale.ROOT)
        return if (a == "unknown artist") "" else a
    }

    fun tagsOf(title: String, artist: String): Set<String> =
        (title + " " + artist).lowercase(Locale.ROOT).split(SPLIT).filter { it in VOCAB }.toSet()

    fun keysOf(title: String, artist: String): List<String> {
        val keys = tagsOf(title, artist).map { "t:$it" }.toMutableList()
        val a = norm(artist)
        if (a.isNotEmpty()) keys.add("a:$a")
        return keys
    }

    fun bucket(hour: Int): String = when (hour) {
        in 5..11 -> "morning"
        in 12..16 -> "afternoon"
        in 17..21 -> "evening"
        else -> "late_night"
    }

    fun stateOf(e: PlayEvent) = MoodState(bucket(e.hour), e.route, e.motion)

    fun build(events: List<PlayEvent>, now: Long): MoodModel {
        val global = HashMap<String, MoodStat>()
        val by = HashMap<String, HashMap<String, MoodStat>>()
        for (e in events) {
            val ageDays = max(0.0, (now - e.startMs) / 86_400_000.0)
            val w = 0.5.pow(ageDays / HALF_LIFE_DAYS)
            val sk = stateOf(e).key
            for (k in keysOf(e.title, e.artist)) {
                global.getOrPut(k) { MoodStat() }.add(e.outcome, e.liked, w)
                by.getOrPut(sk) { HashMap() }.getOrPut(k) { MoodStat() }.add(e.outcome, e.liked, w)
            }
        }
        return MoodModel(global, by, events.size)
    }

    /** True when the last three plays were all early skips in the last 30 minutes. */
    fun notFeelingThis(events: List<PlayEvent>, now: Long): Boolean {
        val last = events.sortedBy { it.startMs }.takeLast(3)
        return last.size == 3 && last.all { it.outcome == SKIP_EARLY && now - it.startMs < 30 * 60_000L }
    }

    /** Named mood for the chip: his correction for this state wins, else the best tag-bundle fit. */
    fun currentMood(events: List<PlayEvent>, now: Long, state: MoodState, overrides: Map<String, String>): Mood? {
        if (events.size < MIN_PLAYS) return null
        overrides[state.key]?.let { name -> Mood.values().firstOrNull { it.name == name }?.let { return it } }
        val model = build(events, now)
        fun best(src: Map<String, MoodStat>): Mood? {
            var bestMood: Mood? = null
            var bestScore = 0.0
            for (m in Mood.values()) {
                var sc = 0.0
                for (tag in MOOD_TAGS.getValue(m)) {
                    val st = src["t:$tag"] ?: continue
                    sc += (st.fin + st.like - st.skip) / (st.plays + 1.0)
                }
                if (sc > bestScore) { bestScore = sc; bestMood = m }
            }
            return bestMood
        }
        best(model.byState[state.key].orEmpty())?.let { return it }
        best(model.global)?.let { return it }
        return when {
            state.motion == "walking" -> Mood.ENERGETIC
            state.bucket == "morning" || state.bucket == "afternoon" -> Mood.FOCUS
            else -> Mood.CHILL
        }
    }
}

/**
 * Scores and reorders candidate songs. Below MIN_PLAYS it changes nothing, so the old
 * taste ranking stays the cold-start behaviour. The old order is blended back in as a
 * prior that fades as plays accumulate.
 */
class MoodRanker(
    private val events: List<PlayEvent>,
    private val now: Long,
    private val state: MoodState,
    private val mood: Mood? = null
) {
    private val model = MoodEngine.build(events, now)
    private val sorted = events.sortedBy { it.startMs }
    private val today = sorted.filter { it.startMs >= now - 86_400_000L }
    private val artistToday: Map<String, Int> = today.map { MoodEngine.norm(it.artist) }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount()
    private val tagToday: Map<String, Int> = today.flatMap { MoodEngine.tagsOf(it.title, it.artist) }.groupingBy { it }.eachCount()
    private val recentIds: Set<String> = sorted.takeLast(50).map { it.songId }.toSet()
    private val sessionTags: Set<String> = sorted
        .filter { it.startMs >= now - 30 * 60_000L && (it.outcome == MoodEngine.FINISHED || it.outcome == MoodEngine.REPLAYED) }
        .flatMap { MoodEngine.tagsOf(it.title, it.artist) }.toSet()
    private val recentEarlySkipArtists: Set<String> = sorted
        .filter { it.outcome == MoodEngine.SKIP_EARLY && now - it.startMs < 2 * 3_600_000L }
        .map { MoodEngine.norm(it.artist) }.filter { it.isNotEmpty() }.toSet()
    private val avoid: Set<String> = lane()

    /**
     * Glide: when listening starts after an idle gap (20+ minutes with no play), the first
     * picks lean calm and the lean fades over the next five songs. 0 once the session is going.
     */
    private val glide: Double = run {
        var n = 0
        var edge = now
        for (e in sorted.asReversed()) {
            val end = e.startMs + e.listenedSec * 1000L
            if (edge - end > MoodEngine.GLIDE_GAP_MS) break
            n++
            edge = e.startMs
        }
        if (n >= MoodEngine.GLIDE_SONGS) 0.0 else 0.6 * (1.0 - n.toDouble() / MoodEngine.GLIDE_SONGS)
    }

    private fun calmBonus(s: Song): Double =
        if (glide > 0.0 && MoodEngine.tagsOf(s.title, s.artist).any { it in MoodEngine.CALM_TAGS }) glide else 0.0
    private val moodTags: Set<String> = mood?.let { MoodEngine.MOOD_TAGS.getValue(it) } ?: emptySet()

    /** After 2 early skips in a row, drop that lane (shared tags or artists) for the next picks. */
    private fun lane(): Set<String> {
        val last = sorted.takeLast(2)
        if (last.size < 2 || !last.all { it.outcome == MoodEngine.SKIP_EARLY && now - it.startMs < 30 * 60_000L }) return emptySet()
        val a = MoodEngine.keysOf(last[0].title, last[0].artist).toSet()
        val b = MoodEngine.keysOf(last[1].title, last[1].artist).toSet()
        val common = a.intersect(b)
        if (common.isNotEmpty()) return common
        return (a + b).filter { it.startsWith("a:") }.toSet()
    }

    class Parts(
        val pFinish: Double, val pReplay: Double, val stateMatch: Double, val novelty: Double,
        val boredom: Double, val skipPenalty: Double, val laneDrop: Double, val moodBoost: Double, val session: Double
    ) {
        val score: Double get() = pFinish * 1.0 + pReplay * 0.5 + stateMatch * 0.8 + novelty + session + moodBoost - boredom - skipPenalty - laneDrop
    }

    fun parts(s: Song): Parts {
        val keys = MoodEngine.keysOf(s.title, s.artist)
        val tags = MoodEngine.tagsOf(s.title, s.artist)
        val g = keys.mapNotNull { model.global[it] }
        val st = model.byState[state.key].orEmpty()
        val sk = keys.mapNotNull { st[it] }
        val pF = if (g.isEmpty()) 0.5 else g.map { (it.fin + 1.0) / (it.plays + 2.0) }.average()
        val pR = if (g.isEmpty()) 0.1 else g.map { (it.rep + 0.1) / (it.plays + 2.0) }.average()
        val sm = if (sk.isEmpty()) 0.0 else sk.map { (it.fin - it.skip) / (it.plays + 2.0) }.average()
        val novelty = if (sk.isEmpty()) 0.3 else 0.0
        val sess = if (tags.isEmpty() || sessionTags.isEmpty()) 0.0 else 0.3 * tags.count { it in sessionTags } / tags.size
        val artist = MoodEngine.norm(s.artist)
        var bored = 0.0
        val na = artistToday[artist] ?: 0
        if (artist.isNotEmpty() && na >= 3) bored += 0.2 * (na - 2)
        var tagBore = 0.0
        for (t in tags) tagBore += 0.05 * max(0, (tagToday[t] ?: 0) - 3)
        bored = minOf(1.0, bored + minOf(0.6, tagBore))
        val skipPen = if (artist.isNotEmpty() && artist in recentEarlySkipArtists) 0.4 else 0.0
        val laneDrop = if (avoid.isNotEmpty() && keys.any { it in avoid }) 1.0 else 0.0
        val boost = if (moodTags.isNotEmpty() && tags.any { it in moodTags }) 0.5 else 0.0
        return Parts(pF, pR, sm, novelty, bored, skipPen, laneDrop, boost, sess)
    }

    fun explain(s: Song): String {
        val p = parts(s)
        val tags = MoodEngine.tagsOf(s.title, s.artist).sorted().joinToString(",").ifEmpty { "no tags" }
        fun f(d: Double) = String.format(Locale.US, "%.2f", d)
        return "state ${state.label} | tags $tags | finish ${f(p.pFinish)} | state ${f(p.stateMatch)} | " +
            (if (p.novelty > 0) "new in this state | " else "") +
            (if (p.moodBoost > 0) "fits ${mood?.label} | " else "") +
            (if (p.boredom > 0) "boredom -${f(p.boredom)} | " else "") +
            (if (p.skipPenalty > 0) "recent skip -${f(p.skipPenalty)} | " else "") +
            (if (p.laneDrop > 0) "lane dropped | " else "") +
            (if (calmBonus(s) > 0) "calm glide | " else "") +
            "score ${f(p.score)}"
    }

    private fun similar(s: Song, last: List<Song>): Boolean {
        if (last.size < 2) return false
        val t = MoodEngine.tagsOf(s.title, s.artist)
        if (t.isEmpty()) return false
        return last.all { MoodEngine.tagsOf(it.title, it.artist) == t }
    }

    fun rank(list: List<Song>): List<Song> {
        if (list.size < 2) return list
        if (model.count < MoodEngine.MIN_PLAYS) {
            // Too few plays to learn from: only the idle-gap glide applies (stable sort keeps the old order otherwise).
            return if (glide > 0.0) list.sortedByDescending { calmBonus(it) } else list
        }
        val w = model.count.toDouble() / (model.count + 50.0)
        val n = list.size
        class Item(val song: Song, val parts: Parts, val blended: Double)
        val items = list.mapIndexed { i, s ->
            val p = parts(s)
            Item(s, p, w * p.score + (1.0 - w) * (1.0 - i.toDouble() / n) + calmBonus(s))
        }
        val byScore = items.sortedByDescending { it.blended }
        // Songs played in the last 50 go to the back, not out: a thin pool still fills.
        val pool = (byScore.filter { it.song.id !in recentIds } + byScore.filter { it.song.id in recentIds }).toMutableList()
        val out = ArrayList<Song>(n)
        val firstLast = MoodEngine.norm(sorted.lastOrNull()?.artist ?: "")
        while (pool.isNotEmpty()) {
            val lastArtist = out.lastOrNull()?.let { MoodEngine.norm(it.artist) } ?: firstLast
            val tail = out.takeLast(2)
            fun ok(it: Item): Boolean {
                val a = MoodEngine.norm(it.song.artist)
                return (a.isEmpty() || a != lastArtist) && !similar(it.song, tail)
            }
            // One slot in five goes to a song with no history in this state.
            var idx = -1
            if (out.size % 5 == 4) idx = pool.indexOfFirst { it.parts.novelty > 0 && ok(it) }
            if (idx < 0) idx = pool.indexOfFirst { ok(it) }
            if (idx < 0) idx = 0
            out.add(pool.removeAt(idx).song)
        }
        return out
    }
}
