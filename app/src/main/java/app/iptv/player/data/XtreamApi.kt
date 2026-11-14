package app.iptv.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder

/**
 * Una cuenta: servidor (URL base del panel, sin barra final) + usuario + contraseña.
 * `name` es el nombre que el usuario le puso para reconocerla; es opcional y solo vive en el dispositivo.
 */
@Serializable
data class Creds(val url: String, val username: String, val password: String, val name: String = "") {
    /** Nombre para mostrar: el personalizado o, si no tiene, el usuario. */
    val label: String get() = name.ifEmpty { username }

    /** Mismo servidor y usuario (la contraseña puede haber cambiado). */
    fun sameAccount(other: Creds) = url.equals(other.url, ignoreCase = true) && username == other.username

    /** Nombre del servidor para mostrar, sin esquema ni puerto. */
    val host: String get() = url.substringAfter("://").substringBefore('/').substringBefore(':')
}

private val SCHEME = Regex("^[a-z][a-z0-9+.-]*://", RegexOption.IGNORE_CASE)

/**
 * Deja la dirección que escribió el usuario como URL base del panel: agrega `http://` si falta
 * y quita la barra final o un `/player_api.php…` o `/get.php…` pegado desde un enlace. "" si queda vacía.
 */
fun normalizePanelUrl(input: String): String {
    var s = input.trim()
    if (s.isEmpty()) return ""
    val scheme = SCHEME.find(s)?.value
    s = if (scheme == null) "http://$s" else scheme.lowercase() + s.drop(scheme.length)
    s = s.substringBefore('?').substringBefore('#')
    for (tail in listOf("/player_api.php", "/get.php", "/xmltv.php")) {
        if (s.endsWith(tail, ignoreCase = true)) s = s.dropLast(tail.length)
    }
    return s.trimEnd('/')
}

/** Con el agente por defecto, los hosts de logos detrás de Cloudflare responden 403. */
const val PANEL_USER_AGENT = "Mozilla/5.0 (IPTV-Web)"

/**
 * Fallo al hablar con el panel. El mensaje es apto para mostrar: nunca incluye la URL
 * (lleva usuario y contraseña). `invalid` indica que respondió algo que no es el JSON esperado.
 */
class PanelException(message: String, val invalid: Boolean = false) : Exception(message)

enum class StreamKind(val path: String) { LIVE("live"), MOVIE("movie"), SERIES("series") }

/** Cliente de la API compatible con Xtream Codes (player_api.php). La app habla directo con el panel. */
class XtreamApi(private val http: OkHttpClient) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun <T> get(creds: Creds, params: Map<String, String>, strategy: DeserializationStrategy<T>): T =
        withContext(Dispatchers.IO) {
            val root = creds.url.toHttpUrlOrNull() ?: throw PanelException("La dirección del servidor no es válida")
            val url = root.newBuilder()
                .addPathSegment("player_api.php")
                .addQueryParameter("username", creds.username)
                .addQueryParameter("password", creds.password)
                .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
                .build()
            try {
                http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                    if (!res.isSuccessful) throw PanelException("El servidor respondió ${res.code}")
                    val body = res.body ?: throw PanelException("Respuesta inválida del servidor", invalid = true)
                    json.decodeFromStream(strategy, body.byteStream())
                }
            } catch (e: SerializationException) {
                throw PanelException("Respuesta inválida del servidor", invalid = true)
            } catch (e: IOException) {
                throw PanelException("No se pudo contactar al servidor")
            }
        }

    /** Listas del catálogo. Si no hay datos, algunos paneles mandan `{}` o null en vez de `[]`. */
    private suspend fun <T> list(creds: Creds, action: String, item: KSerializer<T>): List<T> =
        try {
            get(creds, mapOf("action" to action), ListSerializer(item))
        } catch (e: PanelException) {
            if (e.invalid) emptyList() else throw e
        }

    /** Sin acción: estado de la cuenta. */
    suspend fun account(creds: Creds): UserInfo = get(creds, emptyMap(), AuthResponse.serializer()).userInfo

    suspend fun liveCategories(creds: Creds) = list(creds, "get_live_categories", Category.serializer())
    suspend fun liveStreams(creds: Creds) = list(creds, "get_live_streams", LiveStream.serializer())
    suspend fun vodCategories(creds: Creds) = list(creds, "get_vod_categories", Category.serializer())
    suspend fun vodStreams(creds: Creds) = list(creds, "get_vod_streams", VodStream.serializer())
    suspend fun seriesCategories(creds: Creds) = list(creds, "get_series_categories", Category.serializer())
    suspend fun series(creds: Creds) = list(creds, "get_series", Series.serializer())

    /** Ficha de una película. Si el panel no la tiene, responde vacía (todos los campos por defecto). */
    suspend fun vodInfo(creds: Creds, id: Int): VodInfo =
        orDefault(VodInfo()) { get(creds, mapOf("action" to "get_vod_info", "vod_id" to id.toString()), VodInfo.serializer()) }

    suspend fun seriesInfo(creds: Creds, id: Int): SeriesInfo =
        orDefault(SeriesInfo()) { get(creds, mapOf("action" to "get_series_info", "series_id" to id.toString()), SeriesInfo.serializer()) }

    /** Guía corta de un canal (pocos programas desde ahora). Solo una minoría de canales la trae. */
    suspend fun shortEpg(creds: Creds, streamId: Int): List<EpgItem> = orDefault(emptyList()) {
        get(creds, mapOf("action" to "get_short_epg", "stream_id" to streamId.toString(), "limit" to "6"), EpgResponse.serializer()).listings
    }

    private suspend fun <T> orDefault(default: T, call: suspend () -> T): T =
        try {
            call()
        } catch (e: PanelException) {
            if (e.invalid) default else throw e
        }

    /** URL directa del stream. Contiene las credenciales: no registrarla ni mostrarla. */
    fun streamUrl(creds: Creds, kind: StreamKind, id: String, ext: String): String =
        "${creds.url}/${kind.path}/${enc(creds.username)}/${enc(creds.password)}/$id.$ext"

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
