package app.iptv.player.ui.vod

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.iptv.player.AppGraph
import app.iptv.player.data.FavKind
import app.iptv.player.data.Series
import app.iptv.player.data.StreamKind
import app.iptv.player.data.VodStream
import app.iptv.player.domain.EpisodeDetails
import app.iptv.player.domain.SeasonDetails
import app.iptv.player.domain.clock
import app.iptv.player.domain.movieDetails
import app.iptv.player.domain.seriesDetails
import app.iptv.player.player.PlayerActivity
import app.iptv.player.player.QueueItem
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.Chip
import app.iptv.player.ui.components.PosterArt
import app.iptv.player.ui.components.RoundIconButton
import app.iptv.player.ui.components.Tag
import app.iptv.player.ui.components.alpha
import app.iptv.player.ui.components.rememberLoaded
import app.iptv.player.ui.theme.Palette

/** Cabecera común de las fichas: fondo con degradado, póster, título, datos y acciones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailHeader(
    title: String,
    poster: String?,
    backdrop: String?,
    rating: String?,
    tags: List<String>,
    genres: List<String>,
    onClose: () -> Unit,
    actions: @Composable () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        PosterArt(title, backdrop ?: poster, showName = false, modifier = Modifier.fillMaxWidth().height(230.dp))
        Box(
            modifier = Modifier.fillMaxWidth().height(230.dp)
                .background(Brush.verticalGradient(listOf(Palette.Background.alpha(0.25f), Palette.Background.alpha(0.7f), Palette.Background))),
        )
        RoundIconButton(
            AppIcons.ChevronLeft,
            "Volver",
            onClose,
            tint = Palette.Foreground,
            background = Palette.Background.alpha(0.7f),
            modifier = Modifier.statusBarsPadding().padding(12.dp),
        )
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 130.dp).widthIn(max = 900.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val shape = RoundedCornerShape(12.dp)
            PosterArt(title, poster, modifier = Modifier.width(116.dp).aspectRatio(2f / 3f).clip(shape).border(1.dp, Palette.Border, shape))
            Column(modifier = Modifier.weight(1f).padding(top = 34.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, color = Palette.Foreground, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (rating != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(AppIcons.Star, null, tint = Palette.Warning, modifier = Modifier.size(13.dp))
                            Text(rating, color = Palette.Foreground, fontSize = 13.sp, fontFamily = AppFonts.Mono)
                        }
                    }
                    tags.forEach { Tag(it) }
                    genres.forEach { Text(it, color = Palette.Soft, fontSize = 12.sp) }
                }
            }
        }
    }
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) { actions() }
}

@Composable
private fun FavoriteAction(favorite: Boolean, onToggle: () -> Unit) {
    OutlinedButton(onClick = onToggle) {
        Icon(
            if (favorite) AppIcons.Star else AppIcons.StarOutline,
            null,
            tint = if (favorite) Palette.Warning else Palette.Soft,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(if (favorite) "En favoritos" else "Favorito", color = Palette.Foreground)
    }
}

@Composable
private fun Fact(label: String, value: String?) {
    if (value.isNullOrEmpty()) return
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 900.dp)) {
        Text(label.uppercase(), color = Palette.Dim, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        Text(value, color = Palette.Soft, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
private fun Plot(text: String?) {
    if (text.isNullOrEmpty()) return
    var expanded by remember(text) { mutableStateOf(false) }
    Text(
        text,
        color = Palette.Soft,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        maxLines = if (expanded) Int.MAX_VALUE else 6,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.widthIn(max = 900.dp).clickable { expanded = !expanded }.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Barra fina de avance (película o episodio a medio ver). */
@Composable
private fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(Palette.Secondary)) {
        Box(modifier = Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(4.dp).background(Palette.Primary))
    }
}

/** Botón principal de reproducción: "Continuar · 1:02:13" si quedó a medias, o el texto dado. */
@Composable
private fun PlayButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Button(enabled = enabled, onClick = onClick) {
        Icon(AppIcons.Play, null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Ficha de película. Cada campo es opcional: se muestra lo que el panel entregue. */
@Composable
fun MovieDetail(graph: AppGraph, id: Int, base: VodStream?, onClose: () -> Unit) {
    val context = LocalContext.current
    val info = rememberLoaded(id) { graph.catalog.vodInfo(id) }
    val movie = movieDetails(info.data, base)
    val favorites by graph.favorites.all.collectAsState()
    val favorite = id in favorites[FavKind.MOVIE].orEmpty()
    val progressVersion by graph.progress.version.collectAsState()
    val progress = remember(id, progressVersion) { graph.progress.get(StreamKind.MOVIE, id.toString()) }
    val resumable = progress?.takeIf { !it.finished && it.positionMs > 0 }

    fun play(fromStart: Boolean) =
        context.startActivity(PlayerActivity.intent(context, StreamKind.MOVIE, id.toString(), movie.ext, movie.title, fromStart))

    BackHandler(onBack = onClose)
    Column(modifier = Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState())) {
        DetailHeader(
            title = movie.title,
            poster = movie.poster,
            backdrop = movie.backdrop,
            rating = movie.rating,
            tags = listOfNotNull(movie.year, movie.duration, "Vista".takeIf { progress?.finished == true }),
            genres = movie.genres,
            onClose = onClose,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Se espera a la ficha para conocer la extensión real del archivo.
                    if (resumable != null) {
                        PlayButton("Continuar · ${clock(resumable.positionMs)}", enabled = !info.loading) { play(fromStart = false) }
                        OutlinedButton(enabled = !info.loading, onClick = { play(fromStart = true) }) {
                            Text("Desde el inicio", color = Palette.Foreground)
                        }
                    } else {
                        PlayButton("Reproducir", enabled = !info.loading) { play(fromStart = false) }
                    }
                    movie.trailer?.let { url ->
                        OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                            Icon(AppIcons.Trailer, null, tint = Palette.Soft, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Tráiler", color = Palette.Foreground)
                        }
                    }
                    FavoriteAction(favorite) { graph.favorites.toggle(FavKind.MOVIE, id) }
                }
                if (resumable != null && resumable.durationMs > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressBar(resumable.fraction, modifier = Modifier.width(220.dp))
                        Text(
                            "Quedan ${clock(resumable.durationMs - resumable.positionMs)}",
                            color = Palette.Dim,
                            fontSize = 12.sp,
                            fontFamily = AppFonts.Mono,
                        )
                    }
                }
            }
        }
        Plot(movie.plot)
        Fact("Reparto", movie.cast)
        Fact("Dirección", movie.director)
        if (!info.loading && movie.plot == null && movie.cast == null) {
            Text("Sin sinopsis ni ficha para este título.", color = Palette.Dim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Un episodio con la temporada a la que pertenece (para la cola y "Continuar"). */
private class EpisodeRef(val season: SeasonDetails, val episode: EpisodeDetails)

/**
 * Ficha de serie con selector de temporada y lista de episodios.
 * "Continuar" retoma el episodio a medio ver más avanzado o, si el último visto terminó, el siguiente.
 * Al reproducir un episodio, los que siguen (de todas las temporadas) quedan en cola.
 */
@Composable
fun SeriesDetail(graph: AppGraph, id: Int, base: Series?, onClose: () -> Unit) {
    val context = LocalContext.current
    val info = rememberLoaded(id) { graph.catalog.seriesInfo(id) }
    val series = seriesDetails(info.data, base)
    val favorites by graph.favorites.all.collectAsState()
    val favorite = id in favorites[FavKind.SERIES].orEmpty()
    val progressVersion by graph.progress.version.collectAsState()

    val all = remember(series.seasons) { series.seasons.flatMap { s -> s.episodes.map { EpisodeRef(s, it) } } }
    val progress = remember(all, progressVersion) { all.associate { it.episode.id to graph.progress.get(StreamKind.SERIES, it.episode.id) } }
    val target: Int? = remember(all, progress) {
        val last = all.indexOfLast { progress[it.episode.id] != null }
        when {
            all.isEmpty() -> null
            last < 0 -> 0
            progress[all[last].episode.id]?.finished == true -> (last + 1).takeIf { it <= all.lastIndex }
            else -> last
        }
    }
    val started = all.any { progress[it.episode.id] != null }

    var seasonId by rememberSaveable(id) { mutableStateOf<String?>(null) }
    // Sin elección del usuario, se abre en la temporada del episodio a continuar.
    val season = series.seasons.firstOrNull { it.id == seasonId }
        ?: target?.let { all[it].season }
        ?: series.seasons.firstOrNull()

    fun play(index: Int) {
        val queue = all.map { ref ->
            QueueItem(ref.episode.id, ref.episode.ext, "${series.title} · T${ref.season.id} ${ref.episode.code} · ${ref.episode.title}")
        }
        context.startActivity(PlayerActivity.intent(context, StreamKind.SERIES, queue, index))
    }

    BackHandler(onBack = onClose)
    Column(modifier = Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState())) {
        DetailHeader(
            title = series.title,
            poster = series.cover,
            backdrop = series.backdrop,
            rating = series.rating,
            tags = listOfNotNull(
                series.year,
                series.seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 temporada" else "$it temporadas" },
            ),
            genres = series.genres,
            onClose = onClose,
        ) {
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (target != null) {
                    val ref = all[target]
                    val code = "T${ref.season.id} ${ref.episode.code}"
                    PlayButton(if (started) "Continuar $code" else "Ver $code", enabled = true) { play(target) }
                }
                FavoriteAction(favorite) { graph.favorites.toggle(FavKind.SERIES, id) }
            }
        }
        Plot(series.plot)
        Fact("Reparto", series.cast)
        Fact("Dirección", series.director)

        when {
            info.error != null -> Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No se pudieron cargar los episodios.", color = Palette.Destructive, fontSize = 14.sp)
                OutlinedButton(onClick = info.reload) { Text("Reintentar", color = Palette.Foreground) }
            }
            info.loading -> Text("Cargando episodios…", color = Palette.Dim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            season == null -> Text("Esta serie no tiene episodios disponibles.", color = Palette.Dim, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            else -> {
                if (series.seasons.size > 1) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        series.seasons.forEach { s ->
                            Chip(s.label, active = s.id == season.id, onClick = { seasonId = s.id }, count = s.episodes.size.toString())
                        }
                    }
                } else {
                    Text(season.label, color = Palette.Foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
                Column(modifier = Modifier.padding(horizontal = 16.dp).widthIn(max = 900.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    season.episodes.forEach { e ->
                        val index = all.indexOfFirst { it.episode.id == e.id }
                        val p = progress[e.id]
                        val shape = RoundedCornerShape(12.dp)
                        val isTarget = index == target && started
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(shape)
                                .background(if (isTarget) Palette.Primary.alpha(0.08f) else Palette.Card)
                                .border(1.dp, if (isTarget) Palette.Primary.alpha(0.5f) else Palette.Border, shape)
                                .clickable { if (index >= 0) play(index) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.width(104.dp).clip(RoundedCornerShape(8.dp))) {
                                PosterArt(e.title, e.thumb, showName = false, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                Box(
                                    modifier = Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(Color.Black.alpha(0.55f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(AppIcons.Play, "Reproducir", tint = Color.White, modifier = Modifier.size(13.dp).offset(x = 1.dp))
                                }
                                if (p != null && !p.finished) ProgressBar(p.fraction, modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(e.code, color = Palette.Primary, fontSize = 12.sp, fontFamily = AppFonts.Mono)
                                    if (p?.finished == true) {
                                        Icon(AppIcons.Check, null, tint = Palette.Success, modifier = Modifier.size(13.dp))
                                        Text("Visto", color = Palette.Success, fontSize = 11.sp)
                                    } else if (p != null) {
                                        Text("Quedan ${clock(p.durationMs - p.positionMs)}", color = Palette.Dim, fontSize = 11.sp, fontFamily = AppFonts.Mono)
                                    }
                                }
                                Text(e.title, color = Palette.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (e.duration != null) Text(e.duration, color = Palette.Dim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}
