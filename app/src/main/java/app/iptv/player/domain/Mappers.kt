package app.iptv.player.domain

import app.iptv.player.data.Series
import app.iptv.player.data.SeriesInfo
import app.iptv.player.data.VodInfo
import app.iptv.player.data.VodStream

/* Datos crudos del panel → modelos de vista (mismas reglas que `lib/mappers.ts` de la web). */

private fun String.orNull(): String? = ifEmpty { null }
private fun first(vararg values: String?): String? = values.firstOrNull { !it.isNullOrEmpty() }

/** Modelo mínimo para pintar un póster (película o serie). */
data class PosterItem(val id: Int, val name: String, val image: String?, val rating: String?, val categoryId: String)

fun VodStream.toPoster() = PosterItem(streamId, name, icon.orNull(), formatRating(rating), categoryId)
fun Series.toPoster() = PosterItem(seriesId, name, cover.orNull(), formatRating(rating), categoryId)

data class MovieDetails(
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val rating: String?,
    val year: String?,
    val duration: String?,
    val genres: List<String>,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val trailer: String?,
    val ext: String,
)

/** Une la ficha de get_vod_info con lo que ya trae la lista, tolerando campos faltantes. */
fun movieDetails(info: VodInfo?, base: VodStream?): MovieDetails {
    val i = info?.info
    return MovieDetails(
        title = first(i?.name, info?.movieData?.name, base?.name).orEmpty(),
        poster = first(i?.movieImage, i?.coverBig, base?.icon),
        backdrop = i?.backdrops?.firstOrNull(),
        rating = formatRating(first(i?.rating, base?.rating)),
        year = first(i?.releasedate, i?.year)?.take(4),
        duration = formatDuration(i?.duration),
        genres = splitList(i?.genre),
        plot = first(i?.plot, i?.description),
        cast = i?.cast?.orNull(),
        director = i?.director?.orNull(),
        trailer = youtubeUrl(i?.youtubeTrailer),
        ext = first(info?.movieData?.containerExtension, base?.containerExtension) ?: "mp4",
    )
}

data class EpisodeDetails(
    val id: String,
    val num: Int,
    val code: String,
    val title: String,
    val duration: String?,
    val thumb: String?,
    val ext: String,
)

data class SeasonDetails(val id: String, val label: String, val episodes: List<EpisodeDetails>)

data class SeriesDetails(
    val title: String,
    val cover: String?,
    val backdrop: String?,
    val rating: String?,
    val year: String?,
    val genres: List<String>,
    val plot: String?,
    val cast: String?,
    val director: String?,
    val seasons: List<SeasonDetails>,
)

private val EPISODE_CODE = Regex("^S\\d{1,3}\\s*E\\d{1,4}\\b", RegexOption.IGNORE_CASE)
private val SEPARATORS = Regex("^[\\s\\-–—·|:.]+")

/**
 * Título de episodio sin lo que repiten muchos paneles: el nombre de la serie y el código
 * ("Serie - S01E02 - Sombra del pasado" → "Sombra del pasado"). Si no queda nada, "" (el llamador pone "Episodio N").
 */
fun cleanEpisodeTitle(title: String, seriesName: String): String {
    var t = title.trim()
    val series = seriesName.trim()
    if (series.isNotEmpty() && normalize(t).startsWith(normalize(series))) t = t.substring(series.length)
    t = SEPARATORS.replace(t, "")
    t = EPISODE_CODE.replace(t, "")
    return SEPARATORS.replace(t, "").trim()
}

fun seriesDetails(info: SeriesInfo?, base: Series?): SeriesDetails {
    val i = info?.info
    val seriesName = first(i?.name, base?.name).orEmpty()
    val seasons = info?.episodes.orEmpty().entries
        .sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }
        .map { (id, episodes) ->
            SeasonDetails(
                id = id,
                label = "Temporada $id",
                episodes = episodes
                    .filter { it.id.isNotEmpty() }
                    .mapIndexed { index, e ->
                        val num = e.episodeNum.toIntOrNull()?.takeIf { it != 0 } ?: (index + 1)
                        EpisodeDetails(
                            id = e.id,
                            num = num,
                            code = "E" + num.toString().padStart(2, '0'),
                            title = cleanEpisodeTitle(e.title, seriesName).ifEmpty { "Episodio $num" },
                            duration = formatDuration(e.info.duration),
                            thumb = e.info.movieImage.orNull(),
                            ext = e.containerExtension.ifEmpty { "mp4" },
                        )
                    }
                    .sortedBy { it.num },
            )
        }
    return SeriesDetails(
        title = first(i?.name, base?.name).orEmpty(),
        cover = first(i?.cover, base?.cover),
        backdrop = i?.backdrops?.firstOrNull(),
        rating = formatRating(first(i?.rating, base?.rating)),
        year = i?.releaseDate?.take(4)?.orNull(),
        genres = splitList(i?.genre),
        plot = first(i?.plot, base?.plot),
        cast = i?.cast?.orNull(),
        director = i?.director?.orNull(),
        seasons = seasons,
    )
}
