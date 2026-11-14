package app.iptv.player.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.iptv.player.AppGraph
import app.iptv.player.data.FavKind
import app.iptv.player.domain.normalize
import app.iptv.player.domain.toPoster
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.ChannelCard
import app.iptv.player.ui.components.EmptyState
import app.iptv.player.ui.components.PosterCard
import app.iptv.player.ui.components.SearchField
import app.iptv.player.ui.components.countLabel
import app.iptv.player.ui.components.rememberLoaded
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.ui.theme.Palette
import app.iptv.player.ui.vod.MovieDetail
import app.iptv.player.ui.vod.SeriesDetail

/** Resultados visibles por tipo; si hay más, se pide afinar la búsqueda. */
private const val LIMIT = 40

/** Mínimo de letras para buscar (con una sola, casi todo coincide). */
private const val MIN_CHARS = 2

/**
 * Búsqueda global: canales, películas y series a la vez, por nombre.
 * Un canal se abre en TV en vivo (`onOpenChannel`); películas y series abren su ficha aquí mismo.
 */
@Composable
fun SearchScreen(graph: AppGraph, onOpenChannel: (Int) -> Unit) {
    val live = rememberLoaded { graph.catalog.liveStreams() }
    val liveCats = rememberLoaded { graph.catalog.liveCategories() }
    val movies = rememberLoaded { graph.catalog.movies() }
    val series = rememberLoaded { graph.catalog.series() }

    var query by rememberSaveable { mutableStateOf("") }
    var openMovie by rememberSaveable { mutableStateOf<Int?>(null) }
    var openSeries by rememberSaveable { mutableStateOf<Int?>(null) }
    val favorites by graph.favorites.all.collectAsState()

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (query.isEmpty()) runCatching { focus.requestFocus() } }

    // Nombres normalizados una sola vez por lista (las listas pueden tener decenas de miles de ítems).
    val liveKeys = remember(live.data) { live.data?.map { normalize(it.name) }.orEmpty() }
    val movieKeys = remember(movies.data) { movies.data?.map { normalize(it.name) }.orEmpty() }
    val seriesKeys = remember(series.data) { series.data?.map { normalize(it.name) }.orEmpty() }
    val catNames = remember(liveCats.data) { liveCats.data.orEmpty().associate { it.id to it.name } }

    val q = normalize(query.trim())
    val ready = q.length >= MIN_CHARS
    val liveHits = remember(q, liveKeys) { if (!ready) emptyList() else live.data.orEmpty().filterIndexed { i, _ -> liveKeys[i].contains(q) } }
    val movieHits = remember(q, movieKeys) { if (!ready) emptyList() else movies.data.orEmpty().filterIndexed { i, _ -> movieKeys[i].contains(q) } }
    val seriesHits = remember(q, seriesKeys) { if (!ready) emptyList() else series.data.orEmpty().filterIndexed { i, _ -> seriesKeys[i].contains(q) } }
    val loading = live.loading || movies.loading || series.loading
    val errors = listOfNotNull(live.error, movies.error, series.error).distinct()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchField(
                query,
                { query = it },
                "Buscar canales, películas y series…",
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                focusRequester = focus,
            )
            if (loading) Note("Cargando el catálogo… los resultados aparecen a medida que llega.")
            errors.forEach { Note("No se pudo cargar parte del catálogo: $it", error = true) }

            when {
                !ready -> EmptyState(
                    AppIcons.Search,
                    "Busca en todo el catálogo",
                    "Escribe al menos $MIN_CHARS letras: canales, películas y series a la vez.",
                )
                liveHits.isEmpty() && movieHits.isEmpty() && seriesHits.isEmpty() -> if (!loading) {
                    EmptyState(AppIcons.SearchOff, "Sin resultados", "No hay canales, películas ni series con “${query.trim()}”.")
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                    section("Canales", liveHits.size, "canales", "canal") {
                        items(liveHits.take(LIMIT), key = { it.streamId }) { c ->
                            Box(modifier = Modifier.width(300.dp)) {
                                ChannelCard(
                                    channel = c,
                                    category = catNames[c.categoryId],
                                    active = false,
                                    favorite = c.streamId in favorites[FavKind.LIVE].orEmpty(),
                                    onSelect = { onOpenChannel(c.streamId) },
                                    onToggleFavorite = { graph.favorites.toggle(FavKind.LIVE, c.streamId) },
                                )
                            }
                        }
                    }
                    section("Películas", movieHits.size, "películas", "película") {
                        items(movieHits.take(LIMIT), key = { it.streamId }) { m ->
                            Box(modifier = Modifier.width(128.dp)) {
                                PosterCard(m.toPoster(), favorite = m.streamId in favorites[FavKind.MOVIE].orEmpty(), onOpen = { openMovie = m.streamId })
                            }
                        }
                    }
                    section("Series", seriesHits.size, "series", "serie") {
                        items(seriesHits.take(LIMIT), key = { it.seriesId }) { s ->
                            Box(modifier = Modifier.width(128.dp)) {
                                PosterCard(s.toPoster(), favorite = s.seriesId in favorites[FavKind.SERIES].orEmpty(), onOpen = { openSeries = s.seriesId })
                            }
                        }
                    }
                }
            }
        }

        openMovie?.let { id -> MovieDetail(graph, id, base = movies.data?.firstOrNull { it.streamId == id }, onClose = { openMovie = null }) }
        openSeries?.let { id -> SeriesDetail(graph, id, base = series.data?.firstOrNull { it.seriesId == id }, onClose = { openSeries = null }) }
    }
}

/** Fila de resultados de un tipo (no aparece si no hubo coincidencias). */
private fun LazyListScope.section(
    title: String,
    count: Int,
    unit: String,
    unitOne: String,
    row: LazyListScope.() -> Unit,
) {
    if (count == 0) return
    item(key = title) {
        Column(modifier = Modifier.padding(top = 14.dp)) {
            Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Palette.Foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(countLabel(count, unit, unitOne), color = Palette.Dim, fontSize = 12.sp, fontFamily = AppFonts.Mono, modifier = Modifier.padding(start = 10.dp))
                if (count > LIMIT) {
                    Text("· se muestran $LIMIT, escribe más para afinar", color = Palette.Dim, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 10.dp),
                content = row,
            )
        }
    }
}

@Composable
private fun Note(text: String, error: Boolean = false) {
    Text(
        text,
        color = if (error) Palette.Destructive else Palette.Dim,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp),
    )
}
