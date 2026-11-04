package app.bungaealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionCodeTest {
    @Test
    fun newerCodeIsGreater() {
        assertTrue(VersionCodes.isNewer(43, 42))
        assertFalse(VersionCodes.isNewer(42, 42))
        assertFalse(VersionCodes.isNewer(41, 42))
        assertFalse(VersionCodes.isNewer(0, 1))
    }

    @Test
    fun bodyVersionCodeBeatsTheAssetName() {
        assertEquals(
            42,
            VersionCodes.parse(
                body = "번개알림 v1.4.0\nversionCode: 42\n",
                assetNames = listOf("bungae-alert-v1.4.0-vc7.apk", "SHA256SUMS"),
            ),
        )
        assertEquals(7, VersionCodes.parse("versionCode = 7", emptyList()))
        assertEquals(7, VersionCodes.parse("VERSIONCODE: 7", emptyList()))
    }

    @Test
    fun assetNameIsUsedWhenTheBodyHasNoCode() {
        assertEquals(
            15,
            VersionCodes.parse(
                body = "첨부 APK를 받아 설치합니다.",
                assetNames = listOf("SHA256SUMS", "bungae-alert-v1.2.0-vc15.apk"),
            ),
        )
        assertEquals(15, VersionCodes.parse("", listOf("vc15.apk")))
    }

    @Test
    fun missingOrNonPositiveCodesAreIgnored() {
        assertNull(VersionCodes.parse("versionCode: none", listOf("app.apk")))
        assertNull(VersionCodes.parse("versionCode: 0", listOf("vc0.apk")))
        assertNull(VersionCodes.parse("versionCode:", listOf("bungae-alert-v1.apk")))
        assertNull(VersionCodes.parse("", emptyList()))
    }
}

class UpdateThrottleTest {
    @Test
    fun firstCheckAndStaleCheckAreDue() {
        assertTrue(UpdateThrottle.due(lastCheckAtMillis = 0L, nowMillis = 1_000L, intervalMs = 100L))
        assertTrue(UpdateThrottle.due(lastCheckAtMillis = 1_000L, nowMillis = 1_100L, intervalMs = 100L))
        assertFalse(UpdateThrottle.due(lastCheckAtMillis = 1_000L, nowMillis = 1_099L, intervalMs = 100L))
    }

    @Test
    fun clockGoingBackwardsChecksAgain() {
        assertTrue(UpdateThrottle.due(lastCheckAtMillis = 5_000L, nowMillis = 4_000L, intervalMs = 100L))
    }
}
