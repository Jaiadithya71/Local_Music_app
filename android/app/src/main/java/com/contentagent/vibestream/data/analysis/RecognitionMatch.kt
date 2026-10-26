package com.contentagent.vibestream.data.analysis

import com.google.gson.JsonObject
import kotlin.math.abs

/** Conservative prototype acceptance. A provider response alone is not an identity. */
object RecognitionMatch {
    data class Match(val id: String, val title: String, val artist: String, val offsetMs: Long)
    fun parse(json: JsonObject): Match? {
        val matches = json.getAsJsonArray("matches") ?: return null
        if (matches.size() != 1) return null
        val match = matches[0].asJsonObject
        val timeSkew = match.get("timeskew")?.asDouble ?: return null
        val frequencySkew = match.get("frequencyskew")?.asDouble ?: return null
        if (!timeSkew.isFinite() || !frequencySkew.isFinite() || abs(timeSkew) > 0.005 || abs(frequencySkew) > 0.005) return null
        val offset = match.get("offset")?.asDouble ?: return null
        if (!offset.isFinite() || offset < 0) return null
        val track = json.getAsJsonObject("track") ?: return null
        val id = track.get("key")?.asString ?: return null
        val title = track.get("title")?.asString?.takeIf { it.isNotBlank() } ?: return null
        return Match(id, title, track.get("subtitle")?.asString.orEmpty(), (offset * 1000).toLong())
    }
}
