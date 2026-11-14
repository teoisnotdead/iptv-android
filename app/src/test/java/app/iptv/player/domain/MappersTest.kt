package app.iptv.player.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MappersTest {
    private val serie = "El señor de los anillos: Los anillos de poder (AMZN -DUAL)"

    @Test fun tituloDeEpisodioSinRepeticiones() {
        assertEquals("Sombra del pasado", cleanEpisodeTitle("$serie - S01E01 - Sombra del pasado", serie))
        assertEquals("Episodio 2", cleanEpisodeTitle("$serie - S01E02 - Episodio 2", serie))
        assertEquals("", cleanEpisodeTitle("$serie - S01E03", serie))
        assertEquals("Piloto", cleanEpisodeTitle("S1E1 · Piloto", serie))
        assertEquals("Un título cualquiera", cleanEpisodeTitle("Un título cualquiera", serie))
        // Sin tildes en el panel, igual reconoce el nombre de la serie.
        assertEquals("Final", cleanEpisodeTitle("El senor de los anillos: Los anillos de poder (AMZN -DUAL) - S03E04 - Final", serie))
    }
}
