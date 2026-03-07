package com.memorystream.ui.navigation

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlinx.coroutines.delay
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.memorystream.audio.AudioPlaybackManager
import com.memorystream.service.RecordingService
import com.memorystream.ui.debug.DebugScreen
import com.memorystream.ui.insights.InsightsScreen
import com.memorystream.ui.onboarding.OnboardingScreen
import com.memorystream.ui.player.FullPlayerScreen
import com.memorystream.ui.player.MiniPlayer
import com.memorystream.ui.profile.ProfileScreen
import com.memorystream.ui.review.DayReviewScreen
import com.memorystream.ui.search.SearchScreen
import com.memorystream.ui.settings.SettingsScreen
import com.memorystream.ui.speakers.SpeakersScreen
import com.memorystream.ui.theme.CalmColors
import com.memorystream.ui.timeline.TimelineScreen
import androidx.navigation.NavGraph.Companion.findStartDestination

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val iconOutlined: ImageVector) {
    data object Timeline : Screen("timeline", "Timeline", Icons.Filled.Schedule, Icons.Outlined.Schedule)
    data object Insights : Screen("insights", "Insights", Icons.Filled.BarChart, Icons.Outlined.BarChart)
    data object Ask : Screen("ask", "Search", Icons.Filled.Search, Icons.Outlined.Search)
    data object Profile : Screen("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)
    data object Onboarding : Screen("onboarding", "Setup", Icons.Filled.Search, Icons.Outlined.Search)
    data object DayReview : Screen("day_review/{dayTimestamp}", "Day Review", Icons.Filled.Search, Icons.Outlined.Search) {
        fun createRoute(dayTimestamp: Long) = "day_review/$dayTimestamp"
    }
    data object FullPlayer : Screen("full_player", "Player", Icons.Filled.Search, Icons.Outlined.Search)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Search, Icons.Outlined.Search)
    data object Speakers : Screen("speakers", "People", Icons.Filled.Search, Icons.Outlined.Search)
    data object Debug : Screen("debug", "Debug", Icons.Filled.Search, Icons.Outlined.Search)
}

private const val PREFS_NAME = "memorystream_prefs"
private const val KEY_ONBOARDED = "onboarding_complete"
private const val KEY_CONTINUOUS_MEMORY = "continuous_memory_enabled"

fun isOnboardingComplete(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_ONBOARDED, false)
}

fun setOnboardingComplete(context: Context) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_ONBOARDED, true)
        .putBoolean(KEY_CONTINUOUS_MEMORY, true)
        .apply()
}

fun isContinuousMemoryEnabled(context: Context): Boolean {
    return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getBoolean(KEY_CONTINUOUS_MEMORY, false)
}

fun setContinuousMemoryEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_CONTINUOUS_MEMORY, enabled)
        .apply()
}

@Composable
fun MemoryStreamNavGraph(playbackManager: AudioPlaybackManager = hiltViewModel<NavPlaybackHolder>().playbackManager) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val onboarded = remember { isOnboardingComplete(context) }
    val startDest = if (onboarded) Screen.Ask.route else Screen.Onboarding.route
    val bottomTabs = listOf(Screen.Timeline, Screen.Insights, Screen.Ask, Screen.Profile)
    val playbackState by playbackManager.playbackState.collectAsState()

    var showSplash by remember { mutableStateOf(onboarded) }

    LaunchedEffect(Unit) {
        if (showSplash) {
            delay(2500)
            showSplash = false
        }
    }

    Box {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val hideBottomNav = currentRoute != null && (
                currentRoute in listOf(
                    Screen.Onboarding.route, Screen.Settings.route, Screen.Speakers.route,
                    Screen.Debug.route, Screen.FullPlayer.route
                ) || currentRoute.startsWith("day_review")
            )
            if (!hideBottomNav) {
                Column(
                    modifier = Modifier
                        .background(CalmColors.NavBarBackground)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    MiniPlayer(
                        playbackState = playbackState,
                        onPlayPause = { playbackManager.togglePlayPause() },
                        onStop = { playbackManager.stop() },
                        onTap = { navController.navigate(Screen.FullPlayer.route) }
                    )

                    CalmBottomNav(
                        tabs = bottomTabs,
                        currentDestinationHierarchy = navBackStackEntry?.destination?.hierarchy,
                        onTabSelected = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .background(CalmColors.ScreenGradient)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            NavHost(
                navController = navController,
                startDestination = startDest
            ) {
                composable(
                    Screen.Onboarding.route,
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    OnboardingScreen(
                        onComplete = {
                            setOnboardingComplete(context)
                            RecordingService.startRecording(context)
                            navController.navigate(Screen.Ask.route) {
                                popUpTo(Screen.Onboarding.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(
                    Screen.Ask.route,
                    enterTransition = { fadeIn(tween(300)) },
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    SearchScreen(
                        onNavigateToSettings = {
                            navController.navigate(Screen.Profile.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(
                    Screen.Timeline.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    TimelineScreen(
                        onNavigateToDayReview = { dayTimestamp ->
                            navController.navigate(Screen.DayReview.createRoute(dayTimestamp))
                        }
                    )
                }

                composable(
                    Screen.Insights.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    InsightsScreen()
                }

                composable(
                    Screen.Profile.route,
                    enterTransition = { fadeIn(tween(200)) },
                    exitTransition = { fadeOut(tween(200)) }
                ) {
                    ProfileScreen(
                        onNavigateToDebug = { navController.navigate(Screen.Debug.route) },
                        onNavigateToSpeakers = { navController.navigate(Screen.Speakers.route) }
                    )
                }

                composable(
                    Screen.Settings.route,
                    enterTransition = { slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                    exitTransition = { slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)) }
                ) {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToDebug = { navController.navigate(Screen.Debug.route) }
                    )
                }

                composable(
                    Screen.Debug.route,
                    enterTransition = { slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                    exitTransition = { slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)) }
                ) {
                    DebugScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    Screen.DayReview.route,
                    arguments = listOf(navArgument("dayTimestamp") { type = NavType.LongType }),
                    enterTransition = { slideInVertically(tween(300)) { it / 4 } + fadeIn(tween(300)) },
                    exitTransition = { slideOutVertically(tween(250)) { it / 4 } + fadeOut(tween(200)) }
                ) { backStackEntry ->
                    val dayTimestamp = backStackEntry.arguments?.getLong("dayTimestamp") ?: System.currentTimeMillis()
                    DayReviewScreen(
                        onBack = { navController.popBackStack() },
                        dayTimestamp = dayTimestamp
                    )
                }

                composable(
                    Screen.FullPlayer.route,
                    enterTransition = { slideInVertically(tween(300)) { it } + fadeIn(tween(300)) },
                    exitTransition = { slideOutVertically(tween(250)) { it } + fadeOut(tween(200)) }
                ) {
                    FullPlayerScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    Screen.Speakers.route,
                    enterTransition = { slideInHorizontally(tween(250)) { it / 3 } + fadeIn(tween(250)) },
                    exitTransition = { slideOutHorizontally(tween(250)) { it / 3 } + fadeOut(tween(200)) }
                ) {
                    SpeakersScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }

    AnimatedVisibility(
        visible = showSplash,
        exit = fadeOut(tween(800))
    ) {
        // Splash covers entire screen including system bars
        SplashOverlay()
    }
    } // end Box
}

@Composable
private fun SplashOverlay() {
    val pulseTransition = rememberInfiniteTransition(label = "splash_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.40f,
        targetValue = 0.80f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val waveScale by pulseTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "waveScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CalmColors.ScreenGradient),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            com.memorystream.ui.components.WaveformVisualization(
                modifier = Modifier
                    .fillMaxWidth(0.5f * waveScale)
                    .height(48.dp),
                barCount = 30,
                barColor = CalmColors.Periwinkle.copy(alpha = 0.15f),
                barHighlightColor = CalmColors.Lavender.copy(alpha = 0.30f),
                animate = true,
                seed = 7777L
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "I'm listening",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Light,
                color = Color.White.copy(alpha = pulseAlpha)
            )
        }
    }
}

@Composable
private fun CalmBottomNav(
    tabs: List<Screen>,
    currentDestinationHierarchy: Sequence<androidx.navigation.NavDestination>?,
    onTabSelected: (Screen) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { screen ->
            val selected = currentDestinationHierarchy?.any { it.route == screen.route } == true
            val icon = if (selected) screen.icon else screen.iconOutlined

            val animatedTint by animateFloatAsState(
                targetValue = if (selected) 1f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "tint_${screen.route}"
            )
            val tint = if (animatedTint > 0.5f) CalmColors.SoftTeal else Color.White.copy(alpha = 0.40f)

            val iconScale by animateFloatAsState(
                targetValue = if (selected) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "scale_${screen.route}"
            )

            val indicatorWidth by animateDpAsState(
                targetValue = if (selected) 24.dp else 0.dp,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "indicator_${screen.route}"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onTabSelected(screen) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(
                    icon,
                    contentDescription = screen.label,
                    modifier = Modifier
                        .size((24 * iconScale).dp),
                    tint = tint
                )
                Spacer(modifier = Modifier.height(3.dp))
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(3.dp)
                        .background(
                            CalmColors.SoftTeal.copy(alpha = animatedTint * 0.8f),
                            RoundedCornerShape(1.5.dp)
                        )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = screen.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}
