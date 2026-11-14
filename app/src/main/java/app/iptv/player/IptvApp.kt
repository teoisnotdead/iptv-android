package app.iptv.player

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.iptv.player.data.CatalogRepository
import app.iptv.player.data.Creds
import app.iptv.player.data.DiskCache
import app.iptv.player.data.FavoritesStore
import app.iptv.player.data.PANEL_USER_AGENT
import app.iptv.player.data.ProgressStore
import app.iptv.player.data.SessionStore
import app.iptv.player.data.TrackPrefs
import app.iptv.player.data.XtreamApi
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Objetos compartidos por toda la app (una instancia por proceso). */
class AppGraph(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Cliente HTTP único: API del panel, video e imágenes, siempre con el mismo agente. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", PANEL_USER_AGENT).build())
        }
        .build()

    val api = XtreamApi(http)
    val session = SessionStore(context)
    private val disk = DiskCache(context)
    val catalog = CatalogRepository(api, session, disk, scope)
    val favorites = FavoritesStore(context, session)
    val progress = ProgressStore(context, session)
    val trackPrefs = TrackPrefs(context)

    /** Aviso para la pantalla de login cuando la sesión se cerró sola (cuenta vencida, etc.). */
    var notice by mutableStateOf<String?>(null)

    /** Sube al pedir "Actualizar catálogo": las pantallas lo usan de clave para volver a cargar. */
    var catalogVersion by mutableIntStateOf(0)
        private set

    /** Descarta la copia guardada del catálogo y lo vuelve a descargar del panel. */
    fun refreshCatalog() {
        catalog.forgetStored()
        catalogVersion++
    }

    // El catálogo en memoria y los favoritos son de la cuenta en uso: se renuevan cada vez que cambia.

    fun addAccount(creds: Creds) {
        catalog.clear()
        session.add(creds)
        favorites.reload()
        notice = null
    }

    fun switchTo(creds: Creds) {
        if (session.creds.value?.sameAccount(creds) == true) return
        catalog.clear()
        session.select(creds)
        favorites.reload()
    }

    /** Cierra la sesión de la cuenta en uso (la quita del dispositivo). */
    fun logout(reason: String? = null) {
        val active = session.creds.value ?: return
        catalog.forgetStored() // la cuenta se quita del dispositivo: su catálogo guardado también
        session.remove(active)
        favorites.reload()
        notice = reason
    }
}

class IptvApp : Application(), ImageLoaderFactory {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }

    /** Logos y pósters usan el mismo cliente (y agente) que el resto de la app. */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient(graph.http).crossfade(true).build()
}

val Context.graph: AppGraph get() = (applicationContext as IptvApp).graph
