package app.iptv.player.domain

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.abs
import kotlin.math.ceil

/** Utilidades puras de formato de texto y fechas (mismas reglas que `lib/format.ts` de la web). */

private val LOCALE = Locale("es", "CL")

/** El panel entrega títulos y descripciones de la guía en base64. */
@OptIn(ExperimentalEncodingApi::class)
fun b64(s: String?): String {
    if (s.isNullOrEmpty()) return ""
    return try {
        Base64.decode(s).toString(Charsets.UTF_8)
    } catch (e: IllegalArgumentException) {
        s
    }
}

/** Títulos de relleno que mandan algunos paneles cuando en realidad no tienen guía. */
private val EPG_PLACEHOLDER = Regex("^(sin informaci[oó]n|no information|n/a|-*)$", RegexOption.IGNORE_CASE)

/** Título de un programa de la guía, o "" si falta o es de relleno ("Sin información"). */
fun epgTitle(rawTitle: String?): String {
    val title = b64(rawTitle).trim()
    return if (EPG_PLACEHOLDER.matches(title)) "" else title
}

private fun seconds(ts: String?): Long? = ts?.trim()?.toLongOrNull()?.takeIf { it > 0 }

fun hhmm(ts: String?): String {
    val s = seconds(ts) ?: return ""
    return SimpleDateFormat("HH:mm", LOCALE).format(Date(s * 1000))
}

fun formatDate(ts: String?): String {
    val s = seconds(ts) ?: return ""
    return SimpleDateFormat("dd-MM-yyyy", LOCALE).format(Date(s * 1000))
}

/** Días que faltan para el vencimiento (negativo si ya venció). */
fun daysLeft(ts: String?, nowMillis: Long = System.currentTimeMillis()): Int? {
    val s = ts?.trim()?.toLongOrNull()?.takeIf { it != 0L } ?: return null
    return ceil((s * 1000 - nowMillis) / 86_400_000.0).toInt()
}

private val MARKS = Regex("\\p{Mn}+")

/** Minúsculas y sin tildes, para búsquedas. */
fun normalize(s: String): String = MARKS.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

/** Rating válido como texto con un decimal, o null si falta o es 0. */
fun formatRating(r: String?): String? {
    val n = r?.trim()?.toDoubleOrNull() ?: return null
    return if (n.isFinite() && n > 0) String.format(Locale.US, "%.1f", n) else null
}

private val CLOCK = Regex("^(\\d{1,2}):(\\d{2})(?::\\d{2})?$")
private val DIGITS = Regex("^\\d+$")

/** "01:52:00" | "112" (min) | "1h 52m" → "1 h 52 min". Si no se entiende, lo deja igual. */
fun formatDuration(d: String?): String? {
    val s = d?.trim().orEmpty()
    if (s.isEmpty()) return null
    val clock = CLOCK.find(s)
    val minutes = when {
        clock != null -> clock.groupValues[1].toInt() * 60 + clock.groupValues[2].toInt()
        DIGITS.matches(s) -> s.toIntOrNull() ?: return s
        else -> return s
    }
    if (minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m min"
        m == 0 -> "$h h"
        else -> "$h h ${m.toString().padStart(2, '0')} min"
    }
}

private val QUALITY_TAG = Regex("^(F?HD|SD|4K|UHD|HQ|E|P|LT|ENC|MULTI[- ]?AUDIO|\\d+\\s?mb|\\d+\\s?hrs?)$", RegexOption.IGNORE_CASE)
private val PUNCT = Regex("[.()*]")
private val COUNTRY = Regex("^[A-Z]{2,3}$")
private val SPACES = Regex("\\s+")

/** Iniciales de un canal a partir del nombre "limpio" (sin país ni calidad). */
fun initials(name: String): String {
    val segments = name.split("|")
        .map { PUNCT.replace(it, "").trim() }
        .filter { it.length > 1 && !QUALITY_TAG.matches(it) }
    val useful = if (segments.size > 1 && COUNTRY.matches(segments[0])) segments.drop(1) else segments
    val words = (useful.firstOrNull() ?: name).split(SPACES).filter { it.isNotEmpty() }
    if (words.size >= 2) return (words[0].take(1) + words[1].take(1)).uppercase()
    return (words.firstOrNull() ?: "?").take(2).uppercase()
}

private val HUES = intArrayOf(285, 300, 262, 318, 248, 276, 305, 292)

/** Tono estable por título, para que un mismo póster sin imagen tenga siempre el mismo color. */
fun hueOf(name: String): Int {
    var h = 0
    for (c in name) h = h * 31 + c.code
    return HUES[abs(h % HUES.size)]
}

/** Tiempo de reproducción: "1:02:13" o "45:10". */
fun clock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val mm = m.toString().padStart(2, '0')
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:$mm:$ss" else "$m:$ss"
}

/** Nombre del idioma en español a partir de su código ("spa", "es", "en-US"…), o null si no se reconoce. */
fun languageName(code: String?): String? {
    val tag = code?.trim()?.takeIf { it.isNotEmpty() && it != "und" && it != "unknown" } ?: return null
    val locale = Locale.forLanguageTag(tag.replace('_', '-'))
    val name = locale.getDisplayLanguage(LOCALE)
    if (name.isBlank() || name.equals(tag, ignoreCase = true)) return null
    return name.replaceFirstChar { it.titlecase(LOCALE) }
}

fun splitList(s: String?): List<String> = s.orEmpty().split(',', '/').map { it.trim() }.filter { it.isNotEmpty() }

private val HTTP = Regex("^https?://", RegexOption.IGNORE_CASE)

fun youtubeUrl(idOrUrl: String?): String? {
    if (idOrUrl.isNullOrEmpty()) return null
    return if (HTTP.containsMatchIn(idOrUrl)) idOrUrl else "https://www.youtube.com/watch?v=$idOrUrl"
}
