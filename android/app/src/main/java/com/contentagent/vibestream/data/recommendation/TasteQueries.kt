package com.contentagent.vibestream.data.recommendation

/** Query planning only. Query labels are not proof that returned songs fit the mood. */
object TasteQueries {
    fun seeds(languages: List<String>, moods: List<String>, artists: List<String>): List<String> {
        val langs = languages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val feelings = moods.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val favourites = artists.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val planned = langs.map { "$it hit songs" } +
            feelings.flatMap { mood -> if (langs.isEmpty()) listOf("$mood songs") else langs.map { "$it $mood songs" } } +
            favourites.take(3).map { "$it songs" }
        return planned.ifEmpty { listOf("tamil hit songs", "bollywood hit songs", "retro classic songs") }.distinct()
    }
}
