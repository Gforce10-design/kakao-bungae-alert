package app.bungaealert.update

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

/**
 * The system starts this activity when an install session needs the user to confirm.
 * The confirmation window itself is Android's.
 */
class UpdateInstallTrampoline : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> openConfirm(intent.parcelableIntent(Intent.EXTRA_INTENT))
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                Log.w(
                    TAG,
                    "install status=$status ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()}",
                )
                toast(UpdateCopy.INSTALL_REJECTED)
            }
        }
        finish()
    }

    private fun openConfirm(confirm: Intent?) {
        if (confirm == null) {
            toast(UpdateCopy.CONFIRM_FAILED)
            return
        }
        try {
            startActivity(confirm)
        } catch (_: Exception) {
            try {
                startActivity(Intent(confirm).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                toast(UpdateCopy.CONFIRM_FAILED)
            }
        }
    }

    private fun toast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
    }

    private fun Intent.parcelableIntent(name: String): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(name, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }
    }

    private companion object {
        const val TAG = "BungaeUpdate"
    }
}
