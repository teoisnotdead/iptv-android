package app.iptv.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.data.Category
import app.iptv.player.ui.theme.Palette

private const val ROW_LIMIT = 24 // ítems visibles por fila antes de "Ver más"

/** Fila fija sobre las categorías cuando no hay filtros (p. ej. los favoritos). */
class Pinned<T>(val title: String, val items: List<T>, val onSeeAll: () -> Unit)

/**
 * Explorador de catálogo reutilizable (TV, películas, series): buscador, chips de categoría y,
 * debajo, filas horizontales por categoría o una grilla al elegir una categoría, buscar o filtrar.
 * Es agnóstico del contenido: el llamador dibuja cada ítem con `item`.
 */
@Composable
fun <T> CatalogBrowser(
    items: List<T>,
    categories: List<Category>,
    keyOf: (T) -> Any,
    categoryOf: (T) -> String,
    category: String,
    onCategoryChange: (String) -> Unit,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    /** Ancho de cada tarjeta en las filas y ancho mínimo de columna en la grilla. */
    cardWidth: Dp,
    gridMinWidth: Dp,
    /** Alto del botón "Ver más" al final de una fila (el de una tarjeta). */
    cardHeight: Dp,
    allLabel: String,
    unit: String,
    unitOne: String,
    /** Muestra siempre la grilla (búsqueda, solo favoritos, lista junto al reproductor). */
    flat: Boolean,
    flatTitle: String,
    modifier: Modifier = Modifier,
    gutter: Dp = 16.dp,
    /** false = sin barra de categorías: el llamador las muestra en otro lugar (junto al reproductor). */
    categoryBar: Boolean = true,
    /** Clave del ítem activo (el canal en reproducción): la grilla se desplaza hasta él si no está a la vista. */
    activeKey: Any? = null,
    pinned: Pinned<T>? = null,
    empty: (@Composable () -> Unit)? = null,
    toolbar: @Composable RowScope.() -> Unit,
    item: @Composable (T) -> Unit,
) {
    val groups = remember(items) { items.groupBy(categoryOf) }
    val visibleCategories = remember(categories, groups) { categories.filter { groups.containsKey(it.id) } }
    val counts = remember(groups) { groups.mapValues { it.value.size } }
    val gridMode = flat || category.isNotEmpty()
    val gridItems = if (category.isNotEmpty()) groups[category].orEmpty() else items

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = gutter, end = gutter, top = 12.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = toolbar,
        )
        if (categoryBar && visibleCategories.isNotEmpty()) {
            var picking by remember { mutableStateOf(false) }
            if (picking) {
                CategoryPickerDialog(
                    categories = visibleCategories,
                    counts = counts,
                    value = category,
                    allLabel = allLabel,
                    onPick = {
                        onCategoryChange(it)
                        picking = false
                    },
                    onDismiss = { picking = false },
                )
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                // Abre el listado completo: con muchas categorías, recorrer esta barra es lento.
                item(key = "\u0000selector") {
                    Chip("Categorías", active = false, onClick = { picking = true }, count = visibleCategories.size.toString(), icon = AppIcons.Grid)
                }
                item(key = "") { Chip(allLabel, active = category.isEmpty(), onClick = { onCategoryChange("") }) }
                items(visibleCategories, key = { it.id }) { c ->
                    Chip(c.name, active = category == c.id, onClick = { onCategoryChange(c.id) }, count = counts[c.id]?.toString())
                }
            }
        }

        when {
            error != null -> EmptyState(
                AppIcons.Warning,
                "No pudimos cargar el catálogo",
                "El servidor de tu proveedor no respondió. Revisa tu conexión e inténtalo de nuevo.",
                error = true,
                detail = error,
                onRetry = onRetry,
            )
            loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> if (empty != null) empty() else {
                EmptyState(AppIcons.SearchOff, "Sin resultados", "Prueba con otro nombre o busca en todas las categorías.")
            }
            gridMode -> {
                val gridState = rememberLazyGridState()
                LaunchedEffect(activeKey, gridItems) {
                    if (activeKey == null) return@LaunchedEffect
                    val index = gridItems.indexOfFirst { keyOf(it) == activeKey }
                    if (index < 0) return@LaunchedEffect
                    val visible = gridState.layoutInfo.visibleItemsInfo.any { it.key == activeKey }
                    // +1 por el título de la grilla, que es el primer ítem.
                    if (!visible) gridState.scrollToItem((index + 1 - 2).coerceAtLeast(0))
                }
                LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(gridMinWidth),
                contentPadding = PaddingValues(start = gutter, end = gutter, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "\u0000titulo", span = { GridItemSpan(maxLineSpan) }) {
                    val name = visibleCategories.firstOrNull { it.id == category }?.name
                    SectionHead(name ?: flatTitle, countLabel(gridItems.size, unit, unitOne))
                }
                if (gridItems.isEmpty()) {
                    item(key = "\u0000vacio", span = { GridItemSpan(maxLineSpan) }) {
                        EmptyState(AppIcons.SearchOff, "Sin resultados", "No hay coincidencias en esta categoría.")
                    }
                }
                items(gridItems, key = keyOf) { item(it) }
            }
            }
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
                if (pinned != null && pinned.items.isNotEmpty()) {
                    item(key = "\u0000fijados") {
                        CategoryRow(pinned.title, pinned.items, keyOf, cardWidth, cardHeight, gutter, unit, unitOne, pinned.onSeeAll, item)
                    }
                }
                items(visibleCategories, key = { it.id }) { c ->
                    CategoryRow(c.name, groups[c.id].orEmpty(), keyOf, cardWidth, cardHeight, gutter, unit, unitOne, { onCategoryChange(c.id) }, item)
                }
            }
        }
    }
}

@Composable
private fun SectionHead(title: String, count: String, modifier: Modifier = Modifier, onSeeAll: (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        // Título y contador ocupan el espacio libre; "Ver todo" queda pegado a la derecha.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Palette.Foreground,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(count, color = Palette.Dim, fontSize = 12.sp, fontFamily = AppFonts.Mono, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
        }
        if (onSeeAll != null) {
            Text(
                "Ver todo",
                color = Palette.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onSeeAll).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun <T> CategoryRow(
    title: String,
    list: List<T>,
    keyOf: (T) -> Any,
    cardWidth: Dp,
    cardHeight: Dp,
    gutter: Dp,
    unit: String,
    unitOne: String,
    onSeeAll: () -> Unit,
    item: @Composable (T) -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 18.dp)) {
        SectionHead(title, countLabel(list.size, unit, unitOne), modifier = Modifier.padding(horizontal = gutter), onSeeAll = onSeeAll)
        LazyRow(
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            items(list.take(ROW_LIMIT), key = keyOf) { Box(modifier = Modifier.width(cardWidth)) { item(it) } }
            if (list.size > ROW_LIMIT) {
                item(key = "\u0000mas") {
                    val shape = RoundedCornerShape(12.dp)
                    Column(
                        modifier = Modifier.width(minOf(cardWidth, 150.dp)).height(cardHeight).clip(shape)
                            .background(Palette.Secondary).border(1.dp, Palette.Border, shape).clickable(onClick = onSeeAll),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(AppIcons.Plus, null, tint = Palette.Soft, modifier = Modifier.size(20.dp))
                        Text("Ver más", color = Palette.Soft, fontSize = 13.sp)
                        Text(countLabel(list.size, "", "").trim(), color = Palette.Primary, fontSize = 12.sp, fontFamily = AppFonts.Mono)
                    }
                }
            }
        }
    }
}
