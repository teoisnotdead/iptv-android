package app.iptv.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.data.Category
import app.iptv.player.domain.normalize
import app.iptv.player.ui.theme.Palette

/**
 * Todas las categorías a la vista, en varias líneas (sin scroll horizontal).
 * `counts` trae cuántos ítems tiene cada una; las que no aparecen ahí no se muestran.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryCloud(
    categories: List<Category>,
    counts: Map<String, Int>,
    value: String,
    onChange: (String) -> Unit,
    allLabel: String,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Chip(allLabel, active = value.isEmpty(), onClick = { onChange("") })
        categories.forEach { c ->
            val n = counts[c.id] ?: return@forEach
            Chip(c.name, active = value == c.id, onClick = { onChange(c.id) }, count = n.toString())
        }
    }
}

/**
 * Columna vertical de carpetas (categorías) con su cantidad de ítems, para pantallas anchas.
 * La primera entrada (`allLabel`) quita el filtro. Al aparecer, se desplaza hasta la elegida.
 */
@Composable
fun CategoryList(
    categories: List<Category>,
    counts: Map<String, Int>,
    total: Int,
    value: String,
    onChange: (String) -> Unit,
    allLabel: String,
    modifier: Modifier = Modifier,
) {
    val shown = remember(categories, counts) { categories.filter { counts.containsKey(it.id) } }
    val state = rememberLazyListState()
    LaunchedEffect(shown.isNotEmpty()) {
        val index = shown.indexOfFirst { it.id == value }
        if (index > 3) state.scrollToItem(index - 2)
    }
    LazyColumn(
        state = state,
        modifier = modifier.background(Palette.Sidebar),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "\u0000titulo") {
            Text(
                "CATEGORÍAS",
                color = Palette.Dim,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.6.sp,
                modifier = Modifier.padding(start = 10.dp, top = 2.dp, bottom = 8.dp),
            )
        }
        item(key = "") { CategoryRow(allLabel, total, active = value.isEmpty()) { onChange("") } }
        items(shown, key = { it.id }) { c -> CategoryRow(c.name, counts[c.id] ?: 0, active = value == c.id) { onChange(c.id) } }
    }
}

@Composable
private fun CategoryRow(name: String, count: Int, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 42.dp)
            .clip(shape)
            .background(if (active) Palette.Primary.alpha(0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            color = if (active) Palette.Foreground else Palette.Soft,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            count.toString(),
            color = if (active) Palette.Primary else Palette.Dim,
            fontSize = 11.sp,
            fontFamily = AppFonts.Mono,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Selector de categoría a pantalla casi completa, con buscador: para no recorrer la barra de chips. */
@Composable
fun CategoryPickerDialog(
    categories: List<Category>,
    counts: Map<String, Int>,
    value: String,
    allLabel: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val q = normalize(query.trim())
    val shown = remember(categories, q) { if (q.isEmpty()) categories else categories.filter { normalize(it.name).contains(q) } }
    val shape = RoundedCornerShape(18.dp)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .clip(shape)
                .background(Palette.Card)
                .border(1.dp, Palette.Border, shape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Categorías", color = Palette.Foreground, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(shown.size.toString(), color = Palette.Dim, fontSize = 12.sp, fontFamily = AppFonts.Mono, modifier = Modifier.padding(end = 6.dp))
                RoundIconButton(AppIcons.Close, "Cerrar", onDismiss, size = 36.dp)
            }
            SearchField(query, { query = it }, "Buscar categoría…", modifier = Modifier.fillMaxWidth())
            if (shown.isEmpty()) {
                Text("Ninguna categoría coincide.", color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(8.dp))
            } else {
                CategoryCloud(
                    categories = shown,
                    counts = counts,
                    value = value,
                    onChange = onPick,
                    allLabel = allLabel,
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}
