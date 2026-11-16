package app.iptv.player.domain

/**
 * Búsqueda por nombre en las listas del catálogo (decenas de miles de títulos). Mismas reglas que la
 * app del TV (`lib/search.ts` en iptv-webos):
 * - Sin tildes ni mayúsculas, y los signos cuentan como espacio: "spider man" encuentra "Spider-Man".
 * - Tienen que aparecer todas las palabras, en cualquier orden.
 * - Orden: cada palabra suma según cómo aparece en el nombre (palabra entera 0, comienzo de palabra 1,
 *   dentro de otra 2; solo en el texto extra, como la categoría, 3), y se gana medio punto si el
 *   nombre empieza con lo buscado. A igual puntaje, el nombre más corto y luego el orden del catálogo.
 */

private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

/** Normalizado para buscar: sin tildes, en minúsculas y con los signos convertidos en un espacio. */
fun fold(s: String): String = SEPARATORS.replace(normalize(s), " ").trim()

/** `best`: puntaje del mejor resultado (menor es mejor), para ordenar grupos entre sí. */
class SearchResult<T>(val items: List<T>, val total: Int, val best: Double)

/**
 * Índice de una lista: los nombres se normalizan una sola vez, no en cada letra que se escribe.
 * `extra` es texto que también cuenta para encontrar (p. ej. el nombre de la categoría), pero vale menos.
 */
class SearchIndex<T>(private val items: List<T>, name: (T) -> String, extra: ((T) -> String)? = null) {
    // Con un espacio adelante: "empieza con" y "palabra que empieza con" son un `indexOf`.
    private val names = items.map { " " + fold(name(it)) }
    private val extras = extra?.let { f -> items.map { " " + fold(f(it)) } }

    fun search(query: String, limit: Int = Int.MAX_VALUE): SearchResult<T> {
        val q = fold(query)
        if (q.isEmpty()) return SearchResult(emptyList(), 0, Double.POSITIVE_INFINITY)
        val words = q.split(' ')
        val hits = ArrayList<Hit>()
        for (i in names.indices) {
            val n = names[i]
            var score = 0.0
            for (w in words) {
                score += when {
                    n.contains(" $w ") || n.endsWith(" $w") -> 0.0
                    n.contains(" $w") -> 1.0
                    n.contains(w) -> 2.0
                    extras != null && extras[i].contains(w) -> 3.0
                    else -> {
                        score = -1.0
                        break
                    }
                }
            }
            if (score < 0) continue
            hits += Hit(i, if (n.startsWith(" $q")) score else score + 0.5, n.length)
        }
        hits.sortWith(compareBy<Hit> { it.score }.thenBy { it.length }.thenBy { it.index })
        return SearchResult(hits.take(limit).map { items[it.index] }, hits.size, hits.firstOrNull()?.score ?: Double.POSITIVE_INFINITY)
    }

    private class Hit(val index: Int, val score: Double, val length: Int)
}
