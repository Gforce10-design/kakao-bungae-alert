package app.bungaealert.detect

/** Which sound the 번개 alarm plays. [SYSTEM] is the phone's default alarm tone. */
enum class AlarmSound(val wire: String, val label: String, val rawName: String?) {
    SYSTEM("system", "기본 알람음", null),
    CHIME("chime", "차임", "alarm_chime"),
    BELL("bell", "벨", "alarm_bell"),
    BEEP("beep", "삐삐삐", "alarm_beep"),
    SILENT("silent", "무음", null),
    ;

    companion object {
        val DEFAULT = SYSTEM
        fun fromWire(value: String?): AlarmSound = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

enum class VibrationStrength(val wire: String, val label: String, val amplitude: Int) {
    WEAK("weak", "약", 70),
    MEDIUM("medium", "중", 150),
    STRONG("strong", "강", 255),
    ;

    companion object {
        val DEFAULT = STRONG
        fun fromWire(value: String?): VibrationStrength = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

/**
 * Vibration rhythm as alternating off/on durations in ms (first entry is the initial
 * delay), the shape [android.os.VibrationEffect.createWaveform] expects.
 */
enum class VibrationPattern(val wire: String, val label: String, val timings: LongArray) {
    SHORT("short", "짧게", longArrayOf(0, 250, 750)),
    LONG("long", "길게", longArrayOf(0, 1200, 500)),
    REPEAT("repeat", "반복", longArrayOf(0, 200, 150, 200, 150, 200, 650)),
    HEARTBEAT("heartbeat", "하트비트", longArrayOf(0, 120, 120, 220, 900)),
    ;

    companion object {
        /** The pre-settings behaviour: 600 ms on, 400 ms off. */
        val DEFAULT = LONG
        fun fromWire(value: String?): VibrationPattern = entries.firstOrNull { it.wire == value } ?: DEFAULT
    }
}

/** The user's alarm sound and vibration choices. Read fresh at every alarm. */
data class AlertFeel(
    val sound: AlarmSound = AlarmSound.DEFAULT,
    val vibrationEnabled: Boolean = true,
    val vibrationStrength: VibrationStrength = VibrationStrength.DEFAULT,
    val vibrationPattern: VibrationPattern = VibrationPattern.DEFAULT,
) {
    val plays: Boolean get() = sound != AlarmSound.SILENT
    val vibrates: Boolean get() = vibrationEnabled
}

/** A vibration waveform: [timings] with one [amplitudes] entry per timing (0 = off). */
class VibrationWave(val timings: LongArray, val amplitudes: IntArray)

object VibrationMapper {
    /** Odd indices are "on" segments and take the strength amplitude; even ones are gaps. */
    fun wave(pattern: VibrationPattern, strength: VibrationStrength): VibrationWave {
        val timings = pattern.timings.copyOf()
        val amplitudes = IntArray(timings.size) { index ->
            if (index % 2 == 1) strength.amplitude else 0
        }
        return VibrationWave(timings, amplitudes)
    }
}
