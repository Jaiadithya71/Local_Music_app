package com.contentagent.vibestream.data.local

import android.content.Context
import com.google.gson.Gson

data class TasteProfile(
    val languages: List<String> = emptyList(),
    val moods: List<String> = emptyList(),
    val artists: List<String> = emptyList()
)

/** First-open taste answers, stored on the device only. */
class TasteStore(context: Context) {
    private val prefs = context.getSharedPreferences("vibestream_taste", Context.MODE_PRIVATE)

    fun isDone() = prefs.getBoolean("done", false)

    fun save(profile: TasteProfile) {
        prefs.edit().putBoolean("done", true).putString("profile", Gson().toJson(profile)).apply()
    }

    fun load(): TasteProfile {
        val json = prefs.getString("profile", null) ?: return TasteProfile()
        return try {
            Gson().fromJson(json, TasteProfile::class.java) ?: TasteProfile()
        } catch (e: Exception) {
            TasteProfile()
        }
    }
}
