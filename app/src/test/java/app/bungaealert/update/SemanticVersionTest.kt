package app.bungaealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {
    @Test
    fun stripsLeadingVAndIgnoresBuildMetadata() {
        assertEquals(0, SemanticVersion.parse("v1.2.3")!!.compareTo(SemanticVersion.parse("1.2.3+build.9")!!))
        assertEquals("v1.2.3", SemanticVersion.display("V1.2.3"))
        assertEquals("1.2.3", SemanticVersion.parse("v1.2.3")!!.normalized())
    }

    @Test
    fun comparesCoreNumbers() {
        assertTrue(SemanticVersion.isNewer("v1.2.0", "1.0.0"))
        assertTrue(SemanticVersion.isNewer("1.10.0", "1.9.0"))
        assertTrue(SemanticVersion.isNewer("1.2", "1.1.9"))
        assertFalse(SemanticVersion.isNewer("v1.0.0", "1.0.0"))
        assertFalse(SemanticVersion.isNewer("v0.9.0", "1.0.0"))
        assertFalse(SemanticVersion.isNewer("1.2.3", "1.2.3"))
    }

    @Test
    fun preReleaseIsOlderThanTheRelease() {
        assertTrue(SemanticVersion.parse("1.0.0-beta")!! < SemanticVersion.parse("1.0.0")!!)
        assertTrue(SemanticVersion.parse("1.0.0-alpha")!! < SemanticVersion.parse("1.0.0-beta")!!)
        assertTrue(SemanticVersion.parse("1.0.0-alpha.1")!! < SemanticVersion.parse("1.0.0-alpha.2")!!)
        assertTrue(SemanticVersion.parse("1.0.0-alpha.1")!! < SemanticVersion.parse("1.0.0-alpha.beta")!!)
        assertTrue(SemanticVersion.parse("1.0.0-1")!! < SemanticVersion.parse("1.0.0-alpha")!!)
        assertFalse(SemanticVersion.isNewer("v1.2.0-rc.1", "1.2.0"))
        assertTrue(SemanticVersion.isNewer("v1.2.0", "1.2.0-rc.1"))
    }

    @Test
    fun rejectsUnusableTags() {
        assertNull(SemanticVersion.parse(""))
        assertNull(SemanticVersion.parse("v"))
        assertNull(SemanticVersion.parse("latest"))
        assertNull(SemanticVersion.parse("1..0"))
        assertNull(SemanticVersion.parse("1.0.0-"))
        assertFalse(SemanticVersion.isNewer("not-a-version", "1.0.0"))
        assertFalse(SemanticVersion.isNewer("v1.2.0", "installed"))
    }
}
