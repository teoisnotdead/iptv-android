package app.iptv.player.ui.live

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import app.iptv.player.AppGraph
import app.iptv.player.data.FavKind
import app.iptv.player.data.LiveStream
import app.iptv.player.data.StreamKind
import app.iptv.player.domain.SearchIndex
import app.iptv.player.player.PlaybackSession
import app.iptv.player.ui.ImmersiveEffect
import app.iptv.player.ui.LocalPip
import app.iptv.player.ui.components.AppIcons
import app.iptv.player.ui.components.CatalogBrowser
import app.iptv.player.ui.components.CategoryList
import app.iptv.player.ui.components.ChannelCard
import app.iptv.player.ui.components.EmptyState
import app.iptv.player.ui.components.FavoritesToggle
import app.iptv.player.ui.components.Pinned
import app.iptv.player.ui.components.SearchField
import app.iptv.player.ui.components.rememberLoaded
import app.iptv.player.ui.theme.Palette

/** Desde este ancho, la lista y el reproductor van lado a lado. */
private val SPLIT_WIDTH = 700.dp

/**
 * TV en vivo. En pantallas anchas, tres columnas: carpetas | canales | previsualizador (este
 * último solo con un canal elegido). En angostas, previsualizador arriba y la lista con chips debajo.
 * El canal se puede ampliar a pantalla completa y, al salir de la app, sigue en una ventana flotante.
 * `openChannel`: canal pedido desde la búsqueda global; se avisa con `onChannelOpened` al usarlo.
 */
@Composable
fun LiveScreen(graph: AppGraph, openChannel: Int? = null, onChannelOpened: () -> Unit = {}) {
    val categoriesState = rememberLoaded { graph.catalog.liveCategories() }
    val streamsState = rememberLoaded { graph.catalog.liveStreams() }
    val streams = streamsState.data.orEmpty()
    val categories = categoriesState.data.orEmpty()
    val catNames = remember(categories) { categories.associate { it.id to it.name } }

    var category by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var onlyFavorites by rememberSaveable { mutableStateOf(false) }
    var currentId by rememberSaveable { mutableStateOf<Int?>(null) }

    val favorites by graph.favorites.all.collectAsState()
    val favoriteIds = favorites[FavKind.LIVE].orEmpty()

    val byId = remember(streams) { streams.associateBy { it.streamId } }
    val current = currentId?.let { byId[it] }
    val watching = current != null

    // Busca por nombre o por nombre de categoría; primero lo más parecido (ver domain/Search.kt).
    val searchIndex = remember(streams, catNames) { SearchIndex(streams, { it.name }, { catNames[it.categoryId].orEmpty() }) }
    val q = query.trim()
    val searching = q.isNotEmpty()
    val filtered = remember(streams, searchIndex, q) { if (q.isEmpty()) streams else searchIndex.search(q).items }

    // Favoritos en el orden en que se agregaron (el último primero); los que ya no están en el panel se omiten.
    val favoriteStreams = remember(favoriteIds, byId) { favoriteIds.mapNotNull { byId[it] } }
    val visible = remember(onlyFavorites, filtered, favoriteStreams) {
        if (!onlyFavorites) filtered else filtered.toSet().let { matches -> favoriteStreams.filter { it in matches } }
    }
    val showOnlyFavorites = { on: Boolean ->
        onlyFavorites = on
        category = "" // la categoría elegida puede no tener favoritos
    }

    // Lista sobre la que navegan "anterior/siguiente".
    val scoped = remember(visible, category) { if (category.isEmpty()) visible else visible.filter { it.categoryId == category } }
    val index = if (current == null) -1 else scoped.indexOfFirst { it.streamId == current.streamId }

    val epg = rememberLoaded(currentId) { currentId?.let { graph.catalog.shortEpg(it) }.orEmpty() }

    // Canal pedido desde la búsqueda global: se elige cuando la lista ya cargó.
    LaunchedEffect(openChannel, streams.isNotEmpty()) {
        val id = openChannel ?: return@LaunchedEffect
        if (streams.isEmpty()) return@LaunchedEffect
        byId[id]?.let { found ->
            query = ""
            onlyFavorites = false
            category = found.categoryId
            currentId = id
        }
        onChannelOpened()
    }

    // Modo de reproducción en vivo (HLS o TS) que eligió el usuario; cambiarlo reinicia el canal en ese modo.
    var preferTs by remember { mutableStateOf(graph.trackPrefs.liveTs) }

    // La reproducción vive aquí, por encima del diseño: cambiar entre previsualizador, pantalla
    // completa y ventana flotante solo mueve el video de lugar, sin volver a cargar el canal.
    val context = LocalContext.current
    val session = remember(currentId, preferTs) {
        currentId?.let { PlaybackSession(context, StreamKind.LIVE, it.toString(), "", byId[it]?.name.orEmpty()).also { s -> s.useTs = preferTs } }
    }
    SessionLifecycle(session)
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    // Si el canal falla (p. ej. 403 porque no está al aire), se sale de pantalla completa:
    // el error queda en el previsualizador y la lista sigue a la vista para elegir otro.
    if (fullscreen && (session == null || session.error != null)) fullscreen = false

    // Al salir de la app con un canal en reproducción, sigue en una ventana flotante.
    val pip = LocalPip.current
    DisposableEffect(session) {
        pip.canEnter = session != null
        onDispose { pip.canEnter = false }
    }

    BackHandler(enabled = watching) { if (fullscreen) fullscreen = false else currentId = null }

    if (session != null && (fullscreen || pip.active)) {
        if (!pip.active) ImmersiveEffect()
        LiveVideo(
            session,
            fullscreen = true,
            onToggleFullscreen = { fullscreen = false },
            title = current?.name.orEmpty(),
            pip = pip.active,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val wide = boxWidth >= SPLIT_WIDTH
        val flatTitle = when {
            onlyFavorites && searching -> "Favoritos con “$q”"
            onlyFavorites -> "Tus favoritos"
            searching -> "Resultados para “$q”"
            else -> "Todos los canales"
        }

        val select = { c: LiveStream ->
            // Desde las filas por categoría, la lista lateral pasa a mostrar la categoría del canal elegido.
            if (!watching && wide && category.isEmpty() && !searching && !onlyFavorites) category = c.categoryId
            currentId = c.streamId
        }

        val browser: @Composable (Modifier, Dp, Boolean) -> Unit = { modifier, gutter, categoryBar ->
            CatalogBrowser(
                items = visible,
                categories = categories,
                keyOf = { it.streamId },
                categoryOf = { it.categoryId },
                category = category,
                onCategoryChange = { category = it },
                loading = streamsState.loading || categoriesState.loading,
                error = streamsState.error ?: categoriesState.error,
                onRetry = {
                    streamsState.reload()
                    categoriesState.reload()
                },
                cardWidth = 300.dp,
                gridMinWidth = 270.dp,
                cardHeight = 72.dp,
                allLabel = "Todos",
                unit = "canales",
                unitOne = "canal",
                flat = !wide || watching || searching || onlyFavorites,
                flatTitle = flatTitle,
                modifier = modifier,
                gutter = gutter,
                categoryBar = categoryBar,
                activeKey = current?.streamId,
                pinned = Pinned("★ Favoritos", favoriteStreams) { showOnlyFavorites(true) },
                empty = if (onlyFavorites && favoriteStreams.isEmpty()) {
                    { EmptyState(AppIcons.Star, "Aún no tienes favoritos", "Toca la estrella de un canal para tenerlo siempre a mano aquí.") }
                } else null,
                toolbar = {
                    SearchField(query, { query = it }, "Buscar canal o categoría…", modifier = Modifier.weight(1f))
                    FavoritesToggle(onlyFavorites, favoriteStreams.size) { showOnlyFavorites(!onlyFavorites) }
                },
            ) { c ->
                ChannelCard(
                    channel = c,
                    category = catNames[c.categoryId],
                    active = current?.streamId == c.streamId,
                    favorite = c.streamId in favoriteIds,
                    onSelect = { select(c) },
                    onToggleFavorite = { graph.favorites.toggle(FavKind.LIVE, c.streamId) },
                )
            }
        }

        // Categorías que tienen canales en la lista actual (respeta búsqueda y "solo favoritos").
        val counts = remember(visible) { visible.groupingBy { it.categoryId }.eachCount() }

        val stage: @Composable (Modifier, Dp) -> Unit = { modifier, playerHeight ->
            if (current != null && session != null) {
                Column(modifier = modifier) {
                    LiveVideo(session, fullscreen = false, onToggleFullscreen = { fullscreen = true }, modifier = Modifier.fillMaxWidth().height(playerHeight))
                    ChannelInfo(
                        channel = current,
                        categoryName = catNames[current.categoryId],
                        ts = session.useTs,
                        onEngineChange = { ts ->
                            graph.trackPrefs.liveTs = ts
                            preferTs = ts
                        },
                        favorite = current.streamId in favoriteIds,
                        onToggleFavorite = { graph.favorites.toggle(FavKind.LIVE, current.streamId) },
                        onPrev = if (index > 0) ({ currentId = scoped[index - 1].streamId }) else null,
                        onNext = if (index >= 0 && index < scoped.size - 1) ({ currentId = scoped[index + 1].streamId }) else null,
                        onClose = { currentId = null },
                    )
                    NowNext(epg.data.orEmpty(), modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp))
                }
            }
        }

        if (wide) {
            // Tres columnas, como las apps clásicas de IPTV: carpetas | canales | reproductor.
            // Sin canal elegido, los canales ocupan todo el espacio a la derecha de las carpetas.
            Row(modifier = Modifier.fillMaxSize()) {
                val foldersWidth = (boxWidth * 0.2f).coerceIn(170.dp, 230.dp)
                val listWidth = (boxWidth * 0.3f).coerceIn(250.dp, 330.dp)
                CategoryList(
                    categories = categories,
                    counts = counts,
                    total = visible.size,
                    value = category,
                    onChange = { category = it },
                    allLabel = "Todos",
                    modifier = Modifier.width(foldersWidth).fillMaxHeight(),
                )
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
                browser(if (watching) Modifier.width(listWidth).fillMaxHeight() else Modifier.weight(1f).fillMaxHeight(), 12.dp, false)
                if (watching) {
                    Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(Palette.Border))
                    val stageWidth = boxWidth - foldersWidth - listWidth - 2.dp
                    // El video conserva 16:9 pero nunca desplaza fuera de pantalla la ficha del canal.
                    val playerHeight = min(stageWidth * 9f / 16f, boxHeight - 76.dp).coerceAtLeast(120.dp)
                    stage(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), playerHeight)
                }
            }
        } else {
            // Pantalla angosta: no caben columnas. Reproductor arriba (si hay canal) y la lista debajo, con chips de categoría.
            Column(modifier = Modifier.fillMaxSize()) {
                if (watching) {
                    stage(Modifier.fillMaxWidth(), min(boxWidth * 9f / 16f, boxHeight * 0.45f))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Palette.Border))
                }
                browser(Modifier.fillMaxWidth().weight(1f), if (watching) 12.dp else 16.dp, true)
            }
        }
    }
}
