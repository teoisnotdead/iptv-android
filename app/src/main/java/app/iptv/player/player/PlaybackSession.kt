package app.iptv.player.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.iptv.player.data.StreamKind
import app.iptv.player.data.TrackPrefs
import app.iptv.player.graph

/**
 * Una reproducción (Media3/ExoPlayer) de un canal, película o episodio. La comparten el
 * reproductor a pantalla completa y el previsualizador de TV en vivo.
 * - Usa los decodificadores del dispositivo y, si falta alguno de audio (AC-3, E-AC-3, DTS…), FFmpeg por software.
 * - En vivo: intenta HLS y, si falla, cae a MPEG-TS; ante cortes reintenta solo.
 * - Película/episodio: retoma donde quedó y va guardando el avance. Si el servidor se corta o se queda
 *   sin mandar datos, reintenta solo desde donde iba (hasta 3 veces seguidas) antes de avisar.
 * - Aplica el idioma de audio y subtítulos que el usuario eligió la última vez.
 * `release()` suelta la conexión con el panel; hay que llamarlo siempre al salir de pantalla.
 */
@OptIn(UnstableApi::class)
class PlaybackSession(
    context: Context,
    val kind: StreamKind,
    val streamId: String,
    /** `container_extension` de la película o episodio; se ignora en vivo. */
    val ext: String,
    val title: String,
) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val isLive get() = kind == StreamKind.LIVE

    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    /** Mensaje apto para mostrar; nunca incluye la URL del stream (lleva las credenciales). */
    var error by mutableStateOf<String?>(null)
        private set

    /** Pistas de audio y subtítulos del contenido actual (para el selector). */
    var tracks by mutableStateOf(Tracks.EMPTY)
        private set

    /** En vivo: true cuando HLS no funcionó y se usa MPEG-TS. */
    var useTs by mutableStateOf(false)

    /** Posición a retomar en películas y episodios. Sin definir = la guardada (o el inicio). */
    var resumeAt = C.TIME_UNSET

    /** Película o episodio que llegó al final (para pasar al siguiente). */
    var onEnded: (() -> Unit)? = null

    private var retries = 0
    private var closed = false

    fun start() {
        if (player != null || closed) return
        val graph = context.graph
        val creds = graph.session.creds.value ?: run {
            error = "Sesión vencida"
            return
        }
        error = null

        if (!isLive && resumeAt == C.TIME_UNSET) {
            resumeAt = graph.progress.get(kind, streamId)?.takeUnless { it.finished }?.positionMs ?: C.TIME_UNSET
        }

        val url = graph.api.streamUrl(creds, kind, streamId, if (isLive) (if (useTs) "ts" else "m3u8") else ext.ifEmpty { "mp4" })
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).build())
            .apply { if (isLive && !useTs) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()

        // EXTENSION_RENDERER_MODE_ON: primero el decodificador del dispositivo; FFmpeg solo si no hay.
        val renderers = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)
        val sources = DefaultMediaSourceFactory(OkHttpDataSource.Factory(graph.videoHttp))

        val exo = ExoPlayer.Builder(context, renderers).setMediaSourceFactory(sources).build()
        val prefs = graph.trackPrefs
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*listOfNotNull(prefs.audio, "es", "spa").distinct().toTypedArray())
            .apply {
                when (val text = prefs.text) {
                    null -> Unit
                    TrackPrefs.TEXT_OFF -> setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    else -> setPreferredTextLanguages(text)
                }
            }
            .build()
        exo.addListener(listener)
        exo.setMediaItem(item)
        if (!isLive && resumeAt != C.TIME_UNSET) exo.seekTo(resumeAt)
        exo.playWhenReady = true
        exo.prepare()
        player = exo
        if (!isLive) handler.postDelayed(saveTick, SAVE_EVERY_MS)
    }

    /** Suelta el reproductor (y la conexión). Se puede volver a llamar a `start()`. */
    fun release() {
        handler.removeCallbacksAndMessages(null)
        player?.let {
            if (!isLive && it.currentPosition > 0) {
                resumeAt = it.currentPosition
                saveProgress(it)
            }
            it.removeListener(listener)
            it.release()
        }
        player = null
        tracks = Tracks.EMPTY
    }

    /** Suelta todo de forma definitiva: ya no vuelve a arrancar aunque quede un reintento pendiente. */
    fun close() {
        closed = true
        release()
    }

    fun retry() {
        retries = 0
        release()
        start()
    }

    private fun saveProgress(p: Player) {
        val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        context.graph.progress.save(kind, streamId, p.currentPosition, duration)
    }

    /** Guarda el avance cada tanto, por si la app se cierra de golpe. */
    private val saveTick = object : Runnable {
        override fun run() {
            player?.let { if (it.isPlaying) saveProgress(it) }
            handler.postDelayed(this, SAVE_EVERY_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(value: Tracks) {
            tracks = value
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) retries = 0
            if (state != Player.STATE_ENDED) return
            if (isLive) {
                // Una señal en vivo que "termina" es un corte del servidor: se vuelve a pedir.
                retryOrFail("El servidor cortó la señal")
            } else {
                player?.let { saveProgress(it) }
                onEnded?.invoke()
            }
        }

        override fun onPlayerError(e: PlaybackException) {
            val detail = e.errorCodeName.removePrefix("ERROR_CODE_")
            val status = httpStatus(e)
            when {
                // Rechazo definitivo del servidor (403, 404…) también en MPEG-TS o en una película: se informa de inmediato.
                status != null && status in 400..499 && (!isLive || useTs) -> {
                    release()
                    error = unavailable(status)
                }
                isLive && e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> player?.run {
                    seekToDefaultPosition()
                    prepare()
                }
                isLive && !useTs -> {
                    useTs = true // HLS no funcionó: mismo canal en MPEG-TS
                    retries = 0
                    release()
                    start()
                }
                isLive -> retryOrFail(detail)
                // Película o episodio con un servidor lento o que cortó: se retoma solo desde donde iba.
                isNetwork(e) && retries < MAX_VOD_RETRIES -> {
                    retries++
                    release() // deja la posición en `resumeAt`
                    handler.postDelayed({ start() }, 1500L * retries)
                }
                else -> {
                    release()
                    error = describe(e, detail)
                }
            }
        }
    }

    /** Falla de red (sin conexión, corte o el servidor dejó de mandar datos): reintentar puede servir. */
    private fun isNetwork(e: PlaybackException): Boolean =
        e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            e.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED

    /** Código HTTP con que el servidor rechazó el stream, si ese fue el motivo. */
    private fun httpStatus(e: PlaybackException): Int? =
        generateSequence(e.cause) { it.cause }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

    /** El servidor respondió, pero se niega a entregar este contenido: reintentar no sirve. */
    private fun unavailable(status: Int): String = when (status) {
        403 -> "El servidor no tiene disponible este contenido ahora, o la cuenta alcanzó su límite de conexiones. (403)"
        404 -> "Este contenido ya no existe en el servidor. (404)"
        else -> "El servidor rechazó la reproducción. ($status)"
    }

    private fun retryOrFail(detail: String) {
        release()
        if (retries >= MAX_LIVE_RETRIES) {
            error = "El servidor no respondió o la cuenta alcanzó su límite de conexiones. ($detail)"
            return
        }
        retries++
        handler.postDelayed({ start() }, 1500L * retries)
    }

    private fun describe(e: PlaybackException, detail: String): String = when (e.errorCode) {
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        -> "Este dispositivo no puede decodificar el video. ($detail)"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        -> "El formato .$ext no es compatible o el archivo está dañado. ($detail)"
        else -> "El servidor no respondió. ($detail)"
    }

    private companion object {
        const val MAX_LIVE_RETRIES = 3
        const val MAX_VOD_RETRIES = 3
        const val SAVE_EVERY_MS = 15_000L
    }
}
