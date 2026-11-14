package app.iptv.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.data.PanelException
import app.iptv.player.ui.theme.Palette
import java.util.Locale

/** Resultado de una carga asíncrona: datos, error apto para mostrar y acción para reintentar. */
class Loaded<T>(val data: T?, val error: String?, val reload: () -> Unit) {
    val loading: Boolean get() = data == null && error == null
}

/** Ejecuta `load` al entrar y cada vez que cambia `key` o se reintenta. */
@Composable
fun <T> rememberLoaded(key: Any? = Unit, load: suspend () -> T): Loaded<T> {
    var attempt by remember { mutableIntStateOf(0) }
    var data by remember(key) { mutableStateOf<T?>(null) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    LaunchedEffect(key, attempt) {
        error = null
        try {
            data = load()
        } catch (e: PanelException) {
            error = e.message ?: "Error al cargar"
        }
    }
    return Loaded(data, error) { attempt++ }
}

private val CL = Locale("es", "CL")

/** "1.234 canales" / "1 canal". */
fun countLabel(n: Int, unit: String, unitOne: String): String = String.format(CL, "%,d", n) + " " + if (n == 1) unitOne else unit

fun Color.alpha(a: Float): Color = copy(alpha = a)

@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(50.dp)
            .clip(shape)
            .background(Palette.Card)
            .border(1.dp, if (focused) Palette.Primary else Palette.Border, shape)
            .padding(start = 16.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Search, null, tint = if (focused) Palette.Primary else Palette.Muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, color = Palette.Muted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Palette.Foreground, fontSize = 15.sp),
                cursorBrush = SolidColor(Palette.Primary),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth()
                    .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                    .onFocusChanged { focused = it.isFocused },
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(Palette.Border).clickable { onValueChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Close, "Borrar búsqueda", tint = Palette.Soft, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Píldora de categoría con contador opcional. */
@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit, count: String? = null, icon: ImageVector? = null, iconTint: Color = Palette.Soft) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .height(36.dp)
            .widthIn(max = 240.dp)
            .clip(shape)
            .background(if (active) Palette.Primary.alpha(0.14f) else Palette.Secondary)
            .border(1.dp, if (active) Palette.Primary.alpha(0.4f) else Palette.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
        Text(
            label,
            color = if (active) Palette.Foreground else Palette.Soft,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (count != null) Text(count, color = Palette.Primary, fontSize = 12.sp, fontFamily = AppFonts.Mono)
    }
}

/** Botón junto al buscador que deja solo los favoritos. */
@Composable
fun FavoritesToggle(active: Boolean, count: Int, onToggle: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .height(50.dp)
            .clip(shape)
            .background(if (active) Palette.Warning.alpha(0.12f) else Palette.Card)
            .border(1.dp, if (active) Palette.Warning.alpha(0.5f) else Palette.Border, shape)
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (active) AppIcons.Star else AppIcons.StarOutline,
            if (active) "Mostrar todo" else "Solo favoritos",
            tint = if (active) Palette.Warning else Palette.Soft,
            modifier = Modifier.size(18.dp),
        )
        if (count > 0) Text(count.toString(), color = if (active) Palette.Warning else Palette.Soft, fontSize = 13.sp, fontFamily = AppFonts.Mono)
    }
}

enum class TagTone { NEUTRAL, SUCCESS, PRIMARY }

@Composable
fun Tag(text: String, tone: TagTone = TagTone.NEUTRAL) {
    val (bg, fg) = when (tone) {
        TagTone.NEUTRAL -> Palette.Secondary to Palette.Soft
        TagTone.SUCCESS -> Palette.Success.alpha(0.14f) to Palette.Success
        TagTone.PRIMARY -> Palette.Primary to Palette.OnPrimary
    }
    Text(
        text,
        color = fg,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = AppFonts.Mono,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    detail: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp))
                .background(if (error) Palette.Destructive.alpha(0.12f) else Palette.Secondary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (error) Palette.Destructive else Palette.Muted, modifier = Modifier.size(26.dp))
        }
        Text(title, color = Palette.Foreground, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(text, color = Palette.Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 380.dp))
        if (detail != null) Text(detail, color = Palette.Dim, fontSize = 12.sp, fontFamily = AppFonts.Mono, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = onRetry) {
                Icon(AppIcons.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Reintentar")
            }
        }
    }
}

/** Botón redondo de ícono (40dp por defecto). */
@Composable
fun RoundIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Palette.Soft,
    size: Dp = 40.dp,
    background: Color = Color.Transparent,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(background).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = if (enabled) tint else Palette.Dim.alpha(0.5f), modifier = Modifier.size(size * 0.48f))
    }
}
