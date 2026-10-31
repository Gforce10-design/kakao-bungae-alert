package app.bungaealert

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.bungaealert.alert.AlarmActivity
import app.bungaealert.alert.KakaoOpener
import app.bungaealert.notify.ListenerRuntime
import app.bungaealert.ui.BungaeTheme
import app.bungaealert.ui.RootScreen

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ROUTE = "bungae_route"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        openKakaoIfAsked(intent)
    }

    private fun openKakaoIfAsked(intent: Intent?) {
        val id = intent?.getStringExtra(AlarmActivity.EXTRA_OPEN_ID) ?: return
        KakaoOpener.open(this, id)
    }
}
