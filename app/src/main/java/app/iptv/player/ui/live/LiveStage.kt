package app.iptv.player.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.iptv.player.data.EpgItem
import app.iptv.player.data.LiveStream
import app.iptv.player.domain.b64
import app.iptv.player.domain.epgTitle
import app.iptv.player.domain.hhmm
import app.iptv.player.player.PlaybackSession
import app.iptv.player.player.VideoSurface
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.ChannelLogo
import app.iptv.player.ui.components.RoundIconButton
import app.iptv.player.ui.components.Tag
import app.iptv.player.ui.components.TagTone
import app.iptv.player.ui.components.alpha
import app.iptv.player.ui.theme.Palette
import kotlinx.coroutines.delay

/** Mantiene la reproducción ligada a la pantalla: arranca al mostrarse y suelta la conexión al ocultarse. */
@Composable
fun SessionLifecycle(session: PlaybackSession?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(session, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> session?.start()
                Lifecycle.Event.ON_STOP -> session?.release()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) session?.start()
        onDispose {
            lifecycle.removeObserver(observer)
            session?.close()
        }
    }
}

/**
 * Video de una reproducción en vivo. Sirve de previsualizador (junto a la lista, sin controles:
 * un toque lo amplía), de pantalla completa (con controles, audio y subtítulos) y de ventana
 * flotante (picture-in-picture, sin nada encima). Todos muestran la *misma* reproducción, así que
 * cambiar entre ellos no vuelve a cargar el canal ni abre otra conexión.
 */
@Composable
fun LiveVideo(
    session: PlaybackSession,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "",
    pip: Boolean = false,
) {
    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        VideoSurface(
            session = session,
            controls = fullscreen && !pip,
            title = title,
            onBack = onToggleFullscreen,
            modifier = Modifier.fillMaxSize(),
        )
        if (pip) return@Box
        val message = session.error
        if (message != null) {
            Column(
                modifier = Modifier.fillMaxSize().background(Color(0xCC000000)).padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("No se pudo reproducir el canal", color = Palette.Foreground, fontWeight = FontWeight.SemiBold)
                Text(message, color = Palette.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                Button(onClick = { session.retry() }) { Text("Reintentar") }
            }
        } else if (session.player == null) {
            CircularProgressIndicator(color = Palette.Primary)
        }
        if (!fullscreen && message == null) {
            // Capa clicable sobre el video: toda la superficie lo amplía.
            Box(modifier = Modifier.fillMaxSize().clickable(onClick = onToggleFullscreen)) {
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).size(38.dp).clip(CircleShape)
                        .background(Palette.Background.alpha(0.7f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Expand, "Pantalla completa", tint = Palette.Foreground, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Logo, nombre, categoría y modo (HLS/TS) del canal, con favorito, anterior/siguiente y cerrar. */
@Composable
fun ChannelInfo(
    channel: LiveStream,
    categoryName: String?,
    /** Modo en uso: true = MPEG-TS (elegido, o porque HLS no funcionó). */
    ts: Boolean,
    onEngineChange: (ts: Boolean) -> Unit,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        val shape = RoundedCornerShape(10.dp)
        ChannelLogo(
            channel.name,
            channel.icon,
            modifier = Modifier.size(44.dp).clip(shape)
                .background(Brush.linearGradient(listOf(Palette.Secondary, Palette.Sidebar)))
                .border(1.dp, Palette.Border, shape),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(channel.name, color = Palette.Foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EngineSwitch(ts, onEngineChange)
                if (!categoryName.isNullOrEmpty()) {
                    Text(
                        categoryName.uppercase(),
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
        RoundIconButton(
            if (favorite) AppIcons.Star else AppIcons.StarOutline,
            if (favorite) "Quitar de favoritos" else "Agregar a favoritos",
            onToggleFavorite,
            tint = if (favorite) Palette.Warning else Palette.Soft,
        )
        RoundIconButton(AppIcons.ChevronLeft, "Canal anterior", { onPrev?.invoke() }, enabled = onPrev != null)
        RoundIconButton(AppIcons.ChevronRight, "Canal siguiente", { onNext?.invoke() }, enabled = onNext != null)
        RoundIconButton(AppIcons.Close, "Cerrar reproductor", onClose)
    }
}

/**
 * Píldoras HLS / TS, como en la web: marcan el modo en uso y permiten cambiarlo.
 * HLS es el recomendado (si falla pasa solo a TS); TS sirve cuando el canal se corta o se congela.
 */
@Composable
private fun EngineSwitch(ts: Boolean, onChange: (ts: Boolean) -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier.clip(shape).background(Palette.Secondary).border(1.dp, Palette.Border, shape).padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        listOf(false to "HLS", true to "TS").forEach { (value, label) ->
            val active = ts == value
            Text(
                label,
                color = if (active) Palette.OnPrimary else Palette.Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = AppFonts.Mono,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) Palette.Primary else Color.Transparent)
                    .clickable(enabled = !active) { onChange(value) }
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Programa actual (con progreso) y el siguiente.
 * La mayoría de los canales no trae guía (o trae "Sin información"): en ese caso no ocupa espacio.
 */
@Composable
fun NowNext(items: List<EpgItem>, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = System.currentTimeMillis() / 1000
        }
    }
    val index = items.indexOfFirst { (it.start.toLongOrNull() ?: Long.MAX_VALUE) <= now && now < (it.stop.toLongOrNull() ?: 0) }
    val current = items.getOrNull(index) ?: return
    val title = epgTitle(current.title)
    if (title.isEmpty()) return

    val start = current.start.toLong()
    val stop = current.stop.toLong()
    val progress = ((now - start).toFloat() / (stop - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    val minutesLeft = ((stop - now + 30) / 60).coerceAtLeast(1)
    val description = b64(current.description).trim()
    val next = items.getOrNull(index + 1)
    val nextTitle = epgTitle(next?.title)

    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.fillMaxWidth().clip(shape).background(Palette.Card).border(1.dp, Palette.Border, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag("AHORA", TagTone.SUCCESS)
            Text("${hhmm(current.start)} – ${hhmm(current.stop)}", color = Palette.Muted, fontSize = 12.sp, fontFamily = AppFonts.Mono, maxLines = 1)
            Text(title, color = Palette.Foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("quedan $minutesLeft min", color = Palette.Dim, fontSize = 12.sp, maxLines = 1)
        }
        Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Palette.Secondary)) {
            Box(modifier = Modifier.fillMaxWidth(progress).height(4.dp).clip(CircleShape).background(Palette.Primary))
        }
        if (description.isNotEmpty()) Text(description, color = Palette.Soft, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (nextTitle.isNotEmpty() && next != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("DESPUÉS")
                Text(hhmm(next.start), color = Palette.Muted, fontSize = 12.sp, fontFamily = AppFonts.Mono)
                Text(nextTitle, color = Palette.Soft, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
