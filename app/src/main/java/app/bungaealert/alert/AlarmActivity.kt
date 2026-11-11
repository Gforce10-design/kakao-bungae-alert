package app.bungaealert.alert

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.bungaealert.ui.AlarmScreen
import app.bungaealert.ui.BungaeTheme

class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        current = this
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val words = intent.getStringArrayListExtra(EXTRA_WORDS).orEmpty()
        setContent {
            BungaeTheme {
                AlarmScreen(
                    title = title,
                    body = body,
                    matchedWords = words,
                    onOpen = { openKakaoAndStopAlarm(this@AlarmActivity, id) },
                    onClose = {
                        AlarmRingService.stop(this, id, openKakao = false)
                        finish()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        if (current === this) current = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_BODY = "body"
        const val EXTRA_WORDS = "matchedWords"
        const val EXTRA_OPEN_ID = "openId"

        private var current: AlarmActivity? = null

        /** Closes the full-screen alarm when 카톡 열기 was tapped from the notification. */
        fun dismiss(id: String) {
            val activity = current ?: return
            val showing = activity.intent?.getStringExtra(EXTRA_ID).orEmpty()
            if (id.isEmpty() || showing.isEmpty() || showing == id) activity.finish()
        }
    }
}
