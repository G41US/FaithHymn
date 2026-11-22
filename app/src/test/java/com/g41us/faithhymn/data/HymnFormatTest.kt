package com.g41us.faithhymn.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.sql.DriverManager

class HymnFormatTest {
    @Test fun parts() {
        assertEquals(setOf(Voice.A, Voice.T, Voice.B), HymnFormat.parsePart("ATB")!!.voices)
        assertEquals(setOf(Voice.S, Voice.A), HymnFormat.parsePart("S/A")!!.voices)
        assertTrue(HymnFormat.parsePart("Sop. solo")!!.solo)
        assertEquals(setOf(Voice.T, Voice.B), HymnFormat.parsePart("Ten/Bass")!!.voices)
        assertTrue(HymnFormat.parsePart("Gen")!!.everyone)
        assertNull(HymnFormat.parsePart("JESUS knocks"))
        assertNull(HymnFormat.parsePart("Trust Him"))
    }

    @Test fun hymn1() {
        val c = "ALL hail to Thee, Immanuel,\nWe cast\n\nChorus:\n\nSop: Hail,...\nATB: Hail to the King we love so well,\nSop: Immanuel!\nATB: Hail, (Immanuel!)\n\n2. Second verse\nmf line two"
        val b = HymnFormat.parse(c)
        assertEquals(3, b.size)
        assertEquals(1, b[0].number)
        assertEquals(BlockKind.CHORUS, b[1].kind)
        assertEquals("Sop", b[1].lines[0].part!!.label)
        assertTrue(b[1].lines[3].segments.any { it.echo })
        assertEquals(2, b[2].number)
        assertEquals("mf", b[2].lines[1].dynamic)
    }

    @Test fun chorusExtras() {
        val b = HymnFormat.parse("x\n\nChorus: \$\nLet the sun\n\nChorus: 12\n8\nSA: Behold\n\nChorus: p\nJesus is here")
        assertTrue(b[1].segno); assertEquals("12/8", b[2].timeSignature); assertEquals("S", b[2].lines[0].part!!.voices.first().name)
        assertEquals("p", b[3].dynamic)
    }

    /** Whole book parses, nothing is lost. */
    @Test fun wholeBook() {
        val db = File("src/main/assets/hymns.db").absolutePath
        DriverManager.getConnection("jdbc:sqlite:$db").use { con ->
            val rs = con.createStatement().executeQuery("SELECT number, content FROM hymns")
            var n = 0; var parts = 0; var dyn = 0
            while (rs.next()) {
                val blocks = HymnFormat.parse(rs.getString(2))
                assertTrue("hymn ${rs.getInt(1)} empty", blocks.isNotEmpty() && blocks.all { it.lines.isNotEmpty() })
                parts += blocks.sumOf { b -> b.lines.count { it.showPart } }
                dyn += blocks.sumOf { b -> b.lines.count { it.dynamic != null } + b.lines.sumOf { l -> l.segments.count { it.dynamic != null } } } + blocks.count { it.dynamic != null }
                n++
            }
            println("hymns=$n partLabels=$parts dynamics=$dyn")
            assertEquals(1001, n)
        }
    }
}
