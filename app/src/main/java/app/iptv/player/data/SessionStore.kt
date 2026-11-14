package app.iptv.player.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
private data class Saved(val accounts: List<Creds> = emptyList(), val active: Int = 0)

/**
 * Cuentas guardadas en el dispositivo (servidor + usuario + contraseña) y cuál está en uso.
 * Todo va cifrado con una clave del Android Keystore (AES-GCM) que no sale del dispositivo;
 * equivale a la cookie cifrada de la web.
 */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("sesion", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val initial = load()
    private val list = MutableStateFlow(initial.accounts)
    private val current = MutableStateFlow(initial.accounts.getOrNull(initial.active))

    /** Todas las cuentas guardadas. */
    val accounts: StateFlow<List<Creds>> = list

    /** Cuenta en uso; null = no hay ninguna (se muestra el login). */
    val creds: StateFlow<Creds?> = current

    /** Agrega la cuenta (o actualiza su contraseña si ya estaba) y la deja en uso. */
    fun add(creds: Creds) {
        // Volver a ingresar una cuenta sin escribir nombre no borra el que ya tenía.
        val previous = list.value.firstOrNull { it.sameAccount(creds) }
        val saved = if (creds.name.isEmpty() && previous != null) creds.copy(name = previous.name) else creds
        commit(list.value.filterNot { it.sameAccount(creds) } + saved, saved)
    }

    /** Cambia el nombre personalizado de una cuenta ("" lo quita). */
    fun rename(creds: Creds, name: String) {
        val renamed = list.value.map { if (it.sameAccount(creds)) it.copy(name = name.trim()) else it }
        commit(renamed, current.value?.let { active -> renamed.firstOrNull { it.sameAccount(active) } })
    }

    fun select(creds: Creds) {
        val found = list.value.firstOrNull { it.sameAccount(creds) } ?: return
        commit(list.value, found)
    }

    /** Quita la cuenta. Si era la que estaba en uso, pasa a otra (o al login si no quedan). */
    fun remove(creds: Creds) {
        val rest = list.value.filterNot { it.sameAccount(creds) }
        val active = current.value?.takeUnless { it.sameAccount(creds) } ?: rest.firstOrNull()
        commit(rest, active)
    }

    private fun commit(accounts: List<Creds>, active: Creds?) {
        val saved = Saved(accounts, accounts.indexOfFirst { active != null && it.sameAccount(active) }.coerceAtLeast(0))
        val sealed = runCatching { encrypt(json.encodeToString(Saved.serializer(), saved)) }.getOrNull()
        prefs.edit().apply { if (sealed != null && accounts.isNotEmpty()) putString(KEY, sealed) else remove(KEY) }.apply()
        list.value = accounts
        current.value = active
    }

    private fun load(): Saved {
        val sealed = prefs.getString(KEY, null) ?: return Saved()
        return runCatching { json.decodeFromString(Saved.serializer(), decrypt(sealed)) }.getOrDefault(Saved())
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply { init(spec) }.generateKey()
    }

    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + data, Base64.NO_WRAP)
    }

    private fun decrypt(sealed: String): String {
        val raw = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, IV_SIZE))
        return String(cipher.doFinal(raw, IV_SIZE, raw.size - IV_SIZE), Charsets.UTF_8)
    }

    private companion object {
        const val KEY = "cuentas"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "iptv_sesion"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
