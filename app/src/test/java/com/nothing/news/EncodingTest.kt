package com.nothing.news

import org.junit.Test
import org.junit.Assert.*
import java.nio.charset.Charset

class EncodingTest {

    private fun repairEncoding(text: String): String {
        if (!text.contains("\u00C3") && !text.contains("\u00C2")) return text
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '\u00C3' || c == '\u00C2') && i + 1 < text.length) {
                val next = text[i + 1].toInt()
                if (next in 0x80..0xBF || next in 0xA0..0xBF) {
                    val base = if (c == '\u00C3') 0xC0 else 0x80
                    val repairedChar = ((next and 0x3F) or base).toChar()
                    out.append(repairedChar)
                    i += 2
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    @Test
    fun testHardwareUpgradeCorruption() {
        // Simula byte UTF-8 di "fornirà" e "è polemica"
        val forniraBytes = byteArrayOf(0x66, 0x6f, 0x72, 0x6e, 0x69, 0x72, 0xc3.toByte(), 0xa0.toByte())
        val polemicaBytes = byteArrayOf(0xc3.toByte(), 0xa8.toByte(), 0x20, 0x70, 0x6f, 0x6c, 0x65, 0x6d, 0x69, 0x63, 0x61)

        // Decodifica ERRORE (ISO-8859-1)
        val corruptedFornira = String(forniraBytes, Charset.forName("ISO-8859-1"))
        val corruptedPolemica = String(polemicaBytes, Charset.forName("ISO-8859-1"))

        // Verifica che siano corrotti (contengono Ã)
        assertTrue(corruptedFornira.contains("Ã"))
        assertTrue(corruptedPolemica.contains("Ã"))

        // Riparazione
        val repairedFornira = repairEncoding(corruptedFornira)
        val repairedPolemica = repairEncoding(corruptedPolemica)

        // Risultato atteso
        assertEquals("fornirà", repairedFornira)
        assertEquals("è polemica", repairedPolemica)
        
        println("Test superato: $repairedFornira, $repairedPolemica")
    }
}
