package com.g41us.faithhymn.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
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

private data class Tab(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab("hymns", "Hymns", Icons.Outlined.MenuBook, Icons.Rounded.MenuBook),
    Tab("search", "Search", Icons.Outlined.Search, Icons.Rounded.Search),
    Tab("library", "Library", Icons.Outlined.FavoriteBorder, Icons.Rounded.Favorite),
    Tab("settings", "Settings", Icons.Outlined.Settings, Icons.Rounded.Settings),
)

@Composable
fun AppRoot(app: FaithHymnApp) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = tabs.any { it.route == route }
    val c = LocalIos.current

    Scaffold(
        containerColor = c.bg,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (onTab) Column {
                MiniPlayer(app, onOpen = { n -> nav.navigate("hymn/$n") })
                TabBar(route) { t -> nav.navigate(t) { popUpTo("hymns") { saveState = true }; launchSingleTop = true; restoreState = true } }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "hymns", modifier = Modifier.padding(pad)) {
            composable("hymns") { HymnsScreen(app, open = { nav.openHymn(it) }) }
            composable("search") { SearchScreen(app, open = { nav.openHymn(it) }) }
            composable("library") { LibraryScreen(app, open = { nav.openHymn(it) }, openPlaylist = { nav.navigate("playlist/$it") }) }
            composable("settings") { SettingsScreen(app) }
            composable("hymn/{n}?playlist={p}", listOf(
                navArgument("n") { type = NavType.IntType },
                navArgument("p") { type = NavType.LongType; defaultValue = -1L },
            )) { e ->
                HymnScreen(app, e.arguments!!.getInt("n"), e.arguments!!.getLong("p").takeIf { it >= 0 },
                    back = { nav.popBackStack() }, present = { n, p -> nav.navigate("present/$n?playlist=${p ?: -1}") })
            }
            composable("playlist/{id}", listOf(navArgument("id") { type = NavType.LongType })) { e ->
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
        }
    }
}

private fun NavHostController.openHymn(n: Int) = navigate("hymn/$n")

@Composable
private fun TabBar(route: String?, onSelect: (String) -> Unit) {
    val c = LocalIos.current
    Column(Modifier.background(c.card.copy(alpha = 0.97f))) {
        Separator(0)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(52.dp)) {
            tabs.forEach { t ->
                val sel = t.route == route
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(t.route) }.padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(if (sel) t.selectedIcon else t.icon, t.label, tint = if (sel) c.accent else c.secondary, modifier = Modifier.size(25.dp))
                    Text(t.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (sel) c.accent else c.secondary)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(app: FaithHymnApp, onOpen: (Int) -> Unit) {
    val st by app.player.state.collectAsStateWithLifecycle()
    val c = LocalIos.current
    AnimatedVisibility(st.number != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp).fillMaxWidth().shadow(8.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp)).background(c.card).clickable { st.number?.let(onOpen) }
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(Icons.Rounded.MusicNote, c.accent, 34)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(st.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(st.error ?: if (st.buffering) "Loading…" else "${fmtTime(st.positionMs)} / ${fmtTime(st.durationMs)}",
                    fontSize = 12.sp, color = if (st.error != null) c.red else c.secondary, maxLines = 1)
            }
            IconButton({ app.player.toggle() }) {
                Icon(if (st.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/Pause", tint = c.label, modifier = Modifier.size(30.dp))
            }
            if (st.hasNext) IconButton({ app.player.next() }) { Icon(Icons.Rounded.SkipNext, "Next", tint = c.label) }
            IconButton({ app.player.stop() }) { Icon(Icons.Rounded.Close, "Stop", tint = c.secondary) }
        }
    }
}
