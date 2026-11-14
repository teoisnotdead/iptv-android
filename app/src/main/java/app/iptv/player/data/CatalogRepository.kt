package app.iptv.player.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer

/**
 * Cargadores del catálogo con caché en memoria y en disco.
 * - Memoria: cada lista se pide una sola vez y la comparten todas las pantallas y el buscador.
 * - Disco: las listas grandes se guardan en el dispositivo. Al abrir la app se muestran al instante
 *   y, si la copia tiene más de [REFRESH_AFTER_MS], se actualizan en segundo plano para la próxima vez.
 * Si una carga falla, no queda cacheada. Se vacía al cambiar o cerrar la cuenta.
 */
class CatalogRepository(
    private val api: XtreamApi,
    private val session: SessionStore,
    private val disk: DiskCache,
    private val scope: CoroutineScope,
) {
    private val cache = HashMap<String, Deferred<Any>>()

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T : Any> memo(key: String, load: suspend (Creds) -> T): T {
        val creds = session.creds.value ?: throw PanelException("Sesión vencida")
        val job = synchronized(cache) { cache.getOrPut(key) { scope.async { load(creds) } } }
        try {
            return job.await() as T
        } catch (e: Throwable) {
            // Solo se descarta si falló la carga, no si se canceló quien la esperaba.
            if (job.isCompleted) synchronized(cache) { if (cache[key] === job) cache.remove(key) }
            throw e
        }
    }

    /** Lista guardada en disco: se usa la copia si existe y se renueva en segundo plano cuando envejece. */
    private suspend fun <T : Any> stored(key: String, item: KSerializer<T>, fetch: suspend (Creds) -> List<T>): List<T> {
        val serializer = ListSerializer(item)
        return memo(key) { creds ->
            val copy = withContext(Dispatchers.IO) { disk.read(creds, key, serializer) }
            if (copy != null) {
                if (System.currentTimeMillis() - copy.savedAt > REFRESH_AFTER_MS) refreshLater(creds, key, serializer, fetch)
                copy.value
            } else {
                fetch(creds).also { fresh -> withContext(Dispatchers.IO) { disk.write(creds, key, serializer, fresh) } }
            }
        }
    }

    private fun <T : Any> refreshLater(creds: Creds, key: String, serializer: KSerializer<List<T>>, fetch: suspend (Creds) -> List<T>) {
        scope.launch(Dispatchers.IO) {
            val fresh = runCatching { fetch(creds) }.getOrNull() ?: return@launch // sin conexión: se queda la copia
            disk.write(creds, key, serializer, fresh)
            // La lista nueva queda en memoria solo si la cuenta sigue siendo la misma.
            if (session.creds.value?.sameAccount(creds) == true) {
                synchronized(cache) { cache[key] = CompletableDeferred<Any>(fresh) }
            }
        }
    }

    fun clear() = synchronized(cache) {
        cache.values.forEach { it.cancel() }
        cache.clear()
    }

    /** Borra la copia en disco de la cuenta en uso: la próxima carga va al panel. */
    fun forgetStored() {
        val creds = session.creds.value ?: return
        clear()
        disk.clear(creds)
    }

    /** Fecha de la copia guardada de la cuenta en uso (0 si no hay). */
    fun storedAt(): Long = session.creds.value?.let { disk.savedAt(it) } ?: 0L

    // Las listas se deduplican por id: hay paneles que repiten ítems y las grillas exigen claves únicas.

    suspend fun liveCategories() = stored("live-cats", Category.serializer()) { api.liveCategories(it) }
    suspend fun liveStreams() = stored("live", LiveStream.serializer()) { c ->
        api.liveStreams(c).filter { it.streamId != 0 }.distinctBy { it.streamId }
    }

    suspend fun movieCategories() = stored("vod-cats", Category.serializer()) { api.vodCategories(it) }
    suspend fun movies() = stored("vod", VodStream.serializer()) { c ->
        api.vodStreams(c).filter { it.streamId != 0 }.distinctBy { it.streamId }
    }
    suspend fun vodInfo(id: Int) = memo("vod-info:$id") { api.vodInfo(it, id) }

    suspend fun seriesCategories() = stored("series-cats", Category.serializer()) { api.seriesCategories(it) }
    suspend fun series() = stored("series", Series.serializer()) { c ->
        api.series(c).filter { it.seriesId != 0 }.distinctBy { it.seriesId }
    }
    suspend fun seriesInfo(id: Int) = memo("series-info:$id") { api.seriesInfo(it, id) }

    /** La guía cambia con el tiempo: no se cachea. */
    suspend fun shortEpg(streamId: Int): List<EpgItem> {
        val creds = session.creds.value ?: return emptyList()
        return api.shortEpg(creds, streamId)
    }

    /** Estado de la cuenta en uso (tampoco se cachea). */
    suspend fun account(): UserInfo {
        val creds = session.creds.value ?: throw PanelException("Sesión vencida")
        return api.account(creds)
    }

    private companion object {
        /** Antigüedad a partir de la cual una copia se renueva en segundo plano. */
        const val REFRESH_AFTER_MS = 6 * 60 * 60 * 1000L
    }
}
