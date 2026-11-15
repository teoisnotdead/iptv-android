package app.iptv.player.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.iptv.player.AppGraph
import app.iptv.player.BuildConfig
import app.iptv.player.data.LoginError
import app.iptv.player.data.LoginResult
import app.iptv.player.data.login
import app.iptv.player.domain.formatDate
import app.iptv.player.ui.components.BrandMark
import app.iptv.player.ui.components.Wordmark
import app.iptv.player.ui.theme.Palette
import kotlinx.coroutines.launch

private fun message(fail: LoginResult.Fail): String = when (fail.error) {
    LoginError.BAD_REQUEST -> "Ingresa usuario y contraseña"
    LoginError.BAD_URL -> "La dirección del servidor no es válida"
    LoginError.UNREACHABLE -> "No se pudo contactar al servidor. Intenta más tarde."
    LoginError.INVALID -> "Usuario o contraseña incorrectos"
    LoginError.EXPIRED -> {
        val date = formatDate(fail.info?.expDate)
        if (date.isEmpty()) "Tu suscripción está vencida" else "Tu suscripción venció el $date"
    }
    LoginError.DISABLED -> "Tu cuenta está deshabilitada"
    LoginError.INACTIVE -> "Cuenta no activa (${fail.info?.status.orEmpty()})"
}

/**
 * Login y "agregar cuenta". `onCancel` distinto de null = ya hay una cuenta en uso y se está
 * agregando otra. En la versión de revendedor (`PANEL_LOCKED`) el servidor es fijo y no se pide.
 */
@Composable
fun LoginScreen(graph: AppGraph, onCancel: (() -> Unit)? = null, onDone: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val locked = BuildConfig.PANEL_LOCKED
    var url by rememberSaveable { mutableStateOf(BuildConfig.PANEL_URL) }
    var name by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    // Si la sesión se cerró sola (cuenta vencida, etc.), el motivo aparece aquí.
    var error by remember { mutableStateOf(if (onCancel == null) graph.notice else null) }

    fun submit() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            when (val result = graph.api.login(if (locked) BuildConfig.PANEL_URL else url, username, password, name)) {
                is LoginResult.Ok -> {
                    graph.addAccount(result.creds)
                    onDone()
                }
                is LoginResult.Fail -> error = message(result)
            }
            busy = false
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
                .widthIn(max = 380.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(54.dp))
                Spacer(Modifier.width(12.dp))
                Wordmark(fontSize = 44.sp)
            }
            Text(
                if (onCancel == null) "Ingresa con los datos de tu suscripción" else "Agrega otra cuenta a este dispositivo",
                color = Palette.Muted,
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40) },
                label = { Text("Nombre de la cuenta (opcional)") },
                placeholder = { Text("Casa, Familia, Trabajo…") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            if (!locked) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Servidor") },
                    placeholder = { Text("http://servidor.com:8080") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                enabled = !busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = Palette.Destructive) }

            Button(onClick = { submit() }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Palette.OnPrimary)
                else Text(if (onCancel == null) "Entrar" else "Agregar cuenta")
            }
            if (onCancel != null) {
                TextButton(onClick = onCancel, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
            }
        }
    }
}
