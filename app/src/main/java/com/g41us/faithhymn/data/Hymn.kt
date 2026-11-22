package com.g41us.faithhymn.data

data class Hymn(val number: Int, val title: String, val content: String, val firstLine: String) {
    /** Verses/chorus blocks, split on blank lines. Used by presentation mode. */
    val blocks: List<String> get() = content.split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }
}

data class HymnSummary(val number: Int, val title: String, val firstLine: String, val snippet: String? = null)

data class Playlist(val id: Long, val name: String, val count: Int)
