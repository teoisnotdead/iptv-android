package app.iptv.player.player

import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.iptv.player.data.TrackPrefs
import app.iptv.player.domain.languageName
import app.iptv.player.graph
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.RoundIconButton
import app.iptv.player.ui.components.alpha
import app.iptv.player.ui.theme.Palette

/**
 * Video de una [PlaybackSession]. Con `controls`, muestra los controles de Media3 (avance, pausa)
 * y encima una barra propia con volver, título y los botones de **Audio**, **Subtítulos** y
 * **Siguiente**, que aparecen y se ocultan junto con los controles.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    session: PlaybackSession,
    controls: Boolean,
    modifier: Modifier = Modifier,
    title: String = "",
    onBack: (() -> Unit)? = null,
    onNext: (() -> Unit)? = null,
) {
    var controlsVisible by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf<Int?>(null) }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    // Subtítulos y audio se eligen desde la barra propia.
                    setShowSubtitleButton(false)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    keepScreenOn = true
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { controlsVisible = it == View.VISIBLE })
                }
            },
            update = {
                it.useController = controls && session.error == null
                it.player = session.player
            },
            onRelease = { it.player = null },
        )

        if (controls && controlsVisible && session.error == null) {
            val audio = remember(session.tracks) { trackOptions(session.tracks, C.TRACK_TYPE_AUDIO) }
            val text = remember(session.tracks) { trackOptions(session.tracks, C.TRACK_TYPE_TEXT) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.alpha(0.75f), Color.Transparent)))
                    .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onBack != null) RoundIconButton(AppIcons.ChevronLeft, "Volver", onBack, tint = Color.White)
                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = if (onBack == null) 6.dp else 0.dp),
                )
                if (audio.size > 1) BarButton(AppIcons.Audio, "Audio") { picking = C.TRACK_TYPE_AUDIO }
                if (text.isNotEmpty()) BarButton(AppIcons.Subtitles, "Subtítulos") { picking = C.TRACK_TYPE_TEXT }
                if (onNext != null) BarButton(AppIcons.Next, "Siguiente", onNext)
            }
        }

        picking?.let { type -> TrackDialog(session, type) { picking = null } }
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(Color.White.alpha(0.14f))
            .border(1.dp, Color.White.alpha(0.2f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Una pista elegible (audio o subtítulos) con su texto para mostrar. */
private class TrackOption(val group: Tracks.Group, val index: Int, val label: String, val detail: String, val selected: Boolean) {
    val language: String? get() = group.getTrackFormat(index).language
}

private fun trackOptions(tracks: Tracks, type: Int): List<TrackOption> = buildList {
    var n = 0
    for (group in tracks.groups) {
        if (group.type != type) continue
        for (i in 0 until group.length) {
            if (!group.isTrackSupported(i)) continue
            n++
            val f = group.getTrackFormat(i)
            val name = languageName(f.language) ?: f.label ?: if (type == C.TRACK_TYPE_AUDIO) "Audio $n" else "Subtítulos $n"
            val detail = listOfNotNull(
                f.label?.takeIf { it != name },
                if (type == C.TRACK_TYPE_AUDIO) audioCodec(f.sampleMimeType) else textCodec(f.sampleMimeType),
                if (type == C.TRACK_TYPE_AUDIO) channels(f.channelCount) else null,
                "Forzados".takeIf { type == C.TRACK_TYPE_TEXT && f.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
            ).joinToString(" · ")
            add(TrackOption(group, i, name, detail, group.isTrackSelected(i)))
        }
    }
}

private fun audioCodec(mime: String?): String? = when (mime) {
    MimeTypes.AUDIO_AC3 -> "Dolby Digital"
    MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital Plus"
    MimeTypes.AUDIO_TRUEHD -> "Dolby TrueHD"
    MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_HD, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
    MimeTypes.AUDIO_AAC -> "AAC"
    MimeTypes.AUDIO_MPEG -> "MP3"
    MimeTypes.AUDIO_OPUS -> "Opus"
    MimeTypes.AUDIO_FLAC -> "FLAC"
    else -> null
}

private fun textCodec(mime: String?): String? = when (mime) {
    MimeTypes.APPLICATION_SUBRIP -> "SRT"
    MimeTypes.TEXT_SSA -> "ASS"
    MimeTypes.TEXT_VTT -> "WebVTT"
    MimeTypes.APPLICATION_PGS -> "PGS"
    MimeTypes.APPLICATION_DVBSUBS -> "DVB"
    MimeTypes.APPLICATION_VOBSUB -> "VobSub"
    else -> null
}

private fun channels(count: Int): String? = when (count) {
    Format.NO_VALUE, 0 -> null
    1 -> "Mono"
    2 -> "Estéreo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$count canales"
}

/** Lista de pistas de audio o subtítulos. La elección se aplica al momento y se recuerda para los próximos videos. */
@OptIn(UnstableApi::class)
@Composable
private fun TrackDialog(session: PlaybackSession, type: Int, onDismiss: () -> Unit) {
    val prefs = LocalContext.current.graph.trackPrefs
    val player = session.player ?: return onDismiss()
    val options = remember(session.tracks, type) { trackOptions(session.tracks, type) }
    val isText = type == C.TRACK_TYPE_TEXT
    val textOff = isText && options.none { it.selected }

    fun pick(option: TrackOption?) {
        val params = player.trackSelectionParameters.buildUpon()
        if (option == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            prefs.text = TrackPrefs.TEXT_OFF
        } else {
            params.setTrackTypeDisabled(type, false)
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            val language = option.language?.takeIf { it.isNotBlank() && it != "und" }
            if (isText) prefs.text = language ?: prefs.text else prefs.audio = language ?: prefs.audio
        }
        player.trackSelectionParameters = params.build()
        onDismiss()
    }

    Dialog(onDismissRequest = onDismiss) {
        val shape = RoundedCornerShape(18.dp)
        Column(
            modifier = Modifier
                .widthIn(min = 300.dp, max = 460.dp)
                .heightIn(max = 520.dp)
                .clip(shape)
                .background(Palette.Card)
                .border(1.dp, Palette.Border, shape)
                .padding(vertical = 12.dp),
        ) {
            Text(
                if (isText) "Subtítulos" else "Audio",
                color = Palette.Foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                if (isText) OptionRow("Desactivados", "", textOff) { pick(null) }
                options.forEach { option -> OptionRow(option.label, option.detail, option.selected) { pick(option) } }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) Palette.Primary.alpha(0.12f) else Color.Transparent)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = if (selected) Palette.Foreground else Palette.Soft, fontSize = 15.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            if (detail.isNotEmpty()) Text(detail, color = Palette.Dim, fontSize = 12.sp)
        }
        Spacer(Modifier.width(12.dp))
        if (selected) Icon(AppIcons.Check, "Elegido", tint = Palette.Primary, modifier = Modifier.size(20.dp))
    }
}
