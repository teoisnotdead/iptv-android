package app.iptv.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import app.iptv.player.BuildConfig
import app.iptv.player.R
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.ui.theme.Palette

/** Símbolo de Lytra TV (`res/drawable/brand_mark.xml`), en el color de marca. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.brand_mark), contentDescription = null, modifier = modifier)
}

private val LYTRA = Regex("^lytra(?:\\s+(.+))?$", RegexOption.IGNORE_CASE)

/**
 * La firma de la marca, como en Lytra: "lytr" en Geist Semibold con tracking -0.045em, su "a"
 * dibujada (un anillo y un asta) y el resto del nombre como etiqueta violeta ("TV"). Otro nombre
 * (el de un revendedor) se escribe tal cual.
 */
@Composable
fun Wordmark(fontSize: TextUnit, modifier: Modifier = Modifier) {
    val name = BuildConfig.SITE_NAME.trim()
    val match = LYTRA.matchEntire(name)
    if (match == null) {
        Text(
            name, modifier,
            color = Palette.Foreground, fontFamily = AppFonts.Sans, fontWeight = FontWeight.SemiBold,
            fontSize = fontSize, lineHeight = fontSize, letterSpacing = (-0.03).em, maxLines = 1, softWrap = false,
        )
        return
    }
    val tag = match.groupValues[1].uppercase()
    val word = buildAnnotatedString {
        append("lytr")
        appendInlineContent("a", "a")
    }
    // La "a" mide 0.52em de ancho y se apoya en la línea base: 52 × 80 unidades de 1/100 em
    // (el SVG de Lytra es "0 -80 52 100", y la letra ocupa de -53 a 0).
    val a = mapOf(
        "a" to InlineTextContent(Placeholder(0.52.em, 0.8.em, PlaceholderVerticalAlign.AboveBaseline)) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size.width / 52f
                drawCircle(Palette.Foreground, radius = 21.25f * u, center = Offset(24f * u, 53.5f * u), style = Stroke(width = 10.5f * u))
                drawRect(Palette.Foreground, topLeft = Offset(41.5f * u, 27f * u), size = Size(10.5f * u, 53f * u))
            }
        },
    )
    val gap = with(LocalDensity.current) { (fontSize * 0.32f).toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            word, inlineContent = a,
            color = Palette.Foreground, fontFamily = AppFonts.Sans, fontWeight = FontWeight.SemiBold,
            fontSize = fontSize, lineHeight = fontSize, letterSpacing = (-0.045).em, maxLines = 1, softWrap = false,
        )
        if (tag.isNotEmpty()) {
            Text(
                tag, Modifier.padding(start = gap),
                color = Palette.Primary, fontFamily = AppFonts.Sans, fontWeight = FontWeight.Bold,
                fontSize = fontSize * 0.5f, lineHeight = fontSize * 0.5f, letterSpacing = 0.18.em, maxLines = 1, softWrap = false,
            )
        }
    }
}
