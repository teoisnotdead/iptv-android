package app.iptv.player.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Motivo por el que no se pudo entrar; la pantalla de login elige el mensaje. */
enum class LoginError { BAD_REQUEST, BAD_URL, UNREACHABLE, INVALID, EXPIRED, DISABLED, INACTIVE }

sealed interface LoginResult {
    data class Ok(val creds: Creds, val info: UserInfo) : LoginResult
    data class Fail(val error: LoginError, val info: UserInfo? = null) : LoginResult
}

/** Estado de una cuenta ya autenticada; null si está activa. */
fun accountProblem(info: UserInfo): LoginError? = when {
    info.status.isEmpty() || info.status == "Active" -> null
    info.status == "Expired" -> LoginError.EXPIRED
    info.status == "Banned" || info.status == "Disabled" -> LoginError.DISABLED
    else -> LoginError.INACTIVE
}

/** Valida servidor, usuario y contraseña contra el panel: exige `auth == 1` y estado "Active". */
suspend fun XtreamApi.login(url: String, username: String, password: String, name: String = ""): LoginResult {
    val creds = Creds(normalizePanelUrl(url), username.trim(), password.trim(), name.trim())
    if (creds.username.isEmpty() || creds.password.isEmpty()) return LoginResult.Fail(LoginError.BAD_REQUEST)
    if (creds.url.toHttpUrlOrNull() == null) return LoginResult.Fail(LoginError.BAD_URL)

    val info = try {
        account(creds)
    } catch (e: PanelException) {
        return LoginResult.Fail(LoginError.UNREACHABLE)
    }
    if (info.auth != 1) return LoginResult.Fail(LoginError.INVALID)
    accountProblem(info)?.let { return LoginResult.Fail(it, info) }
    return LoginResult.Ok(creds, info)
}
