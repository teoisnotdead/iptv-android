package app.iptv.player.ui.account

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.iptv.player.ui.theme.AppFonts
import app.iptv.player.data.Creds
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.iptv.player.AppGraph
import app.iptv.player.BuildConfig
import app.iptv.player.data.UserInfo
import app.iptv.player.domain.daysLeft
import app.iptv.player.domain.formatDate
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.alpha
import app.iptv.player.ui.components.rememberLoaded
import app.iptv.player.ui.theme.Palette

@Composable
private fun Card(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(Palette.Card).border(1.dp, Palette.Border, shape),
        content = { content() },
    )
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = Palette.Foreground) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Divider() = Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Palette.Border))

private fun expiry(info: UserInfo): Pair<String, Color> {
    val days = daysLeft(info.expDate) ?: return "Sin vencimiento" to Palette.Foreground
    val date = formatDate(info.expDate)
    return when {
        days < 0 -> "Venció el $date" to Palette.Destructive
        days == 0 -> "Vence hoy" to Palette.Warning
        days <= 7 -> "$date · en $days ${if (days == 1) "día" else "días"}" to Palette.Warning
        else -> "$date · en $days días" to Palette.Foreground
    }
}

/** "hace 5 min", "hace 3 h", "hace 2 días". */
private fun ago(millis: Long): String {
    val minutes = ((System.currentTimeMillis() - millis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "recién"
        minutes < 60 -> "hace $minutes min"
        minutes < 48 * 60 -> "hace ${minutes / 60} h"
        else -> "hace ${minutes / (24 * 60)} días"
    }
}

/** Cuenta en uso (estado, vencimiento, conexiones), cuentas guardadas, catálogo y cierre de sesión. */
@Composable
fun AccountScreen(graph: AppGraph, onAddAccount: () -> Unit) {
    val active by graph.session.creds.collectAsState()
    val accounts by graph.session.accounts.collectAsState()
    val info = rememberLoaded(active) { graph.catalog.account() }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 640.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Cuenta", color = Palette.Foreground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

        // Cuenta que se está renombrando (null = diálogo cerrado).
        var renaming by remember { mutableStateOf<Creds?>(null) }
        renaming?.let { target ->
            var text by remember(target) { mutableStateOf(target.name) }
            AlertDialog(
                onDismissRequest = { renaming = null },
                containerColor = Palette.Card,
                title = { Text("Nombre de la cuenta", color = Palette.Foreground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Solo se guarda en este dispositivo. Déjalo vacío para mostrar el usuario (${target.username}).", color = Palette.Muted, fontSize = 13.sp)
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it.take(40) },
                            singleLine = true,
                            placeholder = { Text("Casa, Familia, Trabajo…") },
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        graph.session.rename(target, text)
                        renaming = null
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancelar", color = Palette.Muted) } },
            )
        }

        Card {
            active?.name?.takeIf { it.isNotEmpty() }?.let {
                InfoRow("Nombre", it)
                Divider()
            }
            InfoRow("Usuario", active?.username.orEmpty())
            if (!BuildConfig.PANEL_LOCKED) {
                Divider()
                InfoRow("Servidor", active?.host.orEmpty())
            }
            val data = info.data
            when {
                data != null -> {
                    Divider()
                    val ok = data.status.isEmpty() || data.status == "Active"
                    InfoRow("Estado", if (ok) "Activa" else data.status, if (ok) Palette.Success else Palette.Destructive)
                    Divider()
                    val (text, color) = expiry(data)
                    InfoRow("Vencimiento", text, color)
                    if (data.maxConnections.isNotEmpty()) {
                        Divider()
                        InfoRow("Conexiones en uso", "${data.activeConnections.ifEmpty { "0" }} de ${data.maxConnections}")
                    }
                }
                info.error != null -> {
                    Divider()
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("No se pudo consultar el estado de la cuenta.", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("Reintentar", color = Palette.Primary, fontSize = 13.sp, modifier = Modifier.clickable(onClick = info.reload).padding(6.dp))
                    }
                }
                else -> {
                    Divider()
                    InfoRow("Estado", "Consultando…", Palette.Dim)
                }
            }
        }

        Text("CUENTAS EN ESTE DISPOSITIVO", color = Palette.Dim, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 6.dp))
        Card {
            accounts.forEachIndexed { i, account ->
                val current = active?.sameAccount(account) == true
                if (i > 0) Divider()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !current) { graph.switchTo(account) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(if (current) Palette.Primary.alpha(0.16f) else Palette.Secondary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.User, null, tint = if (current) Palette.Primary else Palette.Muted, modifier = Modifier.size(18.dp))
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(account.label, color = Palette.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val detail = listOfNotNull(
                            account.username.takeIf { account.name.isNotEmpty() },
                            account.host.takeIf { !BuildConfig.PANEL_LOCKED },
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(detail, color = Palette.Dim, fontSize = 12.sp, fontFamily = AppFonts.Mono, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        "Renombrar",
                        color = Palette.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { renaming = account }.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                    if (current) Icon(AppIcons.Check, "En uso", tint = Palette.Primary, modifier = Modifier.size(18.dp))
                }
            }
            Divider()
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onAddAccount).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(AppIcons.Plus, null, tint = Palette.Primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text("Agregar cuenta", color = Palette.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        // El catálogo se guarda en el dispositivo y se renueva solo cada pocas horas; esto fuerza una descarga ya.
        Text("CATÁLOGO", color = Palette.Dim, fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.6.sp, modifier = Modifier.padding(top = 6.dp))
        Card {
            val savedAt = remember(graph.catalogVersion) { graph.catalog.storedAt() }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Canales, películas y series", color = Palette.Foreground, fontSize = 14.sp)
                    Text(
                        if (savedAt > 0) "Guardado ${ago(savedAt)}. Se actualiza solo cada 6 horas." else "Se descarga al abrir cada sección.",
                        color = Palette.Dim,
                        fontSize = 12.sp,
                    )
                }
                Text(
                    "Actualizar ahora",
                    color = Palette.Primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { graph.refreshCatalog() }.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        OutlinedButton(onClick = { graph.logout() }, modifier = Modifier.padding(top = 6.dp)) {
            Icon(AppIcons.Logout, null, tint = Palette.Destructive, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cerrar sesión de ${active?.label.orEmpty()}", color = Palette.Destructive)
        }
        Text("${BuildConfig.SITE_NAME} · versión ${BuildConfig.VERSION_NAME}", color = Palette.Dim, fontSize = 12.sp)
    }
}
