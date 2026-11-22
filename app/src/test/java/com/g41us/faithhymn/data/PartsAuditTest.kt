package com.g41us.faithhymn.data

import org.junit.Test
import java.io.File
import java.sql.DriverManager

class PartsAuditTest {
    @Test fun audit() {
        val db = File("src/main/assets/hymns.db").absolutePath
        var vParts = 0; var cParts = 0; var hymnsV = 0; var hymnsC = 0
        val missed = mutableMapOf<String, Int>()
        DriverManager.getConnection("jdbc:sqlite:$db").use { con ->
            val rs = con.createStatement().executeQuery("SELECT number, content FROM hymns")
            while (rs.next()) {
                val b = HymnFormat.parse(rs.getString(2))
                val v = b.filter { it.kind == BlockKind.VERSE }.sumOf { x -> x.lines.count { it.part != null } }
                val c = b.filter { it.kind == BlockKind.CHORUS }.sumOf { x -> x.lines.count { it.part != null } }
                vParts += v; cParts += c; if (v > 0) { hymnsV++; if (hymnsV <= 12) print("V${rs.getInt(1)} ") }; if (c > 0) hymnsC++
                // lines that still start with "Something:" after parsing = possible missed labels
                b.flatMap { it.lines }.filter { it.part == null || !it.showPart }.forEach { l ->
                    Regex("^([A-Za-z][A-Za-z./&, ]{0,17}):").find(l.plain.trim())?.let { m -> missed[m.groupValues[1]] = (missed[m.groupValues[1]] ?: 0) + 1 }
                }
            }
        }
        println("AUDIT verseLines=$vParts chorusLines=$cParts hymnsWithVerseParts=$hymnsV hymnsWithChorusParts=$hymnsC")
        println("AUDIT missed=" + missed.entries.sortedByDescending { it.value }.take(40))
    }
}
