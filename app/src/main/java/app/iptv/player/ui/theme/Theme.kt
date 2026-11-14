package app.iptv.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.iptv.player.BuildConfig
import app.iptv.player.R

/** Base oscura de la web (`globals.css`). No es configurable; solo cambia el color de marca. */
object Palette {
    val Background = Color(0xFF0F1115)
    val Sidebar = Color(0xFF0C0E12)
    val Card = Color(0xFF161920)
    val Secondary = Color(0xFF1D212B)
    val Foreground = Color(0xFFE6E8EE)
    val Soft = Color(0xFFA3AABA)
    val Muted = Color(0xFF8B93A7)
    val Dim = Color(0xFF6B7386)
    val Border = Color(0xFF262B37)
    val Destructive = Color(0xFFF08282)
    val Success = Color(0xFF78D296)
    val Warning = Color(0xFFF0B45A)

    /** Color de marca de esta instalación (`brand.color` en local.properties). */
    val Primary: Color = runCatching { Color(android.graphics.Color.parseColor(BuildConfig.BRAND_COLOR)) }
        .getOrDefault(Color(0xFFA78BFA))
    val OnPrimary: Color = if (Primary.luminance() > 0.3f) Color(0xFF120E1F) else Color.White
}

private val Scheme = darkColorScheme(
    primary = Palette.Primary,
    onPrimary = Palette.OnPrimary,
    background = Palette.Background,
    onBackground = Palette.Foreground,
    surface = Palette.Background,
    onSurface = Palette.Foreground,
    surfaceVariant = Palette.Card,
    onSurfaceVariant = Palette.Soft,
    surfaceContainer = Palette.Card,
    surfaceContainerHigh = Palette.Secondary,
    surfaceContainerHighest = Palette.Secondary,
    secondaryContainer = Palette.Secondary,
    onSecondaryContainer = Palette.Foreground,
    outline = Palette.Border,
    outlineVariant = Palette.Border,
    error = Palette.Destructive,
)

/** Fuentes de la web: Geist (texto), Geist Mono (números y códigos) e Instrument Serif (acentos). */
object AppFonts {
    val Sans = FontFamily(
        Font(R.font.geist_regular, FontWeight.Normal),
        Font(R.font.geist_medium, FontWeight.Medium),
        Font(R.font.geist_semibold, FontWeight.SemiBold),
        Font(R.font.geist_bold, FontWeight.Bold),
    )
    val Mono = FontFamily(
        Font(R.font.geist_mono_regular, FontWeight.Normal),
        Font(R.font.geist_mono_medium, FontWeight.Medium),
        Font(R.font.geist_mono_medium, FontWeight.SemiBold),
    )
    val Serif = FontFamily(
        Font(R.font.instrument_serif_regular, FontWeight.Normal),
        Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
    )
}

/** Toda la tipografía de Material usa Geist, así cualquier `Text` sin estilo propio sale con la fuente de la web. */
private val Typography = Typography().let { base ->
    fun TextStyle.sans() = copy(fontFamily = AppFonts.Sans)
    base.copy(
        displayLarge = base.displayLarge.sans(),
        displayMedium = base.displayMedium.sans(),
        displaySmall = base.displaySmall.sans(),
        headlineLarge = base.headlineLarge.sans(),
        headlineMedium = base.headlineMedium.sans(),
        headlineSmall = base.headlineSmall.sans(),
        titleLarge = base.titleLarge.sans(),
        titleMedium = base.titleMedium.sans(),
        titleSmall = base.titleSmall.sans(),
        bodyLarge = base.bodyLarge.sans(),
        bodyMedium = base.bodyMedium.sans(),
        bodySmall = base.bodySmall.sans(),
        labelLarge = base.labelLarge.sans(),
        labelMedium = base.labelMedium.sans(),
        labelSmall = base.labelSmall.sans(),
    )
}

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = Typography, content = content)
}
