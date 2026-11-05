package app.bungaealert.alert

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import app.bungaealert.detect.AlarmSound
import app.bungaealert.detect.AlertFeel
import app.bungaealert.detect.VibrationMapper

/**
 * Plays the alarm sound and vibration itself instead of through a notification
 * channel: a channel's sound and vibration freeze at creation, so user choices
 * would not apply to the next alarm. Callers pass the [AlertFeel] read at alarm time.
 */
class AlertFeedback(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private var player: MediaPlayer? = null
    private var stopAt: Runnable? = null
    private var replay: Runnable? = null

    /**
     * Starts [feel]. [loop] repeats sound and vibration until [stop]; otherwise they play once
     * (a system tone is cut after [ONCE_SYSTEM_MS]). Any earlier playback of this instance stops.
     */
    @Synchronized
    fun start(feel: AlertFeel, loop: Boolean) {
        stop()
        Log.d(TAG, "feedback start sound=${feel.sound.wire} vib=${feel.vibrationEnabled} " +
            "${feel.vibrationStrength.wire}/${feel.vibrationPattern.wire} loop=$loop")
        try {
            startSound(feel.sound, loop)
        } catch (t: Throwable) {
            Log.w(TAG, "sound failed: ${t.javaClass.simpleName}")
        }
        try {
            if (feel.vibrates) startVibration(feel, loop)
        } catch (t: Throwable) {
            Log.w(TAG, "vibration failed: ${t.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun stop() {
        stopAt?.let { handler.removeCallbacks(it) }
        stopAt = null
        replay?.let { handler.removeCallbacks(it) }
        replay = null
        try {
            ringtone?.stop()
        } catch (_: Throwable) {
        }
        ringtone = null
        try {
            player?.stop()
        } catch (_: Throwable) {
        }
        try {
            player?.release()
        } catch (_: Throwable) {
        }
        player = null
        try {
            vibrator().cancel()
        } catch (_: Throwable) {
        }
    }

    private fun alarmAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private fun startSound(sound: AlarmSound, loop: Boolean) {
        when {
            sound == AlarmSound.SILENT -> return
            sound.rawName != null -> {
                val id = context.resources.getIdentifier(sound.rawName, "raw", context.packageName)
                if (id == 0) {
                    startSystem(loop)
                    return
                }
                val afd = context.resources.openRawResourceFd(id)
                val mp = MediaPlayer()
                try {
                    mp.setAudioAttributes(alarmAttributes())
                    mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                } finally {
                    afd.close()
                }
                mp.isLooping = loop
                mp.prepare()
                mp.start()
                player = mp
            }
            else -> startSystem(loop)
        }
    }

    private fun startSystem(loop: Boolean) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        val tone = RingtoneManager.getRingtone(context, uri) ?: return
        tone.audioAttributes = alarmAttributes()
        if (Build.VERSION.SDK_INT >= 28) tone.isLooping = loop
        tone.play()
        ringtone = tone
        if (loop && Build.VERSION.SDK_INT < 28) {
            val again = object : Runnable {
                override fun run() {
                    val current = ringtone ?: return
                    if (!current.isPlaying) current.play()
                    handler.postDelayed(this, 1_500)
                }
            }
            replay = again
            handler.postDelayed(again, 1_500)
        }
        if (!loop) {
            val cut = Runnable { try { tone.stop() } catch (_: Throwable) {} }
            stopAt = cut
            handler.postDelayed(cut, ONCE_SYSTEM_MS)
        }
    }

    private fun startVibration(feel: AlertFeel, loop: Boolean) {
        val vibrator = vibrator()
        if (!vibrator.hasVibrator()) return
        val wave = VibrationMapper.wave(feel.vibrationPattern, feel.vibrationStrength)
        val repeat = if (loop) 0 else -1
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(wave.timings, wave.amplitudes, repeat)
        } else {
            VibrationEffect.createWaveform(wave.timings, repeat)
        }
        vibrator.vibrate(effect)
    }

    private fun vibrator(): Vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }

    companion object {
        private const val TAG = "BungaeAlert"
        private const val ONCE_SYSTEM_MS = 4_000L

        @Volatile
        private var shared: AlertFeedback? = null

        /** Shared instance for one-shot alerts and settings previews, so they cut each other off. */
        fun shared(context: Context): AlertFeedback =
            shared ?: synchronized(this) {
                shared ?: AlertFeedback(context).also { shared = it }
            }
    }
}
