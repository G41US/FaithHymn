package com.g41us.faithhymn.data

/**
 * Turns raw hymnal text into structured blocks, keeping every musical marking:
 *  - voice parts   "Sop:", "ATB:", "S/A:", "Gen:", "Bass solo:" …   -> Part (which voices sing)
 *  - dynamics      "p", "pp", "mf", "f", "ff", "rit.", "dim."        -> Dynamic badge
 *  - echo lines    "(by and by)"                                       -> echo segments
 *  - chorus extras "Chorus: $" (segno), "Chorus: 12 / 8" (time sig), "Chorus: spirited" (tempo)
 *  - verse numbers "2." (verse 1 is implied)
 */
enum class Voice { S, A, T, B }

data class Part(val label: String, val voices: Set<Voice>, val solo: Boolean) {
    val everyone get() = voices.size == 4
}

data class Segment(val text: String, val echo: Boolean = false, val dynamic: String? = null)

data class HymnLine(val part: Part?, val showPart: Boolean, val dynamic: String?, val segments: List<Segment>) {
    val plain: String get() = segments.joinToString("") { it.dynamic ?: it.text }
}

enum class BlockKind { VERSE, CHORUS }

data class HymnBlock(
    val kind: BlockKind,
    val number: Int?,          // verse number
    val lines: List<HymnLine>,
    val dynamic: String? = null,
    val tempo: String? = null,
    val timeSignature: String? = null,
    val segno: Boolean = false,
) {
    val plainText: String get() = lines.joinToString("\n") { it.plain }
}

object HymnFormat {
    val DYNAMICS = mapOf(
        "ppp" to "as softly as possible", "pp" to "very soft", "p" to "soft", "mp" to "moderately soft",
        "mf" to "moderately loud", "f" to "loud", "ff" to "very loud", "fff" to "as loud as possible",
        "rit." to "gradually slow down", "rall." to "gradually slow down", "dim." to "gradually get softer",
        "cresc." to "gradually get louder", "sf" to "sudden accent",
    )
    val TEMPOS = setOf("moderato", "andante", "allegro", "joyfully", "slowly", "chant", "brightly", "largo", "maestoso", "lento", "spirited", "with feeling", "prayerfully", "boldly")
    private val dynToken = Regex("^(ppp|pp|p|mp|mf|fff|ff|f|sf|rit\\.?|rall\\.?|dim\\.?|cresc\\.?)(?=\\s|$)")
    private val midDyn = Regex("(?<![\\p{L}’'])(pp|mp|mf|ff|rit\\.|dim\\.|cresc\\.)(?![\\p{L}])")
    private val verseNum = Regex("^(\\d{1,2})\\.\\s*")
    private val chorusHead = Regex("^(Full Chorus|Semi Chorus|Chorus|Refrain|Cho)\\s*:?\\s*(.*)$", RegexOption.IGNORE_CASE)

    private val voiceWords = listOf(
        "soprano" to Voice.S, "sop" to Voice.S, "s" to Voice.S, "alto" to Voice.A, "a" to Voice.A,
        "tenor" to Voice.T, "ten" to Voice.T, "t" to Voice.T, "bass" to Voice.B, "b" to Voice.B,
    ).toMap()
    private val allWords = setOf("all", "gen", "general", "unison", "parts", "harmony", "others", "quartet", "duet", "full chorus", "semi chorus", "tutti")

    /** Parses "Sop. solo", "S/A", "ATB", "Ten/Bass", "Sop & Alto", "Gen" … Returns null if it isn't a part label. */
    fun parsePart(raw: String): Part? {
        val label = raw.trim().trimEnd(':').trim()
        if (label.isEmpty() || label.length > 18) return null
        val l = label.lowercase().replace(".", "").trim()
        if (l in allWords) return Part(label, Voice.entries.toSet(), solo = false)
        val solo = l.endsWith(" solo") || l == "solo"
        val core = l.removeSuffix(" solo").removeSuffix("solo").trim()
        if (core.isEmpty()) return Part(label, Voice.entries.toSet(), true)
        val words = core.split(Regex("\\s*(/|&|,|\\bor\\b|\\band\\b|\\s)\\s*")).filter { it.isNotBlank() }
        val voices = mutableSetOf<Voice>()
        for (w in words) {
            val v = voiceWords[w]
            if (v != null) { voices += v; continue }
            // Compact forms: "atb", "sat", "tb", "sa"
            if (w.length in 2..4 && w.all { it in "satb" }) { w.forEach { ch -> voices += voiceWords[ch.toString()]!! }; continue }
            return null
        }
        return if (voices.isEmpty()) null else Part(label, voices, solo)
    }

    fun parse(content: String): List<HymnBlock> {
        val raw = content.split(Regex("\n\\s*\n")).map { it.trim('\n') }.filter { it.isNotBlank() }
        val out = mutableListOf<HymnBlock>()
        var pendingChorus: HymnBlock? = null   // "Chorus:" header standing alone in its own block
        var verseCount = 0
        for (text in raw) {
            var lines = text.lines().map { it.trimEnd() }.toMutableList()
            var kind = BlockKind.VERSE; var number: Int? = null
            var dynamic: String? = null; var tempo: String? = null; var time: String? = null; var segno = false
            var firstPart: Part? = null

            val head = chorusHead.find(lines.first().trim())
            if (head != null) {
                kind = BlockKind.CHORUS
                var rest = head.groupValues[2].trim()
                lines.removeAt(0)
                // time signature split over two lines: "Chorus: 12" / "8"
                if (rest.matches(Regex("\\d{1,2}")) && lines.firstOrNull()?.trim()?.matches(Regex("\\d{1,2}")) == true) {
                    time = "$rest/${lines.removeAt(0).trim()}"; rest = ""
                }
                Regex("^(\\d{1,2})\\s*/\\s*(\\d{1,2})$").find(rest)?.let { time = "${it.groupValues[1]}/${it.groupValues[2]}"; rest = "" }
                if (rest.startsWith("$")) { segno = true; rest = rest.drop(1).trim() }
                dynToken.find(rest)?.let { m -> if (rest.length == m.value.length) { dynamic = norm(m.value); rest = "" } }
                if (rest.isNotEmpty()) {
                    val asPart = parsePart(rest)
                    when {
                        asPart != null -> { firstPart = asPart; rest = "" }
                        rest.split(" ").size <= 2 && rest.first().isLowerCase() && !rest.endsWith(",") -> { tempo = rest; rest = "" }
                    }
                }
                if (rest.isNotEmpty()) lines.add(0, rest)
            } else {
                verseNum.find(lines.first().trim())?.let { m ->
                    number = m.groupValues[1].toInt(); lines[0] = lines[0].trim().substring(m.value.length)
                }
                // A lone tempo word ("Moderato") or part name ("Sop.") above the verse
                val head0 = lines.first().trim().trimEnd('.')
                if (lines.size > 1 && head0.lowercase() in TEMPOS) { tempo = head0; lines.removeAt(0) }
                else if (lines.size > 1) parsePart(head0)?.let { p -> firstPart = p; lines.removeAt(0) }
                // "2. Chorus" style is rare; a block after a lone chorus header belongs to that chorus.
            }
            // stray lone numbers (layout leftovers from the printed book) -> time signature if paired
            lines = lines.filterIndexed { i, l -> !(l.trim().matches(Regex("\\d{1,2}")) && (i == 0 || i == lines.lastIndex)) }.toMutableList()

            if (kind == BlockKind.CHORUS && lines.all { it.isBlank() }) {
                pendingChorus = HymnBlock(BlockKind.CHORUS, null, emptyList(), dynamic, tempo, time, segno)
                    .also { if (firstPart != null) pendingPartFor = firstPart }
                continue
            }
            val pc = pendingChorus
            if (pc != null && kind == BlockKind.VERSE && number == null) {
                kind = BlockKind.CHORUS; dynamic = pc.dynamic; tempo = pc.tempo; time = pc.timeSignature; segno = pc.segno
                if (firstPart == null) firstPart = pendingPartFor
            }
            pendingChorus = null; pendingPartFor = null

            if (kind == BlockKind.VERSE) {
                if (number == null) number = if (verseCount == 0) 1 else null
                if (number != null) verseCount = maxOf(verseCount, number!!)
            }
            out += HymnBlock(kind, number, parseLines(lines.filter { it.isNotBlank() }, firstPart), dynamic, tempo, time, segno)
        }
        return out
    }

    private var pendingPartFor: Part? = null

    private fun parseLines(lines: List<String>, startPart: Part?): List<HymnLine> {
        var current: Part? = startPart
        var announce = startPart != null
        return lines.map { l0 ->
            var l = l0.trim()
            // Part label (keeps applying to following lines until the next label)
            Regex("^([A-Za-z][A-Za-z./&, ]{0,17}?)\\s*:\\s*").find(l)?.let { m ->
                parsePart(m.groupValues[1])?.let { p -> current = p; announce = true; l = l.substring(m.value.length) }
            }
            var dyn: String? = null
            // Part label may itself carry a dynamic: "S/A p"
            dynToken.find(l)?.let { m -> if (l.length > m.value.length) { dyn = norm(m.value); l = l.substring(m.value.length).trimStart() } }
            val line = HymnLine(current, announce, dyn, segments(l))
            announce = false
            line
        }
    }

    private fun segments(l: String): List<Segment> {
        val out = mutableListOf<Segment>()
        var i = 0
        val parts = Regex("\\([^)]*\\)").findAll(l).toList()
        fun plain(s: String) {
            var j = 0
            midDyn.findAll(s).forEach { m ->
                if (m.range.first > j) out += Segment(s.substring(j, m.range.first))
                out += Segment("", dynamic = norm(m.value)); j = m.range.last + 1
            }
            if (j < s.length) out += Segment(s.substring(j))
        }
        for (m in parts) {
            if (m.range.first > i) plain(l.substring(i, m.range.first))
            out += Segment(m.value, echo = true); i = m.range.last + 1
        }
        if (i < l.length) plain(l.substring(i))
        return out
    }

    private fun norm(d: String) = when (d) { "rit" -> "rit."; "dim" -> "dim."; "rall" -> "rall."; "cresc" -> "cresc."; else -> d }

    /** "ALL hail" -> "All hail" (hymnals print the first word in capitals; we show a drop cap instead). */
    fun softenFirstWord(s: String): String {
        val m = Regex("^([“\"']?)([\\p{Lu}’']{2,})(\\b)").find(s) ?: return s
        val w = m.groupValues[2]
        return s.replaceRange(m.range, m.groupValues[1] + w.first() + w.drop(1).lowercase())
    }
}
