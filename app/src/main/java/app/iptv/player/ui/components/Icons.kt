package app.iptv.player.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Íconos de la web (`components/ui/Icon.tsx`): mismos trazos, en un lienzo de 24×24. */
object AppIcons {
    private fun icon(name: String, path: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = PathParser().parsePathString(path).toNodes(),
                fill = if (filled) SolidColor(Color.Black) else null,
                stroke = if (filled) null else SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()

    private const val STAR_PATH = "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1L3.2 9.5l6.1-.9z"

    val Play = icon("play", "M7 4.5v15l13-7.5z", filled = true)
    val Star = icon("star", STAR_PATH, filled = true)
    val StarOutline = icon("starOutline", STAR_PATH)
    val Search = icon("search", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0M20 20l-3.5-3.5")
    val SearchOff = icon("searchOff", "M4 11a7 7 0 1 0 14 0a7 7 0 1 0 -14 0M20 20l-3.5-3.5M8.5 11h5")
    val Close = icon("close", "M6 6l12 12M18 6L6 18")
    val ChevronLeft = icon("chevronLeft", "M15 6l-6 6 6 6")
    val ChevronRight = icon("chevronRight", "M9 6l6 6-6 6")
    val Expand = icon("expand", "M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7")
    val Logout = icon("logout", "M15 4h4v16h-4M10 8l-4 4 4 4M6 12h10")
    val Tv = icon("tv", "M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2zM8 21h8")
    val Film = icon("film", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2zM7 3v18M17 3v18M3 12h18")
    val Layers = icon("layers", "M12 3l9 5-9 5-9-5zM3 13l9 5 9-5")
    val User = icon("user", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0M4 21c0-4 4-6 8-6s8 2 8 6")
    val Refresh = icon("refresh", "M20 12a8 8 0 1 1 -2.3 -5.7M20 4v5h-5")
    val Warning = icon("warning", "M12 3l9.5 17h-19zM12 10v4M12 17v.5")
    val Grid = icon(
        "grid",
        "M5 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1z" +
            "M15 4h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1V5a1 1 0 0 1 1 -1z" +
            "M5 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1H5a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z" +
            "M15 14h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1v-4a1 1 0 0 1 1 -1z",
    )
    val Audio = icon("audio", "M4 9v6h4l5 4V5L8 9zM16.5 8.5a5 5 0 0 1 0 7M19 6a8.5 8.5 0 0 1 0 12")
    val Subtitles = icon("subtitles", "M5 5h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2H5a2 2 0 0 1 -2 -2V7a2 2 0 0 1 2 -2zM7 15h4M13 15h4M7 11h10")
    val Next = icon("next", "M6 5.5v13l9.5-6.5zM18 5v14")
    val Plus = icon("plus", "M12 5v14M5 12h14")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Trailer = icon("trailer", "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1 -3 3H6a3 3 0 0 1 -3 -3V8a3 3 0 0 1 3 -3zM10 9.5v5l4-2.5z")
}
