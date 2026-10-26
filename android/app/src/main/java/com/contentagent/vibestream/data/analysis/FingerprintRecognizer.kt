package com.contentagent.vibestream.data.analysis

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Explicitly opt-in unofficial service. Sends a fingerprint, never audio or source URI. */
class FingerprintRecognizer {
    private val client = OkHttpClient.Builder().connectTimeout(12,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
    fun recognize(pcm: ShortArray): RecognitionMatch.Match? {
        val signature = AudioFingerprint.signature(pcm)
        val payload = JsonObject().apply {
            addProperty("timezone","UTC")
            addProperty("timestamp",System.currentTimeMillis())
            add("signature",JsonObject().apply { addProperty("uri",signature);addProperty("samplems",pcm.size / 16) })
            add("context",JsonObject());add("geolocation",JsonObject())
        }
        val url = "https://amp.shazam.com/discovery/v5/en/US/iphone/-/tag/${UUID.randomUUID()}/${UUID.randomUUID()}?sync=true&webv3=true&sampling=true"
        val request = Request.Builder().url(url).header("User-Agent","Mozilla/5.0")
            .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { response ->
            // No blind retries, anti-bot workarounds or fallback hosts.
            check(response.isSuccessful) { "Recognition service unavailable (${response.code})" }
            RecognitionMatch.parse(JsonParser.parseString(response.body?.string() ?: "{}").asJsonObject)
        }
    }
}
