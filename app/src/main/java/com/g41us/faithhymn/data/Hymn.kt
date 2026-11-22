package com.g41us.faithhymn.data

data class Hymn(val number: Int, val title: String, val content: String, val firstLine: String) {
    /** Raw verses/chorus blocks, split on blank lines. */
    val blocks: List<String> get() = content.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
    /** Structured blocks with parts, dynamics and verse numbers. */
    val parsed: List<HymnBlock> by lazy { HymnFormat.parse(content) }
}

data class HymnSummary(val number: Int, val title: String, val firstLine: String, val snippet: String? = null, val titleHit: Boolean = false)

data class Playlist(val id: Long, val name: String, val count: Int, val serviceDate: String? = null, val color: Int = 0, val preview: List<Int> = emptyList())

data class PlaylistItem(val hymn: HymnSummary, val slot: String?)

data class SearchResults(val titleHits: List<HymnSummary>, val lyricHits: List<HymnSummary>, val correctedQuery: String? = null) {
    val all get() = titleHits + lyricHits
    val isEmpty get() = titleHits.isEmpty() && lyricHits.isEmpty()
}

enum class SearchFilter { ALL, TITLE, LYRICS, NUMBER }
