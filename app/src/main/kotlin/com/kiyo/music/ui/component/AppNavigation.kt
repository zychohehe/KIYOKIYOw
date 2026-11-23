// AppNavigation.kt

package com.kiyo.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt
import com.kiyo.music.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.kiyo.music.ui.screens.Screens
import com.kiyo.music.ui.screens.settings.SettingsListRoute
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size
import com.kiyo.music.LocalGlassState

@Immutable
private data class NavItemState(
    val isSelected: Boolean,
    val iconRes: Int
)

/**
 * Navigates to a top-level tab exactly the way the navigation bar does.
 *
 * Anything that jumps to a tab from inside another tab MUST use this rather than a plain
 * `navigate(route)`, and the reason is subtle enough to be worth spelling out.
 *
 * A plain `navigate("settings")` from the profile menu pushes Settings on top of the *Home* tab's
 * stack, because that is where you were standing. Tapping Home in the bar then runs
 * `popUpTo(startDestination) { saveState = true }`, which sweeps that pushed Settings entry into
 * the state saved *for Home* — and `restoreState = true` on the very same call immediately puts it
 * back. The result is that tapping Home lands you on Settings, which is precisely the bug this
 * fixes, and it looks like the bar is ignoring you when in fact it is faithfully restoring a stack
 * that Settings should never have been part of.
 *
 * Going through here instead means a tab is always entered *as* a tab, so it never becomes a child
 * of whichever tab happened to be on screen when you left.
 */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/**
 * a tap on a tab. another tab: switch to it, back on the page you left it on. the tab you're in:
 * from one of its pages, back to the tab's own page (as every tab bar does); already there, the
 * page scrolls to its top.
 */
fun NavController.onTabTapped(screen: Screens, isSelectedTab: Boolean, scrollToTop: () -> Unit) {
    if (!isSelectedTab) {
        navigateToTab(screen.route)
        return
    }
    if (currentDestination?.route == screen.route) {
        currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
        scrollToTop()
        return
    }
    if (!popBackStack(screen.route, inclusive = false)) navigateToTab(screen.route)
}

@Stable
private fun isRouteSelected(currentRoute: String?, screenRoute: String, navigationItems: List<Screens>): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } &&
           currentRoute.startsWith("$screenRoute/")
}

private val RouteOwners: List<Pair<String, String>> = listOf(
    "search/" to "search_input",
    "genre/" to "search_input",
    "youtube_browse/" to "search_input",
    "browse/" to "search_input",
    "library/" to "library",
    "cipher_settings" to "settings",
    "playback_diagnostics" to "settings",
    "kiyo_settings" to "settings",
    "general_settings" to "settings",
    "playback_settings" to "settings",
    "now_playing_settings" to "settings",
    "experimental_settings" to "settings",
    "storage_settings" to "settings",
    "import_sync" to "settings",
    "import_providers" to "settings",
    "import_tunemymusic" to "settings",
    "import_progress" to "settings",
    "youtube_sync" to "settings",
    "youtube_login" to "settings",
    "account_import/" to "settings",
    "equalizer" to "settings",
)

private fun owningTabRoute(currentRoute: String?, navigationItems: List<Screens>): String? {
    if (currentRoute == null) return null
    navigationItems.firstOrNull { isRouteSelected(currentRoute, it.route, navigationItems) }
        ?.let { return it.route }
    RouteOwners.firstOrNull { (prefix, _) ->
        currentRoute == prefix || currentRoute.startsWith(prefix)
    }?.let { return it.second }

    // Catch-all so RouteOwners does not have to stay exhaustive: every settings sub-screen is
    // owned by the settings tab without anyone having to list it. Only a fallback now; the back
    // stack decides the tab whenever it has a tab page in it (see owningTab).
    if (currentRoute.endsWith("_settings") || currentRoute.startsWith("settings")) return "settings"
    return null
}

/**
 * the tab the page on screen belongs to: the nearest tab page under it in the back stack.
 *
 * the bar used to remember the last tab it could name from the current route alone, and keep it
 * for routes no tab claims (albums, playlists, artists). that went wrong two ways: switching back
 * to a tab restores the page you left it on, so coming back to the Library's album kept Home lit;
 * and the remembered value was dropped whenever the bar left the screen (the player's full view,
 * the recap), so it came back as Home. the back stack always knows: a tab is entered with
 * [navigateToTab], so every page sits above the tab page it was opened from.
 */
fun owningTab(backStack: List<NavBackStackEntry>, navigationItems: List<Screens>): String {
    val tabRoutes = navigationItems.mapTo(HashSet()) { it.route }
    backStack.asReversed().firstOrNull { it.destination.route in tabRoutes }
        ?.destination?.route
        ?.let { return it }
    // a stack without a tab page under it (a deep link): go by the route, else the first tab
    return owningTabRoute(backStack.lastOrNull()?.destination?.route, navigationItems)
        ?: navigationItems.firstOrNull()?.route.orEmpty()
}

/**
 * which tab each page belongs to, kept by entry so a page that has already left the stack (the
 * one a tab switch saved away) can still be placed. the tab slide reads this: an album open in
 * the Library slides like the Library, not like a page no tab owns.
 */
class TabOwnership(private val navigationItems: List<Screens>) {
    private val tabRoutes = navigationItems.map { it.route }
    private val owners = HashMap<String, String>()

    fun update(backStack: List<NavBackStackEntry>) {
        var owner: String? = null
        backStack.forEach { entry ->
            val route = entry.destination.route
            if (route != null && route in tabRoutes) owner = route
            owner?.let { owners[entry.id] = it }
        }
        // saved tab stacks keep their entries' ids, so only trim when it has clearly grown stale
        if (owners.size > 400) {
            val live = backStack.mapTo(HashSet()) { it.id }
            owners.keys.retainAll(live)
        }
    }

    /** the tab's position in the bar, or -1 when nothing places it */
    fun indexOf(entry: NavBackStackEntry, backStack: List<NavBackStackEntry>): Int {
        update(backStack)
        val owner = owners[entry.id] ?: entry.destination.route?.takeIf { it in tabRoutes }
            ?: owningTabRoute(entry.destination.route, navigationItems)
        return tabRoutes.indexOf(owner)
    }
}

/** How much of the dark glass covers the blur behind the nav bar. Lower = more transparent (the old look was 0.78). */
/** how much dark tint covers the blurred page: low, so the bar reads as clear glass the page shows through */
internal const val NavBarTintAlpha = 0.22f

/** the base under the blur: lower still, so the blur itself carries the glass */
internal const val NavBarFoundationAlpha = 0.1f

/** The bar itself, a stadium the mini player's 64dp matches. */
internal val NavBarHeight = 64.dp
internal val NavBarShape = RoundedCornerShape(50)

/** How bright an unlit tab's icon is. */
private const val UnlitIconAlpha = 0.75f

@Composable
fun AppNavigationBar(
    navigationItems: List<Screens>,
    currentRoute: String?,
    /** from [owningTab]: the tab the page on screen belongs to */
    selectedTabRoute: String,
    onItemClick: (Screens, Boolean) -> Unit,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    slimNav: Boolean = false,
    /** a second quick tap on Search: straight into typing */
    onSearchDoubleTap: (() -> Unit)? = null,
    /** "module/page" while one of a module's pages is on screen, so a look's bar can light its tab */
    currentPage: String? = null,
    /** opens a look's own tab page */
    onOpenRoute: (String) -> Unit = {},
    /** the player is over the app, so a look's moving bar can rest */
    obscured: () -> Boolean = { false },
    /** 0 = the full pill, 1 = shrunk to one round tab while a page scrolls (the mini player widens into the room) */
    collapse: () -> Float = { 0f },
) {
    // a look with a nav bar of its own draws it in this one's place
    val moduleBar = com.kiyo.music.modules.skin.ActiveSkin.bar
    if (moduleBar != null) {
        ModuleNavBar(
            active = moduleBar,
            navigationItems = navigationItems,
            selectedTabRoute = selectedTabRoute,
            currentPage = currentPage,
            onItemClick = onItemClick,
            onOpenRoute = onOpenRoute,
            onSearchDoubleTap = onSearchDoubleTap,
            pureBlack = pureBlack,
            obscured = obscured,
            modifier = modifier,
        )
        return
    }
    val glassState = LocalGlassState.current ?: remember { GlassState() }
    // like the reference: the tabs share one glass pill, and Search is its own round button beside it
    val pillItems = remember(navigationItems) { navigationItems.filter { it == Screens.Home || it == Screens.Search || it == Screens.Library } }
    // a page no pill tab owns (Settings and its sub-pages) falls back to Home, so the bar shrunk to one
    // round tab still shows an icon there instead of an empty circle
    val shownTabRoute = if (pillItems.any { it.route == selectedTabRoute }) selectedTabRoute else Screens.Home.route
    val selectedIndex = pillItems.indexOfFirst { it.route == shownTabRoute }
    val tabCount = pillItems.size
    val pill = rememberNavPill(selectedIndex)
    // a look's glass can draw its own edge and its own pill, and then kiyo leaves its hairline, shadow or highlight out
    val moduleGlass = rememberChromeModuleGlass()
    // the lit tab's colour: the theme's accent, pushed toward white so it reads on the dark glass
    // the lit tab is white glass, never the theme or a page colour, so the bar stays clear and neutral
    val litTint = Color.White
    val density = LocalDensity.current
    val glassPill = remember(pill, tabCount, density) {
        { bar: Size -> pill.forGlass(bar, with(density) { NavRowPadding.toPx() }, tabCount, density) }
    }

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .align(Alignment.TopCenter)
                .height(NavBarHeight),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    // shrinks from the full pill to a circle as the page scrolls
                    .layout { measurable, constraints ->
                        val c = collapse().coerceIn(0f, 1f)
                        val w = (constraints.maxWidth + (constraints.maxHeight - constraints.maxWidth) * c).roundToInt()
                        val placeable = measurable.measure(Constraints.fixed(w, constraints.maxHeight))
                        layout(w, constraints.maxHeight) { placeable.place(0, 0) }
                    }
                    .press3D(maxTilt = 4f, pressedScale = 0.985f)
                    .then(if (moduleGlass?.shadow == false) Modifier else Modifier.dropShadow(NavBarShape, ChromeShadow))
                    .clip(NavBarShape)
                    .drawWithContent {
                        drawContent()
                        drawLiquidSheen(size)
                        if (moduleGlass?.outline != false) drawGlassRim(size)
                    }
            ) {
                FrostedChromeGlass(
                    state = glassState,
                    pureBlack = pureBlack,
                    accentWash = null,
                    modifier = Modifier.fillMaxSize(),
                    pill = glassPill,
                    tintAlpha = NavBarTintAlpha,
                    foundationAlpha = NavBarFoundationAlpha,
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = NavRowPadding)
                        .navHighlight(pill, tabCount, color = LitCapsule, rim = true, alpha = { 1f - collapse().coerceIn(0f, 1f) }) { moduleGlass?.drawsPill != true }
                        .navPillDrag(pill, tabCount, selectedIndex) { index ->
                            pillItems.getOrNull(index)?.let { onItemClick(it, index == selectedIndex) }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pillItems.forEach { screen ->
                        // lit = the tab this page belongs to. what a tap on it does (scroll to the top
                        // on the tab's own page, back to that page from deeper in) is the caller's call
                        NavTab(
                            screen = screen,
                            isSelected = screen.route == shownTabRoute,
                            onItemClick = onItemClick,
                            onDoubleTap = null,
                            litTint = litTint,
                            alwaysLabel = true,
                            asExplore = screen == Screens.Search,
                            collapse = collapse().coerceIn(0f, 1f),
                        )
                    }
                }
            }
            }

            // the round button beside the tabs opens Settings: its own menu, apart from the profile
            // (the profile is the avatar at the top of the page)
            SettingsOrb(
                onOpen = { onOpenRoute(SettingsListRoute) },
                glassState = glassState,
                pureBlack = pureBlack,
                moduleGlass = moduleGlass,
                litTint = litTint,
            )
        }
    }
}

/** Settings as its own round glass button next to the tab pill, apart from the profile. */
@Composable
private fun SettingsOrb(
    onOpen: () -> Unit,
    glassState: GlassState,
    pureBlack: Boolean,
    moduleGlass: com.kiyo.music.modules.skin.ActiveGlass?,
    litTint: Color,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(NavBarHeight)
            .press3D(maxTilt = 4f, pressedScale = 0.97f)
            .then(if (moduleGlass?.shadow == false) Modifier else Modifier.dropShadow(CircleShape, ChromeShadow))
            .clip(CircleShape)
            .drawWithContent {
                drawContent()
                drawLiquidSheen(size)
                        if (moduleGlass?.outline != false) drawGlassRim(size)
            }
            .pressBounce(interactionSource, pressedScale = 0.88f)
            .clickable(
                interactionSource = interactionSource,
                indication = RemoteFocus,
                onClick = onOpen,
            ),
        contentAlignment = Alignment.Center
    ) {
        FrostedChromeGlass(
            state = glassState,
            pureBlack = pureBlack,
            accentWash = null,
            modifier = Modifier.fillMaxSize(),
            tintAlpha = NavBarTintAlpha,
            foundationAlpha = NavBarFoundationAlpha,
        )
        Icon(
            painter = painterResource(R.drawable.settings),
            contentDescription = stringResource(R.string.settings),
            tint = Color.White.copy(alpha = UnlitIconAlpha),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** the lit tab's capsule: dark and a little glassy, as in the reference */
private val LitCapsule = Color.White.copy(alpha = 0.12f)

/**
 * the liquid look: a soft light across the top of the glass that fades down through the middle,
 * so it reads as a clear, wet pane rather than a flat tinted plate. one cheap gradient, no blur.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLiquidSheen(size: Size) {
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.45f to Color.White.copy(alpha = 0.035f),
            1f to Color.Transparent,
            startY = 0f,
            endY = size.height,
        ),
        size = size,
    )
}

/** the glass's edge: a thin rim that catches light at the top left and the bottom right */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassRim(size: Size) {
    val w = 1.2.dp.toPx()
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.White.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f), Color.White.copy(alpha = 0.18f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        ),
        topLeft = Offset(w / 2f, w / 2f),
        size = Size(size.width - w, size.height - w),
        cornerRadius = CornerRadius(size.height / 2f - w / 2f),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = w),
    )
}

/**
 * The new bar's mark for the lit tab: a short glowing beam on the bar's top edge and a soft wash of
 * [color] rising from the bottom under it. Drawn from the pill's springs, so it slides and swells
 * with the pill and never recomposes the bar.
 */
private fun Modifier.navBeam(pill: NavPillState, tabCount: Int, color: Color): Modifier = drawBehind {
    val rect = pill.rect(size, tabCount, this) ?: return@drawBehind
    val shown = pill.shown.value
    val lift = pill.lift.value
    val radius = rect.width * (0.85f + 0.25f * lift)
    drawCircle(
        brush = Brush.radialGradient(listOf(color.copy(alpha = 0.38f), Color.Transparent), center = Offset(rect.center.x, size.height), radius = radius),
        radius = radius,
        center = Offset(rect.center.x, size.height),
        alpha = shown,
    )
    val beamWidth = rect.width * (0.34f + 0.1f * lift)
    val beamThick = 3.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(rect.center.x - beamWidth / 2f, 1.dp.toPx()),
        size = Size(beamWidth, beamThick),
        cornerRadius = CornerRadius(beamThick / 2f),
        alpha = shown,
    )
}

/**
 * A tab's tap, or with [onDoubleTap] a second tap close behind the first: the first still goes to
 * the tab at once, the second does [onDoubleTap] instead. [lastTap] holds the time of the one before.
 */
internal fun tabTapped(lastTap: LongArray, doubleTapTimeoutMillis: Long, onDoubleTap: (() -> Unit)?, onTap: () -> Unit) {
    val now = android.os.SystemClock.uptimeMillis()
    if (onDoubleTap != null && now - lastTap[0] < doubleTapTimeoutMillis) {
        lastTap[0] = 0L
        onDoubleTap()
    } else {
        lastTap[0] = now
        onTap()
    }
}

/**
 * One tab of the bar. Its icon swaps to the filled one and brightens as it lights up, and it
 * squishes under a press. [onDoubleTap] is for Search, where two taps open the search field.
 * The Library's icon turns into a download badge while songs download.
 */
@Composable
internal fun RowScope.NavTab(
    screen: Screens,
    isSelected: Boolean,
    onItemClick: (Screens, Boolean) -> Unit,
    onDoubleTap: (() -> Unit)?,
    litTint: Color = Color.White,
    /** the tab's name stays under its icon when it isn't lit (the phone bar); the wide bar keeps it for the lit tab only */
    alwaysLabel: Boolean = false,
    /** this tab is the Explore page (the search page's tab): its own name and the grid icon */
    asExplore: Boolean = false,
    /** 0..1, the bar shrinking to its lit tab: the others narrow away and fade */
    collapse: Float = 0f,
) {
    val viewConfiguration = LocalViewConfiguration.current
    val interactionSource = remember { MutableInteractionSource() }
    val lastTap = remember { longArrayOf(0L) }
    val lit = animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        // a little less bounce than before, so lighting a tab glides instead of wobbling
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "nav_tab_lit"
    )

    // Both icons stay loaded, so lighting a tab never shows an empty frame while its filled icon loads.
    val unlitIcon = coil3.compose.rememberAsyncImagePainter(model = if (asExplore) R.drawable.grid_view else screen.iconIdInactive)
    val litIcon = coil3.compose.rememberAsyncImagePainter(model = if (asExplore) R.drawable.grid_view else screen.iconIdActive)
    val title = if (asExplore) stringResource(R.string.nav_explore) else stringResource(screen.titleId)
    val iconSize = if (screen == Screens.Home || screen == Screens.Search) 22.dp else 24.dp

    Box(
        modifier = Modifier
            .weight((if (isSelected) 1f else 1f - collapse).coerceAtLeast(0.0001f))
            .fillMaxHeight()
            .graphicsLayer { alpha = if (isSelected) 1f else 1f - collapse }
            .pressBounce(interactionSource, pressedScale = 0.85f)
            .clickable(
                interactionSource = interactionSource,
                indication = RemoteFocus,
                onClick = {
                    tabTapped(lastTap, viewConfiguration.doubleTapTimeoutMillis, onDoubleTap) {
                        onItemClick(screen, isSelected)
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (screen == Screens.Library) {
            LibraryDownloadIcon { TabIcons(screen, title, unlitIcon, litIcon, iconSize, litTint, alwaysLabel, if (isSelected) collapse else 0f) { lit.value } }
        } else {
            TabIcons(screen, title, unlitIcon, litIcon, iconSize, litTint, alwaysLabel, if (isSelected) collapse else 0f) { lit.value }
        }
    }
}

/** a tab's two icons, the filled one fading in over the outline as the tab lights up */
@Composable
private fun TabIcons(
    screen: Screens,
    title: String,
    unlitIcon: androidx.compose.ui.graphics.painter.Painter,
    litIcon: androidx.compose.ui.graphics.painter.Painter,
    iconSize: androidx.compose.ui.unit.Dp,
    litTint: Color,
    alwaysLabel: Boolean = false,
    labelHidden: Float = 0f,
    lit: () -> Float,
) {
    Box(contentAlignment = Alignment.Center) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.graphicsLayer {
                val l = lit().coerceIn(0f, 1f)
                // the lit tab's icon lifts a little and grows, making room for its name underneath
                translationY = -6.dp.toPx() * (if (alwaysLabel) 1f else l) * (1f - labelHidden)
                scaleX = 1f + 0.1f * l
                scaleY = 1f + 0.1f * l
            },
        ) {
            Icon(
                painter = unlitIcon,
                contentDescription = title,
                tint = Color.White,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        alpha = UnlitIconAlpha * (1f - lit().coerceIn(0f, 1f))
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
            )
            Icon(
                painter = litIcon,
                contentDescription = null,
                tint = litTint,
                modifier = Modifier
                    .size(iconSize)
                    .graphicsLayer {
                        alpha = lit().coerceIn(0f, 1f)
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
            )
        }
        Text(
            text = title,
            color = if (alwaysLabel) lerp(Color.White.copy(alpha = UnlitIconAlpha), litTint, lit().coerceIn(0f, 1f)) else litTint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.1).sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer {
                val l = lit().coerceIn(0f, 1f)
                alpha = (if (alwaysLabel) 1f else l) * (1f - labelHidden)
                translationY = 17.dp.toPx() + (if (alwaysLabel) 0f else (1f - l) * 4.dp.toPx())
            },
        )
    }
}
