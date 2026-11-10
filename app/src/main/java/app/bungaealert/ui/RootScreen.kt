package app.bungaealert.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.bungaealert.BungaeApp
import app.bungaealert.MainActivity

@Composable
fun RootScreen() {
    val context = LocalContext.current
    val graph = (context.applicationContext as BungaeApp).graph
    val settings by graph.settings.state.collectAsState()
    val nav = rememberNavController()
    val start = if (settings.onboardingDone) "home" else "onboarding"
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val host = context as? MainActivity
    val pendingRoute = host?.pendingRoute
    LaunchedEffect(settings.onboardingDone, pendingRoute) {
        if (pendingRoute == null) return@LaunchedEffect
        if (pendingRoute == MainActivity.ROUTE_STATUS && !settings.onboardingDone) return@LaunchedEffect
        if (pendingRoute != MainActivity.ROUTE_STATUS && pendingRoute != MainActivity.ROUTE_SETTINGS) return@LaunchedEffect
        nav.navigate(pendingRoute) { launchSingleTop = true }
        host.consumeRoute()
    }
    BungaeScaffold(tab = tabFor(route), onTab = { nav.openTab(it) }) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(padding)) {
            composable("onboarding") {
                OnboardingRoute {
                    graph.settings.update(graph.settings.current().copy(onboardingDone = true))
                    nav.navigate("home") { popUpTo("onboarding") { inclusive = true } }
                }
            }
            composable("home") {
                HomeRoute(
                    onStatus = { nav.navigate("status") { launchSingleTop = true } },
                    onGuide = { nav.navigate("help") { launchSingleTop = true } },
                )
            }
            composable("rules") { RulesRoute() }
            composable("settings") {
                SettingsRoute(
                    onOpenStatus = { nav.navigate("status") { launchSingleTop = true } },
                    onOpenTrial = { nav.navigate("trial") { launchSingleTop = true } },
                    onOpenOss = { nav.navigate("oss") { launchSingleTop = true } },
                    onOpenHelp = { nav.navigate("help") { launchSingleTop = true } },
                    onOpenAlarmFeel = { nav.navigate("alert_feel") { launchSingleTop = true } },
                )
            }
            composable("alert_feel") { AlertFeelRoute(onBack = { nav.popBackStack() }) }
            composable("status") { StatusRoute(onBack = { nav.popBackStack() }) }
            composable("trial") { TrialRoute(onBack = { nav.popBackStack() }) }
            composable("oss") { OssScreen(onBack = { nav.popBackStack() }) }
            composable("help") { HelpScreen(onBack = { nav.popBackStack() }) }
        }
    }
}

private fun tabFor(route: String?): String? = when (route) {
    "home", "status" -> "home"
    "rules" -> "rules"
    "settings", "oss", "trial", "help", "alert_feel" -> "settings"
    else -> null
}

private fun NavHostController.openTab(id: String) {
    navigate(id) {
        popUpTo("home") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
