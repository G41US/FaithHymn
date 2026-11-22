package com.g41us.faithhymn.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.g41us.faithhymn.FaithHymnApp
import com.g41us.faithhymn.MainActivity
import com.g41us.faithhymn.R
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.haze
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("hymns", "Hymns", Icons.Outlined.MenuBook, Icons.Rounded.MenuBook),
    Tab("search", "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Tab("library", "Library", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    Tab("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

private var introShown = false

@Composable
fun AppRoot(app: FaithHymnApp, activity: MainActivity? = null) {
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    var intro by remember { mutableStateOf(!introShown) }
    LaunchedEffect(Unit) { app.catalog.refresh() }

    Box(Modifier.fillMaxSize().background(LocalIos.current.bg)) {
        if (!settings.onboarded) Onboarding { app.settings.update { it.copy(onboarded = true) } }
        else MainShell(app, activity)
        if (intro) AnimatedIntro { intro = false; introShown = true }
    }
}

@Composable
private fun MainShell(app: FaithHymnApp, activity: MainActivity?) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }
    val c = LocalIos.current
    val haze = remember { HazeState() }
    val density = LocalDensity.current
    var barH by remember { mutableStateOf(0.dp) }

    // Shared playlist links (faithhymns://playlist?...)
    val link by (activity?.incomingLink ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsStateWithLifecycle()
    link?.let { uri ->
        PlaylistImportDialog(app, uri, onDone = { id -> activity?.incomingLink?.value = null; if (id != null) nav.navigate("playlist/$id") })
    }

    CompositionLocalProvider(LocalBarPadding provides if (onTab) barH else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            NavHost(nav, startDestination = "hymns", modifier = Modifier.fillMaxSize().haze(haze),
                enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) }) {
                composable("hymns") { HymnsScreen(app, open = { nav.navigate("hymn/$it") }, openTopic = { nav.navigate("topic/${android.net.Uri.encode(it)}") }) }
                composable("topic/{t}", enterTransition = { slideInHorizontally { it } }, popExitTransition = { slideOutHorizontally { it } }) { e ->
                    TopicScreen(app, e.arguments?.getString("t").orEmpty(), back = { nav.popBackStack() }, open = { nav.navigate("hymn/$it") })
                }
                composable("search") { SearchScreen(app, open = { nav.navigate("hymn/$it") }) }
                composable("library") { LibraryScreen(app, open = { nav.navigate("hymn/$it") }, openPlaylist = { nav.navigate("playlist/$it") }) }
                composable("settings") { SettingsScreen(app) }
                composable("hymn/{n}?playlist={p}", listOf(
                    navArgument("n") { type = NavType.IntType },
                    navArgument("p") { type = NavType.LongType; defaultValue = -1L },
                ), enterTransition = { slideInHorizontally { it } }, popExitTransition = { slideOutHorizontally { it } }) { e ->
                    HymnScreen(app, e.arguments!!.getInt("n"), e.arguments!!.getLong("p").takeIf { it >= 0 },
                        back = { nav.popBackStack() }, present = { n, p -> nav.navigate("present/$n?playlist=${p ?: -1}") },
                        openPlayer = { nav.navigate("player") })
                }
                composable("playlist/{id}", listOf(navArgument("id") { type = NavType.LongType }),
                    enterTransition = { slideInHorizontally { it } }, popExitTransition = { slideOutHorizontally { it } }) { e ->
                    val id = e.arguments!!.getLong("id")
                    PlaylistScreen(app, id, back = { nav.popBackStack() },
                        open = { n -> nav.navigate("hymn/$n?playlist=$id") }, present = { n -> nav.navigate("present/$n?playlist=$id") })
                }
                composable("present/{n}?playlist={p}", listOf(
                    navArgument("n") { type = NavType.IntType },
                    navArgument("p") { type = NavType.LongType; defaultValue = -1L },
                )) { e ->
                    PresentScreen(app, e.arguments!!.getInt("n"), e.arguments!!.getLong("p").takeIf { it >= 0 }, close = { nav.popBackStack() })
                }
                composable("player", enterTransition = { slideInVertically { it } }, popExitTransition = { slideOutVertically { it } }) {
                    PlayerScreen(app, close = { nav.popBackStack() }, openHymn = { n -> nav.navigate("hymn/$n") })
                }
            }

            if (onTab) Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { barH = with(density) { it.height.toDp() } }
            ) {
                MiniPlayer(app, onOpen = { nav.navigate("player") })
                TabBar(route, haze) { t -> nav.navigate(t) { popUpTo("hymns") { saveState = true }; launchSingleTop = true; restoreState = true } }
            }
        }
    }
}

@Composable
private fun TabBar(route: String?, haze: HazeState, onSelect: (String) -> Unit) {
    val c = LocalIos.current
    val haptic = LocalHapticFeedback.current
    Column(Modifier.hazeChild(haze, HazeStyle(backgroundColor = c.bg, tints = listOf(HazeTint(c.bar)), blurRadius = 24.dp, noiseFactor = 0f))) {
        Separator(0)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)) {
            tabs.forEach { t ->
                val sel = t.route == route
                val scale by animateFloatAsState(if (sel) 1.08f else 1f, spring(dampingRatio = 0.5f), label = "tab")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = null, indication = null) {
                        if (!sel) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onSelect(t.route)
                    }.padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (sel) t.selectedIcon else t.icon, t.label, tint = if (sel) c.accent else c.secondary, modifier = Modifier.size(25.dp).scale(scale))
                    Text(t.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (sel) c.accent else c.secondary)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(app: FaithHymnApp, onOpen: () -> Unit) {
    val st by app.player.state.collectAsStateWithLifecycle()
    val c = LocalIos.current
    AnimatedVisibility(st.number != null, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
        Column(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth().shadow(10.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp)).background(c.card).clickable(onClick = onOpen)
        ) {
            Row(Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(c.accent), contentAlignment = Alignment.Center) {
                    Text("${st.number ?: ""}", color = if (c.dark) c.bg else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(st.title.substringAfter(". "), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(st.error ?: if (st.buffering) "Loading…" else "${fmtTime(st.positionMs)} / ${fmtTime(st.durationMs)}",
                        fontSize = 12.sp, color = if (st.error != null) c.red else c.secondary, maxLines = 1)
                }
                IconButton({ app.player.toggle() }) { Icon(if (st.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = c.label, modifier = Modifier.size(30.dp)) }
                if (st.hasNext) IconButton({ app.player.next() }) { Icon(Icons.Rounded.SkipNext, "Next", tint = c.label) }
                else IconButton({ app.player.stop() }) { Icon(Icons.Rounded.Close, "Stop", tint = c.secondary) }
            }
            if (st.durationMs > 0) LinearProgressIndicator(progress = { st.positionMs.toFloat() / st.durationMs }, color = c.accent, trackColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth().height(2.dp), drawStopIndicator = {}, gapSize = 0.dp)
        }
    }
}

