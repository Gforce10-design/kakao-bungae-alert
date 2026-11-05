package app.bungaealert.detect

import androidx.test.core.app.ApplicationProvider
import app.bungaealert.data.AppSettings
import app.bungaealert.data.SettingsRepository
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class AlertFeelTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun wireValuesRoundTripAndUnknownFallsBackToDefault() {
        AlarmSound.entries.forEach { assertEquals(it, AlarmSound.fromWire(it.wire)) }
        VibrationStrength.entries.forEach { assertEquals(it, VibrationStrength.fromWire(it.wire)) }
        VibrationPattern.entries.forEach { assertEquals(it, VibrationPattern.fromWire(it.wire)) }
        assertEquals(AlarmSound.SYSTEM, AlarmSound.fromWire("nope"))
        assertEquals(AlarmSound.SYSTEM, AlarmSound.fromWire(null))
        assertEquals(VibrationStrength.STRONG, VibrationStrength.fromWire(""))
        assertEquals(VibrationPattern.LONG, VibrationPattern.fromWire("zzz"))
    }

    @Test
    fun strengthAmplitudesAreOrderedAndValid() {
        val a = VibrationStrength.entries.map { it.amplitude }
        assertEquals(a.sorted(), a)
        assertTrue(a.all { it in 1..255 })
        assertEquals(listOf("약", "중", "강"), VibrationStrength.entries.map { it.label })
    }

    @Test
    fun waveformPutsStrengthOnTheOnSegmentsOnly() {
        VibrationPattern.entries.forEach { pattern ->
            VibrationStrength.entries.forEach { strength ->
                val wave = VibrationMapper.wave(pattern, strength)
                assertArrayEquals(pattern.timings, wave.timings)
                assertEquals(wave.timings.size, wave.amplitudes.size)
                wave.amplitudes.forEachIndexed { i, amp ->
                    assertEquals(if (i % 2 == 1) strength.amplitude else 0, amp)
                }
                assertEquals(0L, wave.timings[0])
                assertTrue(wave.timings.size % 2 == 1 || wave.timings.size >= 3)
            }
        }
    }

    @Test
    fun patternsAreDistinct() {
        val all = VibrationPattern.entries.map { it.timings.toList() }
        assertEquals(all.size, all.toSet().size)
        assertEquals(listOf("짧게", "길게", "반복", "하트비트"), VibrationPattern.entries.map { it.label })
    }

    @Test
    fun soundsHaveBundledRawNamesExceptSystemAndSilent() {
        assertEquals(null, AlarmSound.SYSTEM.rawName)
        assertEquals(null, AlarmSound.SILENT.rawName)
        assertEquals(3, AlarmSound.entries.count { it.rawName != null })
        assertFalse(AlertFeel(sound = AlarmSound.SILENT).plays)
        assertFalse(AlertFeel(vibrationEnabled = false).vibrates)
    }

    @Test
    fun settingsJsonRoundTripsAlertFeel() {
        val settings = AppSettings(
            alarmSound = AlarmSound.BELL,
            vibrationEnabled = false,
            vibrationStrength = VibrationStrength.WEAK,
            vibrationPattern = VibrationPattern.HEARTBEAT,
        )
        val back = SettingsRepository.fromJson(JSONObject(SettingsRepository.toJson(settings).toString()))
        assertEquals(AlarmSound.BELL, back.alarmSound)
        assertFalse(back.vibrationEnabled)
        assertEquals(VibrationStrength.WEAK, back.vibrationStrength)
        assertEquals(VibrationPattern.HEARTBEAT, back.vibrationPattern)
        assertEquals(AlertFeel(AlarmSound.BELL, false, VibrationStrength.WEAK, VibrationPattern.HEARTBEAT), back.feel)
    }

    @Test
    fun legacySettingsWithoutAlertFieldsKeepOldBehaviour() {
        val legacy = JSONObject().put("sensitivity", 55).put("maxRingSeconds", 90)
        val settings = SettingsRepository.fromJson(legacy)
        assertEquals(55, settings.sensitivity)
        assertEquals(AlertFeel(), settings.feel)
        assertEquals(AlarmSound.SYSTEM, settings.alarmSound)
        assertTrue(settings.vibrationEnabled)
    }

    @Test
    fun repositoryPersistsAndReadsFreshAtAlarmTime() {
        val repo = SettingsRepository(context)
        assertEquals(AlertFeel(), repo.current().feel)
        repo.update(repo.current().copy(alarmSound = AlarmSound.BEEP, vibrationStrength = VibrationStrength.MEDIUM, vibrationPattern = VibrationPattern.REPEAT))
        // The next alarm reads current() with no restart in between.
        assertEquals(AlarmSound.BEEP, repo.current().feel.sound)
        assertEquals(VibrationPattern.REPEAT, repo.current().feel.vibrationPattern)
        // A fresh repository (process restart) sees the same values.
        val reloaded = SettingsRepository(context).current()
        assertEquals(AlarmSound.BEEP, reloaded.alarmSound)
        assertEquals(VibrationStrength.MEDIUM, reloaded.vibrationStrength)
        assertEquals(VibrationPattern.REPEAT, reloaded.vibrationPattern)
        assertTrue(reloaded.vibrationEnabled)
        // Other settings survive an alert-feel change.
        repo.update(repo.current().copy(vibrationEnabled = false, sensitivity = 61))
        val again = SettingsRepository(context).current()
        assertFalse(again.vibrationEnabled)
        assertEquals(61, again.sensitivity)
        assertEquals(AlarmSound.BEEP, again.alarmSound)
    }
}
