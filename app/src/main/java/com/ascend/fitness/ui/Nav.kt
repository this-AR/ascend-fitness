package com.ascend.fitness.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ascend.fitness.AscendApp
import com.ascend.fitness.ui.screens.AiPlanScreen
import com.ascend.fitness.ui.screens.CoachScreen
import com.ascend.fitness.ui.screens.ExerciseDetailScreen
import com.ascend.fitness.ui.screens.HistoryScreen
import com.ascend.fitness.ui.screens.HomeScreen
import com.ascend.fitness.ui.screens.LoggerScreen
import com.ascend.fitness.ui.screens.MeasurementsScreen
import com.ascend.fitness.ui.screens.OnboardingScreen
import com.ascend.fitness.ui.screens.ProfileScreen
import com.ascend.fitness.ui.screens.ProgressScreen
import com.ascend.fitness.ui.screens.RoutineEditScreen
import com.ascend.fitness.ui.screens.SettingsScreen
import com.ascend.fitness.ui.screens.SplitsScreen
import com.ascend.fitness.ui.screens.SummaryScreen
import com.ascend.fitness.ui.screens.WorkoutScreen
import com.ascend.fitness.ui.theme.AscendTheme
import com.ascend.fitness.ui.theme.LocalAscend

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("home", "Home", Icons.Default.Home),
    Tab("workout", "Workout", Icons.Default.FitnessCenter),
    Tab("progress", "Progress", Icons.Default.ShowChart),
    Tab("profile", "Profile", Icons.Default.Person),
)

fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AscendRoot(app: AscendApp) {
    val repo = app.repo
    val settings by repo.settings.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { repo.ensureSeeded(); repo.removeDuplicateRoutines() }
    AscendTheme(settings.theme) {
        val c = LocalAscend.current
        if (!settings.onboarded) {
            OnboardingScreen(repo)
            return@AscendTheme
        }
        val nav = rememberNavController()
        val entry by nav.currentBackStackEntryAsState()
        val route = entry?.destination?.route
        Column(Modifier.fillMaxSize().background(c.bg)) {
            Box(Modifier.weight(1f)) {
                NavHost(nav, startDestination = "workout") {
                    composable("home") { HomeScreen(repo, nav) }
                    composable("workout") { WorkoutScreen(repo, nav) }
                    composable("progress") { ProgressScreen(repo, nav) }
                    composable("profile") { ProfileScreen(repo, nav) }
                    composable("logger/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        LoggerScreen(repo, nav, it.arguments!!.getLong("id"), app)
                    }
                    composable("summary/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        SummaryScreen(repo, nav, it.arguments!!.getLong("id"), app)
                    }
                    composable("routine/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        RoutineEditScreen(repo, nav, it.arguments!!.getLong("id"))
                    }
                    composable("splits") { SplitsScreen(repo, nav) }
                    composable("exercise/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        ExerciseDetailScreen(repo, nav, it.arguments!!.getLong("id"))
                    }
                    composable("measurements") { MeasurementsScreen(repo, nav) }
                    composable("ranks") { com.ascend.fitness.ui.screens.RanksScreen(repo, nav) }
                    composable("history") { HistoryScreen(repo, nav) }
                    composable("settings") { SettingsScreen(repo, nav) }
                    composable("coach?q={q}", listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })) {
                        CoachScreen(repo, nav, it.arguments?.getString("q").orEmpty())
                    }
                    composable("aiplan") { AiPlanScreen(repo, nav) }
                }
            }
            if (route in TABS.map { it.route }) {
                NavigationBar(containerColor = c.surface, modifier = Modifier.navigationBarsPadding()) {
                    TABS.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = { nav.tab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.accent, selectedTextColor = c.accent, indicatorColor = c.soft,
                                unselectedIconColor = c.muted, unselectedTextColor = c.muted,
                            ),
                        )
                    }
                }
            }
        }
    }
}
