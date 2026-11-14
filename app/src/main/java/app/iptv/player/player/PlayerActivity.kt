package app.iptv.player.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.C
import app.iptv.player.data.StreamKind
import app.iptv.player.ui.theme.IptvTheme

/** Un ítem de la cola de reproducción (película, o episodios de una serie en orden). */
data class QueueItem(val id: String, val ext: String, val title: String)

/**
 * Reproductor a pantalla completa para películas y episodios. En series recibe la cola de
 * episodios: al terminar uno pasa solo al siguiente, y la barra superior tiene "Siguiente".
 * Si algo no se puede reproducir, vuelve a la ficha con un aviso del motivo.
 * La reproducción se suelta al salir de pantalla para no dejar ocupada una conexión de la cuenta.
 */
class PlayerActivity : ComponentActivity() {
    private var kind = StreamKind.MOVIE
    private var queue: List<QueueItem> = emptyList()
    private var index by mutableIntStateOf(0)
    private var session by mutableStateOf<PlaybackSession?>(null)
    private var inPip by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        kind = StreamKind.entries.firstOrNull { it.name == intent.getStringExtra(EXTRA_KIND) } ?: StreamKind.MOVIE
        val ids = intent.getStringArrayListExtra(EXTRA_IDS).orEmpty()
        val exts = intent.getStringArrayListExtra(EXTRA_EXTS).orEmpty()
        val titles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        queue = ids.indices.map { QueueItem(ids[it], exts.getOrNull(it).orEmpty(), titles.getOrNull(it).orEmpty()) }
        if (queue.isEmpty()) {
            finish()
            return
        }
        index = (savedInstanceState?.getInt(STATE_INDEX) ?: intent.getIntExtra(EXTRA_INDEX, 0)).coerceIn(0, queue.lastIndex)

        // Al recrearse se retoma la posición exacta; "Desde el inicio" solo vale para la primera vez.
        val resumeAt = when {
            savedInstanceState != null -> savedInstanceState.getLong(STATE_POSITION, C.TIME_UNSET)
            intent.getBooleanExtra(EXTRA_FROM_START, false) -> 0L
            else -> C.TIME_UNSET
        }
        session = newSession(index, resumeAt).also { it.useTs = intent.getBooleanExtra(EXTRA_TS, false) }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            IptvTheme {
                val current = session ?: return@IptvTheme
                // Si falla, se vuelve solo a la ficha (con el motivo en un aviso) para seguir navegando,
                // en vez de dejar al usuario frente a una pantalla negra de error.
                LaunchedEffect(current.error) {
                    val message = current.error ?: return@LaunchedEffect
                    Toast.makeText(applicationContext, "No se pudo reproducir. $message", Toast.LENGTH_LONG).show()
                    finish()
                }
                VideoSurface(
                    session = current,
                    controls = !inPip,
                    title = queue[index].title,
                    onBack = { finish() },
                    onNext = if (index < queue.lastIndex) ({ playNext() }) else null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun newSession(i: Int, resumeAt: Long): PlaybackSession {
        val item = queue[i]
        return PlaybackSession(this, kind, item.id, item.ext, item.title).also {
            it.resumeAt = resumeAt
            it.onEnded = { playNext() }
        }
    }

    /** Pasa al siguiente de la cola, o cierra si era el último. */
    private fun playNext() {
        if (index >= queue.lastIndex) {
            finish()
            return
        }
        session?.close()
        index++
        // El siguiente episodio arranca desde el inicio aunque se haya visto a medias antes.
        val next = newSession(index, 0L)
        session = next
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) next.start()
        Toast.makeText(this, "Siguiente: ${queue[index].title}", Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        session?.start()
    }

    override fun onStop() {
        super.onStop()
        session?.release()
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.close()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_INDEX, index)
        session?.let { s -> outState.putLong(STATE_POSITION, s.player?.currentPosition ?: s.resumeAt) }
    }

    /* ---------- Picture-in-picture ---------- */

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && session?.player?.isPlaying == true) {
            runCatching {
                enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    companion object {
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_IDS = "ids"
        private const val EXTRA_EXTS = "exts"
        private const val EXTRA_TITLES = "titles"
        private const val EXTRA_INDEX = "index"
        private const val EXTRA_FROM_START = "desde_inicio"
        private const val EXTRA_TS = "ts"
        private const val STATE_POSITION = "position"
        private const val STATE_INDEX = "index"

        /**
         * Reproduce `queue[index]` y sigue con los siguientes. `ext` de cada ítem es su `container_extension`.
         * `fromStart`: ignora el avance guardado y empieza desde el principio.
         */
        fun intent(context: Context, kind: StreamKind, queue: List<QueueItem>, index: Int = 0, fromStart: Boolean = false): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_KIND, kind.name)
                .putStringArrayListExtra(EXTRA_IDS, ArrayList(queue.map { it.id }))
                .putStringArrayListExtra(EXTRA_EXTS, ArrayList(queue.map { it.ext }))
                .putStringArrayListExtra(EXTRA_TITLES, ArrayList(queue.map { it.title }))
                .putExtra(EXTRA_INDEX, index)
                .putExtra(EXTRA_FROM_START, fromStart)

        /** Atajo para un solo ítem. */
        fun intent(context: Context, kind: StreamKind, id: String, ext: String, title: String, fromStart: Boolean = false): Intent =
            intent(context, kind, listOf(QueueItem(id, ext, title)), 0, fromStart)
    }
}
