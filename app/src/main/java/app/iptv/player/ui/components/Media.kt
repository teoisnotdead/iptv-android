package app.iptv.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.data.LiveStream
import app.iptv.player.domain.PosterItem
import app.iptv.player.domain.hueOf
import app.iptv.player.domain.initials
import app.iptv.player.ui.theme.Palette
import coil.compose.AsyncImage

/** Logo de un canal; si no hay imagen o no carga, muestra sus iniciales. */
@Composable
fun ChannelLogo(name: String, src: String, modifier: Modifier = Modifier) {
    var failed by remember(src) { mutableStateOf(false) }
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val side = min(maxWidth, maxHeight)
        if (src.isNotEmpty() && !failed) {
            AsyncImage(
                model = src,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize().padding(side * 0.14f),
            )
        } else {
            val box = min(side * 0.72f, 64.dp)
            val shape = RoundedCornerShape(box * 0.25f)
            Box(
                modifier = Modifier.size(box).clip(shape)
                    .background(Brush.verticalGradient(listOf(Palette.Primary.alpha(0.2f), Palette.Primary.alpha(0.05f))))
                    .border(1.dp, Palette.Primary.alpha(0.2f), shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(initials(name), color = Palette.Primary, fontWeight = FontWeight.Bold, fontSize = (box.value * 0.34f).sp, maxLines = 1)
            }
        }
    }
}

/** Tarjeta horizontal de canal: recuadro con el logo, nombre, categoría y estrella de favorito. */
@Composable
fun ChannelCard(
    channel: LiveStream,
    category: String?,
    active: Boolean,
    favorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
    // En columnas angostas el logo se achica para dejarle espacio al nombre.
    val logoWidth = if (maxWidth < 340.dp) 64.dp else 86.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(shape)
            .background(if (active) Palette.Primary.alpha(0.08f) else Palette.Card)
            .border(if (active) 1.5.dp else 1.dp, if (active) Palette.Primary else Palette.Border, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f).fillMaxHeight().clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(
                channel.name,
                channel.icon,
                modifier = Modifier.width(logoWidth).fillMaxHeight()
                    .background(Brush.linearGradient(listOf(Palette.Secondary, Palette.Sidebar))),
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    channel.name,
                    color = Palette.Foreground,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (active || !category.isNullOrEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (active) Tag("▶ VIENDO", TagTone.PRIMARY)
                        if (!category.isNullOrEmpty()) {
                            Text(
                                category.uppercase(),
                                color = Palette.Dim,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        RoundIconButton(
            icon = if (favorite) AppIcons.Star else AppIcons.StarOutline,
            label = if (favorite) "Quitar de favoritos" else "Agregar a favoritos",
            onClick = onToggleFavorite,
            tint = if (favorite) Palette.Warning else Palette.Dim,
            size = 38.dp,
            modifier = Modifier.padding(end = 6.dp),
        )
    }
    }
}

/** Fondo degradado estable por título, para pósters sin imagen. */
private fun placeholderBrush(name: String): Brush {
    val h = hueOf(name).toFloat()
    return Brush.linearGradient(listOf(Color.hsl(h, 0.42f, 0.36f), Color.hsl((h + 18f) % 360f, 0.34f, 0.2f), Palette.Card))
}

/** Imagen de póster (o fondo); si falta o no carga, un degradado con el título. */
@Composable
fun PosterArt(name: String, src: String?, modifier: Modifier = Modifier, showName: Boolean = true) {
    var failed by remember(src) { mutableStateOf(false) }
    Box(modifier = modifier.background(placeholderBrush(name)), contentAlignment = Alignment.Center) {
        if (!src.isNullOrEmpty() && !failed) {
            AsyncImage(
                model = src,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (showName) {
            Text(
                name,
                color = Palette.Foreground.alpha(0.9f),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}

/** Póster de película o serie con rating, marca de favorito y título debajo. */
@Composable
fun PosterCard(item: PosterItem, favorite: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier.fillMaxWidth().clickable(onClick = onOpen), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(shape).border(1.dp, Palette.Border, shape)) {
            PosterArt(item.name, item.image, modifier = Modifier.fillMaxSize())
            if (item.rating != null) {
                Row(
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(RoundedCornerShape(7.dp))
                        .background(Palette.Background.alpha(0.8f)).padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(AppIcons.Star, null, tint = Palette.Warning, modifier = Modifier.size(10.dp))
                    Text(item.rating, color = Palette.Foreground, fontSize = 11.sp, fontFamily = AppFonts.Mono)
                }
            }
            if (favorite) {
                Box(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp).size(26.dp).clip(CircleShape)
                        .background(Palette.Background.alpha(0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Star, "Favorito", tint = Palette.Warning, modifier = Modifier.size(14.dp))
                }
            }
        }
        Text(item.name, color = Palette.Soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
