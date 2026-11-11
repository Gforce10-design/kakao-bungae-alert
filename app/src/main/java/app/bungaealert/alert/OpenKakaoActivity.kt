package app.bungaealert.alert

import android.app.Activity
import android.os.Bundle

/**
 * Notification action trampoline. A notification tap may start an activity, so KakaoTalk is
 * opened here while this activity is in front. [AlarmRingService] must not open it afterward.
 */
class OpenKakaoActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openKakaoAndStopAlarm(this, intent.getStringExtra(AlarmActivity.EXTRA_ID).orEmpty())
    }
}
