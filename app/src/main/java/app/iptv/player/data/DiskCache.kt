package app.iptv.player.data

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.security.MessageDigest

/**
 * Copia del catálogo en el almacenamiento interno, una carpeta por cuenta.
 * Así la app abre al instante sin volver a descargar miles de canales y películas,
 * y sigue mostrando el catálogo aunque el panel no responda.
 * Nunca guarda credenciales: el nombre de la carpeta es un hash de servidor + usuario.
 */
class DiskCache(context: Context) {
    private val root = File(context.filesDir, "catalogo")
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    class Entry<T>(val value: T, val savedAt: Long)

    private fun dir(creds: Creds): File {
        val digest = MessageDigest.getInstance("SHA-256").digest((creds.url.lowercase() + "\n" + creds.username).toByteArray())
        return File(root, digest.take(12).joinToString("") { "%02x".format(it) })
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> read(creds: Creds, key: String, serializer: KSerializer<T>): Entry<T>? {
        val file = File(dir(creds), "$key.json")
        if (!file.isFile) return null
        return runCatching { Entry(file.inputStream().buffered().use { json.decodeFromStream(serializer, it) }, file.lastModified()) }
            .getOrElse {
                file.delete() // archivo dañado o de una versión anterior: se vuelve a descargar
                null
            }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun <T> write(creds: Creds, key: String, serializer: KSerializer<T>, value: T) {
        val dir = dir(creds).apply { mkdirs() }
        // Se escribe en un temporal y se renombra, para no dejar un archivo a medias si la app se cierra.
        val tmp = File(dir, "$key.json.tmp")
        runCatching {
            tmp.outputStream().buffered().use { json.encodeToStream(serializer, value, it) }
            tmp.renameTo(File(dir, "$key.json"))
        }.onFailure { tmp.delete() }
    }

    /** Fecha de la copia más antigua de la cuenta (0 si no hay ninguna). */
    fun savedAt(creds: Creds): Long =
        dir(creds).listFiles { f -> f.name.endsWith(".json") }?.minOfOrNull { it.lastModified() } ?: 0L

    fun clear(creds: Creds) {
        dir(creds).deleteRecursively()
    }
}
