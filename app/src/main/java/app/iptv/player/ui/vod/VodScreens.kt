package app.iptv.player.ui.vod

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.iptv.player.AppGraph
import app.iptv.player.data.FavKind
import app.iptv.player.domain.toPoster
import app.iptv.player.ui.components.Loaded
import app.iptv.player.ui.components.rememberLoaded

/** Películas: catálogo y, encima, la ficha de la película abierta. */
@Composable
fun MoviesScreen(graph: AppGraph) {
    val categories = rememberLoaded { graph.catalog.movieCategories() }
    val movies = rememberLoaded { graph.catalog.movies() }
    val posters = remember(movies.data) { movies.data?.map { it.toPoster() } }
    var openId by rememberSaveable { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        PosterCatalog(
            graph = graph,
            kind = FavKind.MOVIE,
            items = Loaded(posters, movies.error, movies.reload),
            categories = categories,
            unit = "películas",
            unitOne = "película",
            searchPlaceholder = "Buscar película o categoría…",
            onOpen = { openId = it.id },
        )
        openId?.let { id ->
            MovieDetail(graph, id, base = movies.data?.firstOrNull { it.streamId == id }, onClose = { openId = null })
        }
    }
}

/** Series: catálogo y, encima, la ficha de la serie abierta. */
@Composable
fun SeriesScreen(graph: AppGraph) {
    val categories = rememberLoaded { graph.catalog.seriesCategories() }
    val series = rememberLoaded { graph.catalog.series() }
    val posters = remember(series.data) { series.data?.map { it.toPoster() } }
    var openId by rememberSaveable { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        PosterCatalog(
            graph = graph,
            kind = FavKind.SERIES,
            items = Loaded(posters, series.error, series.reload),
            categories = categories,
            unit = "series",
            unitOne = "serie",
            searchPlaceholder = "Buscar serie o categoría…",
            onOpen = { openId = it.id },
        )
        openId?.let { id ->
            SeriesDetail(graph, id, base = series.data?.firstOrNull { it.seriesId == id }, onClose = { openId = null })
        }
    }
}
