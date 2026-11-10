package app.bungaealert.update

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

class ApkTrustTest {
    private val cert = "phone-cert".toByteArray()
    private val other = "someone-else".toByteArray()
    private val installed = ArchiveIdentity(
        packageName = "app.bungaealert",
        versionCode = 3,
        certificateSha256 = CertificateDigest.of(listOf(cert)),
    )

    @Test
    fun sameCertificateAndHigherVersionIsTrusted() {
        val archive = installed.copy(versionCode = 4)
        assertTrue(ApkTrust.evaluate(archive, installed, "app.bungaealert") is ApkTrust.Verdict.Trusted)
    }

    @Test
    fun certificateMismatchIsRejected() {
        val archive = installed.copy(
            versionCode = 4,
            certificateSha256 = CertificateDigest.of(listOf(other)),
        )
        val verdict = ApkTrust.evaluate(archive, installed, "app.bungaealert")
        assertEquals(UpdateCopy.WRONG_AUTHOR, (verdict as ApkTrust.Verdict.Rejected).message)
        assertTrue(installed.certificateSha256.single() != archive.certificateSha256.single())
    }

    @Test
    fun packageAndVersionMismatchesAreRejected() {
        val otherPackage = installed.copy(packageName = "other.app", versionCode = 4)
        assertEquals(
            UpdateCopy.WRONG_APP,
            (ApkTrust.evaluate(otherPackage, installed, "app.bungaealert") as ApkTrust.Verdict.Rejected).message,
        )
        val same = installed.copy(versionCode = 3)
        assertEquals(
            UpdateCopy.NOT_NEWER,
            (ApkTrust.evaluate(same, installed, "app.bungaealert") as ApkTrust.Verdict.Rejected).message,
        )
        val older = installed.copy(versionCode = 2)
        assertEquals(
            UpdateCopy.NOT_NEWER,
            (ApkTrust.evaluate(older, installed, "app.bungaealert") as ApkTrust.Verdict.Rejected).message,
        )
    }

    @Test
    fun missingArchiveOrCertificatesAreRejected() {
        assertEquals(
            UpdateCopy.UNREADABLE,
            (ApkTrust.evaluate(null, installed, "app.bungaealert") as ApkTrust.Verdict.Rejected).message,
        )
        val blank = installed.copy(versionCode = 4, certificateSha256 = emptySet())
        assertEquals(
            UpdateCopy.UNREADABLE,
            (ApkTrust.evaluate(blank, installed, "app.bungaealert") as ApkTrust.Verdict.Rejected).message,
        )
        assertEquals(
            UpdateCopy.INSTALLED_UNKNOWN,
            (ApkTrust.evaluate(installed.copy(versionCode = 4), null, "app.bungaealert") as ApkTrust.Verdict.Rejected).message,
        )
    }

    @Test
    fun fingerprintUsesSha256OfTheCertificateBytes() {
        assertEquals(setOf(Checksums.sha256(cert)), CertificateDigest.of(listOf(cert)))
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26], application = android.app.Application::class)
class ApkArchiveIdentityTest {
    @Test
    fun readsPackageVersionAndCertificateFromTheArchiveInfo() {
        val cert = "archive-cert".toByteArray()
        val info = PackageInfo()
        info.packageName = "app.bungaealert"
        @Suppress("DEPRECATION")
        info.versionCode = 9
        @Suppress("DEPRECATION")
        info.signatures = arrayOf(Signature(cert))
        val identity = ApkArchive.identity(info)
        assertEquals("app.bungaealert", identity!!.packageName)
        assertEquals(9L, identity.versionCode)
        assertEquals(setOf(Checksums.sha256(cert)), identity.certificateSha256)
        assertTrue(Build.VERSION.SDK_INT < 28)
    }
}
