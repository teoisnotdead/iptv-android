package app.iptv.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test fun base64() {
        assertEquals("Noticias de la mañana", b64("Tm90aWNpYXMgZGUgbGEgbWHDsWFuYQ=="))
        assertEquals("", b64(null))
        assertEquals("no es base64 !!", b64("no es base64 !!"))
    }

    @Test fun tituloDeGuia() {
        assertEquals("", epgTitle("U2luIGluZm9ybWFjacOzbg==")) // "Sin información"
        assertEquals("", epgTitle(null))
        assertEquals("Noticias de la mañana", epgTitle("Tm90aWNpYXMgZGUgbGEgbWHDsWFuYQ=="))
    }

    @Test fun normaliza() {
        assertEquals("accion y pasion", normalize("Acción y Pasión"))
    }

    @Test fun rating() {
        assertEquals("7.5", formatRating("7.46"))
        assertEquals("8.0", formatRating("8"))
        assertNull(formatRating("0"))
        assertNull(formatRating(""))
        assertNull(formatRating("N/A"))
    }

    @Test fun duracion() {
        assertEquals("1 h 52 min", formatDuration("01:52:00"))
        assertEquals("1 h 52 min", formatDuration("112"))
        assertEquals("45 min", formatDuration("00:45"))
        assertEquals("2 h", formatDuration("120"))
        assertEquals("1 h 05 min", formatDuration("65"))
        assertEquals("1h 52m", formatDuration("1h 52m"))
        assertNull(formatDuration("0"))
        assertNull(formatDuration(""))
    }

    @Test fun iniciales() {
        assertEquals("CN", initials("CL | Canal Nacional HD"))
        assertEquals("ES", initials("ESPN"))
        assertEquals("HM", initials("US | HBO Max | 4K"))
        assertEquals("TV", initials("TVN"))
    }

    @Test fun diasRestantes() {
        val now = 1_000_000_000_000L
        assertEquals(1, daysLeft(((now + 3_600_000) / 1000).toString(), now))
        assertEquals(-1, daysLeft(((now - 86_400_000 - 3_600_000) / 1000).toString(), now))
        assertNull(daysLeft("", now))
        assertNull(daysLeft(null, now))
    }

    @Test fun reloj() {
        assertEquals("0:00", clock(0))
        assertEquals("45:10", clock(2_710_000))
        assertEquals("1:02:13", clock(3_733_000))
        assertEquals("2:52:36", clock(10_356_400))
    }

    @Test fun idiomas() {
        assertEquals("Español", languageName("spa"))
        assertEquals("Español", languageName("es"))
        assertEquals("Inglés", languageName("eng"))
        assertEquals("Inglés", languageName("en-US"))
        assertNull(languageName("und"))
        assertNull(languageName(null))
        assertNull(languageName("zzz"))
    }

    @Test fun listas() {
        assertEquals(listOf("Acción", "Drama", "Comedia"), splitList("Acción, Drama / Comedia"))
        assertEquals("https://www.youtube.com/watch?v=abc", youtubeUrl("abc"))
        assertEquals("https://youtu.be/abc", youtubeUrl("https://youtu.be/abc"))
        assertNull(youtubeUrl(""))
    }
}
