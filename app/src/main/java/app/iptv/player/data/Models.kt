package app.iptv.player.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

/*
 * Tipos de las respuestas de player_api.php.
 * Los paneles son inconsistentes: un mismo campo llega como número o como texto,
 * puede faltar, venir en null o (si no hay datos) como arreglo vacío. Por eso todo
 * campo tiene valor por defecto y se lee con serializadores tolerantes:
 * texto vacío o 0 significan "no viene".
 */

private fun Decoder.primitiveOrNull(): JsonPrimitive? {
    val el = (this as? JsonDecoder)?.decodeJsonElement() ?: return null
    return if (el is JsonPrimitive && el !is JsonNull) el else null
}

/** Texto que el panel puede mandar como número, null u otra cosa. */
object FlexString : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)
    override fun deserialize(decoder: Decoder): String = decoder.primitiveOrNull()?.content ?: ""
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

/** Entero que el panel puede mandar como texto ("123"), null u otra cosa. */
object FlexInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun deserialize(decoder: Decoder): Int = decoder.primitiveOrNull()?.content?.trim()?.toIntOrNull() ?: 0
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
}

/** Objeto que el panel reemplaza por `[]`, null o un texto cuando no tiene datos. */
open class ObjectOrEmpty<T : Any>(serializer: KSerializer<T>) : JsonTransformingSerializer<T>(serializer) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonObject) element else JsonObject(emptyMap())
}

@Serializable
data class Category(
    @SerialName("category_id") @Serializable(FlexString::class) val id: String = "",
    @SerialName("category_name") @Serializable(FlexString::class) val name: String = "",
)

@Serializable
data class LiveStream(
    @Serializable(FlexInt::class) val num: Int = 0,
    @Serializable(FlexString::class) val name: String = "",
    @SerialName("stream_id") @Serializable(FlexInt::class) val streamId: Int = 0,
    @SerialName("stream_icon") @Serializable(FlexString::class) val icon: String = "",
    @SerialName("epg_channel_id") @Serializable(FlexString::class) val epgChannelId: String = "",
    @SerialName("category_id") @Serializable(FlexString::class) val categoryId: String = "",
)

@Serializable
data class VodStream(
    @SerialName("stream_id") @Serializable(FlexInt::class) val streamId: Int = 0,
    @Serializable(FlexString::class) val name: String = "",
    @SerialName("stream_icon") @Serializable(FlexString::class) val icon: String = "",
    @Serializable(FlexString::class) val rating: String = "",
    @SerialName("category_id") @Serializable(FlexString::class) val categoryId: String = "",
    @SerialName("container_extension") @Serializable(FlexString::class) val containerExtension: String = "",
)

/** `user_info` de player_api.php sin acción: estado de la cuenta. */
@Serializable
data class UserInfo(
    @Serializable(FlexInt::class) val auth: Int = 0,
    @Serializable(FlexString::class) val username: String = "",
    @Serializable(FlexString::class) val status: String = "",
    /** Vencimiento en segundos Unix; vacío si la cuenta no vence. */
    @SerialName("exp_date") @Serializable(FlexString::class) val expDate: String = "",
    /** Dispositivos simultáneos permitidos (1 a 3 según la cuenta). */
    @SerialName("max_connections") @Serializable(FlexString::class) val maxConnections: String = "",
    @SerialName("active_cons") @Serializable(FlexString::class) val activeConnections: String = "",
)

object UserInfoOrEmpty : ObjectOrEmpty<UserInfo>(UserInfo.serializer())

@Serializable
data class AuthResponse(
    @SerialName("user_info") @Serializable(UserInfoOrEmpty::class) val userInfo: UserInfo = UserInfo(),
)

/** Campo que el panel manda como texto, como arreglo de textos o vacío (p. ej. `backdrop_path`). */
object FlexStringList : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<String> {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return emptyList()
        val values = if (el is JsonArray) el.toList() else listOf(el)
        return values.mapNotNull { v -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() } }
    }
    override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)
}

@Serializable
data class Series(
    @SerialName("series_id") @Serializable(FlexInt::class) val seriesId: Int = 0,
    @Serializable(FlexString::class) val name: String = "",
    @Serializable(FlexString::class) val cover: String = "",
    @Serializable(FlexString::class) val plot: String = "",
    @Serializable(FlexString::class) val rating: String = "",
    @SerialName("category_id") @Serializable(FlexString::class) val categoryId: String = "",
)

/** Programa de la guía. Título y descripción vienen en base64; los horarios, en segundos Unix. */
@Serializable
data class EpgItem(
    @Serializable(FlexString::class) val title: String = "",
    @Serializable(FlexString::class) val description: String = "",
    @SerialName("start_timestamp") @Serializable(FlexString::class) val start: String = "",
    @SerialName("stop_timestamp") @Serializable(FlexString::class) val stop: String = "",
)

@Serializable
data class EpgResponse(@SerialName("epg_listings") val listings: List<EpgItem> = emptyList())

@Serializable
data class VodInfoData(
    @Serializable(FlexString::class) val name: String = "",
    @Serializable(FlexString::class) val plot: String = "",
    @Serializable(FlexString::class) val description: String = "",
    @Serializable(FlexString::class) val cast: String = "",
    @Serializable(FlexString::class) val director: String = "",
    @Serializable(FlexString::class) val genre: String = "",
    @Serializable(FlexString::class) val releasedate: String = "",
    @Serializable(FlexString::class) val year: String = "",
    @Serializable(FlexString::class) val duration: String = "",
    @Serializable(FlexString::class) val rating: String = "",
    @SerialName("movie_image") @Serializable(FlexString::class) val movieImage: String = "",
    @SerialName("cover_big") @Serializable(FlexString::class) val coverBig: String = "",
    @SerialName("backdrop_path") @Serializable(FlexStringList::class) val backdrops: List<String> = emptyList(),
    @SerialName("youtube_trailer") @Serializable(FlexString::class) val youtubeTrailer: String = "",
)

@Serializable
data class MovieData(
    @SerialName("stream_id") @Serializable(FlexInt::class) val streamId: Int = 0,
    @Serializable(FlexString::class) val name: String = "",
    @SerialName("container_extension") @Serializable(FlexString::class) val containerExtension: String = "",
)

object VodInfoDataOrEmpty : ObjectOrEmpty<VodInfoData>(VodInfoData.serializer())
object MovieDataOrEmpty : ObjectOrEmpty<MovieData>(MovieData.serializer())

@Serializable
data class VodInfo(
    @Serializable(VodInfoDataOrEmpty::class) val info: VodInfoData = VodInfoData(),
    @SerialName("movie_data") @Serializable(MovieDataOrEmpty::class) val movieData: MovieData = MovieData(),
)

@Serializable
data class EpisodeInfo(
    @Serializable(FlexString::class) val plot: String = "",
    @Serializable(FlexString::class) val duration: String = "",
    @SerialName("movie_image") @Serializable(FlexString::class) val movieImage: String = "",
)

object EpisodeInfoOrEmpty : ObjectOrEmpty<EpisodeInfo>(EpisodeInfo.serializer())

@Serializable
data class Episode(
    @Serializable(FlexString::class) val id: String = "",
    @SerialName("episode_num") @Serializable(FlexString::class) val episodeNum: String = "",
    @Serializable(FlexString::class) val title: String = "",
    @SerialName("container_extension") @Serializable(FlexString::class) val containerExtension: String = "",
    @Serializable(FlexString::class) val season: String = "",
    @Serializable(EpisodeInfoOrEmpty::class) val info: EpisodeInfo = EpisodeInfo(),
)

@Serializable
data class SeriesInfoData(
    @Serializable(FlexString::class) val name: String = "",
    @Serializable(FlexString::class) val cover: String = "",
    @Serializable(FlexString::class) val plot: String = "",
    @Serializable(FlexString::class) val genre: String = "",
    @Serializable(FlexString::class) val releaseDate: String = "",
    @Serializable(FlexString::class) val cast: String = "",
    @Serializable(FlexString::class) val director: String = "",
    @Serializable(FlexString::class) val rating: String = "",
    @SerialName("backdrop_path") @Serializable(FlexStringList::class) val backdrops: List<String> = emptyList(),
)

object SeriesInfoDataOrEmpty : ObjectOrEmpty<SeriesInfoData>(SeriesInfoData.serializer())

/**
 * Episodios por temporada. Lo normal es un objeto `{"1": [...], "2": [...]}`, pero hay paneles
 * que mandan un arreglo de temporadas (`[[...], [...]]`) o `[]` cuando la serie no tiene episodios.
 */
object EpisodesBySeason : JsonTransformingSerializer<Map<String, List<Episode>>>(
    MapSerializer(String.serializer(), ListSerializer(Episode.serializer())),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(element.filterValues { it is JsonArray })
        is JsonArray -> JsonObject(
            element.filterIsInstance<JsonArray>().withIndex().associate { (i, season) ->
                val declared = ((season.firstOrNull() as? JsonObject)?.get("season") as? JsonPrimitive)?.content
                (declared?.takeIf { it.toIntOrNull() != null } ?: (i + 1).toString()) to season
            },
        )
        else -> JsonObject(emptyMap())
    }
}

@Serializable
data class SeriesInfo(
    @Serializable(SeriesInfoDataOrEmpty::class) val info: SeriesInfoData = SeriesInfoData(),
    @Serializable(EpisodesBySeason::class) val episodes: Map<String, List<Episode>> = emptyMap(),
)
