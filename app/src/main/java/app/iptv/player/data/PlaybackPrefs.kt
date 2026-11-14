package app.iptv.player.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Avance de una película o episodio. */
data class Progress(val positionMs: Long, val durationMs: Long, val finished: Boolean) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Dónde quedó cada película y episodio, por cuenta, para "Continuar" y marcar lo visto.
 * Vive solo en el dispositivo.
 */
class ProgressStore(context: Context, private val session: SessionStore) {
    private val prefs = context.getSharedPreferences("progreso", Context.MODE_PRIVATE)
    private val changes = MutableStateFlow(0)

    /** Cambia cada vez que se guarda un avance: las pantallas lo observan para refrescarse. */
    val version: StateFlow<Int> = changes

    private fun key(kind: StreamKind, id: String): String? =
        session.creds.value?.let { "${it.url.lowercase()}|${it.username}|${kind.path}|$id" }

    fun get(kind: StreamKind, id: String): Progress? {
        val raw = key(kind, id)?.let { prefs.getString(it, null) } ?: return null
        val parts = raw.split(',')
        val position = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return Progress(position, parts.getOrNull(1)?.toLongOrNull() ?: 0L, parts.getOrNull(2) == "1")
    }

    /**
     * Guarda el avance. Menos de [MIN_MS] no cuenta como empezado; pasado el 95 % (o a menos de
     * [END_MARGIN_MS] del final) se marca como visto y la próxima vez arranca desde el inicio.
     */
    fun save(kind: StreamKind, id: String, positionMs: Long, durationMs: Long) {
        val key = key(kind, id) ?: return
        val finished = durationMs > 0 && (positionMs >= durationMs * 0.95 || durationMs - positionMs < END_MARGIN_MS)
        val previous = get(kind, id)
        when {
            finished -> prefs.edit().putString(key, "0,$durationMs,1").apply()
            positionMs < MIN_MS && previous?.finished != true -> prefs.edit().remove(key).apply()
            positionMs < MIN_MS -> return // volver a abrir algo visto y salir enseguida no borra la marca
            else -> prefs.edit().putString(key, "$positionMs,$durationMs,0").apply()
        }
        changes.value++
    }

    private companion object {
        const val MIN_MS = 15_000L
        const val END_MARGIN_MS = 90_000L
    }
}

/**
 * Idioma de audio y subtítulos que eligió el usuario la última vez; se aplica al abrir cualquier
 * video. `text` = [TEXT_OFF] si desactivó los subtítulos, null si nunca eligió.
 */
class TrackPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("pistas", Context.MODE_PRIVATE)

    var audio: String?
        get() = prefs.getString("audio", null)
        set(value) = prefs.edit().putString("audio", value).apply()

    var text: String?
        get() = prefs.getString("subtitulos", null)
        set(value) = prefs.edit().putString("subtitulos", value).apply()

    /** TV en vivo: arrancar directo en MPEG-TS en vez de HLS (lo elige el usuario en la ficha del canal). */
    var liveTs: Boolean
        get() = prefs.getBoolean("vivo_ts", false)
        set(value) = prefs.edit().putBoolean("vivo_ts", value).apply()

    companion object {
        const val TEXT_OFF = "off"
    }
}
