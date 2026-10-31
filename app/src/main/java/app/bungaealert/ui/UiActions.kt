package app.bungaealert.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

internal fun openSetting(context: Context, intent: Intent, failure: String) {
    if (!context.tryStart(intent)) {
        Toast.makeText(context, failure, Toast.LENGTH_LONG).show()
    }
}

internal fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("bungae", text))
    Toast.makeText(context, "복사했습니다", Toast.LENGTH_SHORT).show()
}
