package app.bungaealert

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.bungaealert.alert.AlarmActivity
import app.bungaealert.alert.KakaoOpener
import app.bungaealert.notify.ListenerRuntime
import app.bungaealert.ui.BungaeTheme
import app.bungaealert.ui.RootScreen

class MainActivity : ComponentActivity() {
    var pendingRoute by mutableStateOf<String?>(null)
        private set
    var pendingStartUpdate by mutableStateOf(false)
        private set

    fun consumeRoute() {
        pendingRoute = null
    }

    fun consumeStartUpdate() {
        pendingStartUpdate = false
    }

    companion object {
        const val EXTRA_ROUTE = "bungae_route"
        const val EXTRA_START_UPDATE = "bungae_start_update"
        const val ROUTE_STATUS = "status"
        const val ROUTE_SETTINGS = "settings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        accept(intent)
        openKakaoIfAsked(intent)
        setContent {
            BungaeTheme {
                RootScreen()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        ListenerRuntime.ensure(this, fromWatchdog = false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        accept(intent)
        openKakaoIfAsked(intent)
    }

    private fun accept(intent: Intent?) {
        if (intent == null) return
        val route = intent.getStringExtra(EXTRA_ROUTE)
        val startUpdate = intent.getBooleanExtra(EXTRA_START_UPDATE, false)
        if (route != null || startUpdate) {
            intent.removeExtra(EXTRA_ROUTE)
            intent.removeExtra(EXTRA_START_UPDATE)
        }
        if (route == ROUTE_STATUS || route == ROUTE_SETTINGS) pendingRoute = route
        if (startUpdate) {
            pendingRoute = ROUTE_SETTINGS
            pendingStartUpdate = true
        }
    }

    private fun openKakaoIfAsked(intent: Intent?) {
        val id = intent?.getStringExtra(AlarmActivity.EXTRA_OPEN_ID) ?: return
        KakaoOpener.open(this, id)
    }
}
