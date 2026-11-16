package app.iptv.player.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.coerceIn
import app.iptv.player.ui.components.CategoryList
import app.iptv.player.ui.theme.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.iptv.player.AppGraph
import app.iptv.player.data.Category
import app.iptv.player.data.FavKind
import app.iptv.player.domain.PosterItem
import app.iptv.player.domain.SearchIndex
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.CatalogBrowser
import app.iptv.player.ui.components.EmptyState
import app.iptv.player.ui.components.FavoritesToggle
import app.iptv.player.ui.components.Loaded
import app.iptv.player.ui.components.Pinned
import app.iptv.player.ui.components.PosterCard
import app.iptv.player.ui.components.SearchField

/**
 * Catálogo de pósters compartido por películas y series: buscador (por nombre o categoría),
 * favoritos, categorías y filas o grilla. `onOpen` abre la ficha.
 */
@Composable
fun PosterCatalog(
    graph: AppGraph,
    kind: FavKind,
    items: Loaded<List<PosterItem>>,
    categories: Loaded<List<Category>>,
    unit: String,
    unitOne: String,
    searchPlaceholder: String,
    onOpen: (PosterItem) -> Unit,
) {
    val all = items.data.orEmpty()
    val cats = categories.data.orEmpty()
    val catNames = remember(cats) { cats.associate { it.id to it.name } }

    var category by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }

    val favorites by graph.favorites.all.collectAsState()
    val favoriteIds = favorites[kind].orEmpty()

    // Por nombre o por nombre de categoría; primero lo más parecido (ver domain/Search.kt).
    val index = remember(all, catNames) { SearchIndex(all, { it.name }, { catNames[it.categoryId].orEmpty() }) }
    val q = query.trim()
    val searching = q.isNotEmpty()
    val filtered = remember(all, index, q) { if (q.isEmpty()) all else index.search(q).items }
    val byId = remember(all) { all.associateBy { it.id } }
    val favoriteItems = remember(favoriteIds, byId) { favoriteIds.mapNotNull { byId[it] } }
    val visible = remember(onlyFavorites, filtered, favoriteItems) {
        if (!onlyFavorites) filtered else filtered.toSet().let { matches -> favoriteItems.filter { it in matches } }
    }
    val showOnlyFavorites = { on: Boolean ->
        onlyFavorites = on
        category = ""
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        val cardWidth = if (wide) 150.dp else 118.dp
        // En pantallas anchas, como en TV en vivo: columna de carpetas a la izquierda en vez de chips.
        val counts = remember(visible) { visible.groupingBy { it.categoryId }.eachCount() }
        val foldersWidth = (maxWidth * 0.2f).coerceIn(170.dp, 230.dp)
        Row(modifier = Modifier.fillMaxSize()) {
            if (wide) {
                CategoryList(
                    categories = cats,
                    counts = counts,
                    total = visible.size,
                    value = category,
                    onChange = { category = it },
                    allLabel = "Todas",
                    modifier = Modifier.width(foldersWidth).fillMaxHeight(),
                )
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
            }
        CatalogBrowser(
            items = visible,
            categories = cats,
            keyOf = { it.id },
            categoryOf = { it.categoryId },
            category = category,
            onCategoryChange = { category = it },
            loading = items.loading || categories.loading,
            error = items.error ?: categories.error,
            onRetry = {
                items.reload()
                categories.reload()
            },
            cardWidth = cardWidth,
            gridMinWidth = if (wide) 140.dp else 104.dp,
            cardHeight = cardWidth * 1.5f,
            allLabel = "Todas",
            unit = unit,
            unitOne = unitOne,
            flat = searching || onlyFavorites,
            flatTitle = when {
                onlyFavorites && searching -> "Favoritos con “$q”"
                onlyFavorites -> "Tus favoritos"
                else -> "Resultados para “$q”"
            },
            modifier = Modifier.weight(1f).fillMaxHeight(),
            categoryBar = !wide,
            pinned = Pinned("★ Favoritos", favoriteItems) { showOnlyFavorites(true) },
            empty = if (onlyFavorites && favoriteItems.isEmpty()) {
                { EmptyState(AppIcons.Star, "Aún no tienes favoritos", "Abre una ficha y toca la estrella para guardarla aquí.") }
            } else null,
            toolbar = {
                SearchField(query, { query = it }, searchPlaceholder, modifier = Modifier.weight(1f))
                FavoritesToggle(onlyFavorites, favoriteItems.size) { showOnlyFavorites(!onlyFavorites) }
            },
        ) { p ->
            PosterCard(p, favorite = p.id in favoriteIds, onOpen = { onOpen(p) })
        }
        }
    }
}
