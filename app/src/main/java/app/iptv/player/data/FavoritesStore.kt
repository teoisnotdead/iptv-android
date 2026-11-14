package app.iptv.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class FavKind { LIVE, MOVIE, SERIES }

/**
 * Favoritos de la cuenta en uso, guardados en el dispositivo (nunca llegan al servidor).
 * Una lista por tipo y por cuenta, con el más nuevo primero.
 */
class FavoritesStore(context: Context, private val session: SessionStore) {
    private val prefs = context.getSharedPreferences("favoritos", Context.MODE_PRIVATE)
    private val state = MutableStateFlow<Map<FavKind, List<Int>>>(emptyMap())

    val all: StateFlow<Map<FavKind, List<Int>>> = state

    init {
        reload()
    }

    /** Vuelve a leer las listas; se llama cada vez que cambia la cuenta en uso. */
    fun reload() {
        state.value = FavKind.entries.associateWith { read(it) }
    }

    fun toggle(kind: FavKind, id: Int) {
        val key = key(kind) ?: return
        val current = state.value[kind].orEmpty()
        val next = if (id in current) current - id else listOf(id) + current
        prefs.edit().putString(key, next.joinToString(",")).apply()
        state.value = state.value + (kind to next)
    }

    private fun key(kind: FavKind): String? =
        session.creds.value?.let { "iptv:favoritos:${kind.name.lowercase()}:${it.url.lowercase()}:${it.username}" }

    private fun read(kind: FavKind): List<Int> {
        val key = key(kind) ?: return emptyList()
        return prefs.getString(key, "").orEmpty().split(',').mapNotNull { it.toIntOrNull() }
    }
}
