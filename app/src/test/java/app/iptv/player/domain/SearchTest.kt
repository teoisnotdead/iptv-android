package app.iptv.player.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private fun index(vararg names: String) = SearchIndex(names.toList(), { it })

    @Test fun sinTildesNiSignos() {
        assertEquals("spider man lejos de casa 2019", fold("Spider-Man: Lejos de casa (2019)"))
        assertEquals(listOf("Spider-Man 2"), index("Batman", "Spider-Man 2").search("spider man").items)
        assertEquals(listOf("Película"), index("Película").search("PELICULA").items)
    }

    @Test fun todasLasPalabrasEnCualquierOrden() {
        val r = index("Dragon Ball Z", "Dragon Quest", "Ball Park").search("ball dragon")
        assertEquals(listOf("Dragon Ball Z"), r.items)
    }

    @Test fun primeroLoMasParecido() {
        val r = index("La Dernière Leçon", "Ingeniera Informática", "NieR:Automata Ver1.1a", "Película 120", "Película 12").search("nier")
        assertEquals("NieR:Automata Ver1.1a", r.items.first())
        assertEquals(3, r.total)
        // Palabra entera antes que comienzo de palabra.
        assertEquals(listOf("Película 12", "Película 120"), index("Película 120", "Película 12").search("pelicula 12").items)
    }

    @Test fun laCategoriaTambienEncuentraPeroValeMenos() {
        val items = listOf("Naruto" to "Universo Anime", "Anime Awards" to "Especiales")
        val r = SearchIndex(items, { it.first }, { it.second }).search("anime")
        assertEquals(listOf("Anime Awards", "Naruto"), r.items.map { it.first })
    }

    @Test fun limiteYVacio() {
        val r = index("a1", "a2", "a3").search("a", limit = 2)
        assertEquals(2, r.items.size)
        assertEquals(3, r.total)
        assertTrue(index("a1").search("  ").items.isEmpty())
    }
}
