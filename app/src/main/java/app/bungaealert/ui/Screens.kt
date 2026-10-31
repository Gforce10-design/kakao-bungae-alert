package app.bungaealert.ui

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
    val start = if (settings.onboardingDone) MainTab.Home.route else "onboarding"
    var focusToken by remember { mutableIntStateOf(0) }
    var resume by remember { mutableIntStateOf(0) }
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resume++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(resume, settings.onboardingDone) {
        if (!settings.onboardingDone) return@LaunchedEffect
        if (activity?.intent?.getStringExtra(MainActivity.EXTRA_ROUTE) != "status") return@LaunchedEffect
        activity.intent.removeExtra(MainActivity.EXTRA_ROUTE)
        focusToken++
        nav.navigate(MainTab.Settings.route) {
            popUpTo(MainTab.Home.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    val tab = when {
        route == null || route == "onboarding" -> null
        route.startsWith("rules") -> MainTab.Rules
        route.startsWith("settings") -> MainTab.Settings
        else -> MainTab.Home
    }
    BungaeScaffold(
        tab = tab,
        onTab = { item ->
            nav.navigate(item.route) {
                popUpTo(MainTab.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        },
    ) {
        NavHost(navController = nav, startDestination = start, modifier = Modifier.fillMaxSize()) {
            composable("onboarding") {
                OnboardingRoute {
                    graph.settings.update(graph.settings.current().copy(onboardingDone = true))
                    nav.navigate(MainTab.Home.route) {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            }
            composable(MainTab.Home.route) {
                HomeRoute(
                    onOpenRules = {
                        nav.navigate(MainTab.Rules.route) {
                            popUpTo(MainTab.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenConnection = {
                        focusToken++
                        nav.navigate(MainTab.Settings.route) {
                            popUpTo(MainTab.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
            composable(MainTab.Rules.route) { RulesRoute() }
            composable(MainTab.Settings.route) { SettingsRoute(focusToken) }
        }
    }
}
