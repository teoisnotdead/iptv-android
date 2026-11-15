package app.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.iptv.player.AppGraph
import app.iptv.player.data.LoginError
import app.iptv.player.data.PanelException
import app.iptv.player.data.accountProblem
import app.iptv.player.ui.account.AccountScreen
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.BrandMark
import app.iptv.player.ui.components.alpha
import app.iptv.player.ui.live.LiveScreen
import app.iptv.player.ui.search.SearchScreen
import app.iptv.player.ui.theme.Palette
import app.iptv.player.ui.vod.MoviesScreen
import app.iptv.player.ui.vod.SeriesScreen
import kotlinx.coroutines.delay

private enum class Section(val label: String, val icon: ImageVector) {
    SEARCH("Buscar", AppIcons.Search),
    LIVE("En vivo", AppIcons.Tv),
    MOVIES("Películas", AppIcons.Film),
    SERIES("Series", AppIcons.Layers),
    ACCOUNT("Cuenta", AppIcons.User),
}

/**
 * Comprueba la cuenta contra el panel al entrar. Un estado definitivo (vencida, deshabilitada)
 * cierra la sesión; una respuesta ambigua (`auth != 1`) se vuelve a consultar una vez antes,
 * para que un panel inestable no saque a nadie. Si el panel no responde, no se hace nada.
 */
private suspend fun verifyAccount(graph: AppGraph) {
    suspend fun check() = try {
        graph.catalog.account()
    } catch (e: PanelException) {
        null
    }

    var info = check() ?: return
    if (info.auth != 1) {
        delay(800)
        info = check() ?: return
        if (info.auth != 1) {
            graph.logout("Tu sesión ya no es válida. Vuelve a ingresar.")
            return
        }
    }
    when (accountProblem(info)) {
        LoginError.EXPIRED -> graph.logout("Tu suscripción está vencida")
        LoginError.DISABLED -> graph.logout("Tu cuenta está deshabilitada")
        else -> Unit
    }
}

/** Estructura de la app con sesión iniciada: secciones y navegación (barra inferior o lateral). */
@Composable
fun AppShell(graph: AppGraph, onAddAccount: () -> Unit) {
    val active by graph.session.creds.collectAsState()
    var section by rememberSaveable { mutableStateOf(Section.LIVE) }
    val holder = rememberSaveableStateHolder()

    LaunchedEffect(active) { verifyAccount(graph) }

    val immersive = remember { mutableStateOf(false) }
    val pip = LocalPip.current
    val currentSection = rememberUpdatedState(section)
    // Canal elegido en la búsqueda global, pendiente de abrir en TV en vivo.
    var liveIntent by rememberSaveable { mutableStateOf<Int?>(null) }

    // Contenido "movible": si la navegación cambia de lugar (abajo ↔ costado, o se oculta en
    // pantalla completa), la sección conserva su estado y el video en curso no se reinicia.
    val content = remember {
        movableContentOf {
            // Al cambiar de cuenta (o actualizar el catálogo) se descarta el estado de todas las secciones.
            // Renombrar la cuenta no lo descarta: la clave es servidor + usuario.
            val account = graph.session.creds.collectAsState().value
            key(account?.url, account?.username, graph.catalogVersion) {
                val shown = currentSection.value
                holder.SaveableStateProvider(shown.name) {
                    when (shown) {
                        Section.SEARCH -> SearchScreen(graph, onOpenChannel = { id ->
                            liveIntent = id
                            section = Section.LIVE
                        })
                        Section.LIVE -> LiveScreen(graph, openChannel = liveIntent, onChannelOpened = { liveIntent = null })
                        Section.MOVIES -> MoviesScreen(graph)
                        Section.SERIES -> SeriesScreen(graph)
                        Section.ACCOUNT -> AccountScreen(graph, onAddAccount)
                    }
                }
            }
        }
    }

    CompositionLocalProvider(LocalImmersive provides immersive) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Palette.Background).systemBarsPadding()) {
            // El símbolo va arriba de la barra lateral solo si cabe sin tocar el menú (en un teléfono horizontal no cabe).
            val markFits = maxHeight >= 460.dp
            when {
                // Video a pantalla completa o en ventana flotante: sin navegación.
                immersive.value || pip.active -> Box(modifier = Modifier.fillMaxSize()) { content() }
                // En horizontal la barra va al costado para no quitarle alto al video.
                maxWidth > maxHeight && maxWidth >= 600.dp -> Row(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.width(76.dp).fillMaxHeight().background(Palette.Sidebar)) {
                        if (markFits) BrandMark(Modifier.align(Alignment.TopCenter).padding(top = 18.dp).size(34.dp))
                        Column(
                            modifier = Modifier.fillMaxSize().padding(vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                        ) {
                            Section.entries.forEach { NavItem(it, it == section, Modifier.fillMaxWidth().height(62.dp)) { section = it } }
                        }
                    }
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { content() }
                }
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) { content() }
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
                    Row(modifier = Modifier.fillMaxWidth().background(Palette.Sidebar).navigationBarsPadding().height(64.dp)) {
                        Section.entries.forEach { NavItem(it, it == section, Modifier.weight(1f).fillMaxHeight()) { section = it } }
                    }
                }
            }
        }
    }
}

/**
 * Ventana flotante (picture-in-picture) de la actividad principal. TV en vivo marca `canEnter`
 * mientras hay un canal elegido; la actividad entra al salir de la app y avisa con `active`.
 */
class PipController {
    var canEnter by mutableStateOf(false)
    var active by mutableStateOf(false)
}

val LocalPip = staticCompositionLocalOf { PipController() }

/** Lo activa una pantalla mientras muestra video a pantalla completa; `AppShell` oculta la navegación. */
val LocalImmersive = compositionLocalOf<MutableState<Boolean>> { mutableStateOf(false) }

/**
 * Pantalla completa mientras esté en composición: oculta la navegación de la app y las barras
 * del sistema. Al salir las restaura. (La orientación no se toca: la app es siempre horizontal.)
 */
@Composable
fun ImmersiveEffect() {
    val activity = LocalContext.current as? Activity
    val immersive = LocalImmersive.current
    DisposableEffect(activity) {
        immersive.value = true
        val bars = activity?.let { WindowCompat.getInsetsController(it.window, it.window.decorView) }
        bars?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            immersive.value = false
            bars?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
private fun NavItem(section: Section, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(12.dp))
                .background(if (active) Palette.Primary.alpha(0.14f) else androidx.compose.ui.graphics.Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Icon(section.icon, null, tint = if (active) Palette.Primary else Palette.Muted, modifier = Modifier.size(22.dp))
        }
        Text(
            section.label,
            color = if (active) Palette.Foreground else Palette.Muted,
            fontSize = 11.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}
