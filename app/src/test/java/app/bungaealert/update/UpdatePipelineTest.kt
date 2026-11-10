package app.bungaealert.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdatePipelineTest {
    private val cert = "same-cert".toByteArray()
    private val installed = ArchiveIdentity("app.bungaealert", 3, CertificateDigest.of(listOf(cert)))
    private val newer = installed.copy(versionCode = 4)

    @Test
    fun installsOnlyAfterTheHashCertificateAndVersionMatch() {
        val bytes = "signed-apk".toByteArray()
        val hash = Checksums.sha256(bytes)
        val dest = temp("update.apk")
        var installedFile: File? = null
        val phases = mutableListOf<String>()
        var sawProgress = false
        try {
            val outcome = run(
                bytes = bytes,
                sums = "$hash  bungae-alert-v0.2.0-vc4.apk\n",
                dest = dest,
                archive = newer,
                onProgress = { _, _ -> sawProgress = true },
                onPhase = { phases += it },
            ) { installedFile = it }
            assertTrue(outcome is UpdateOutcome.Started)
            assertEquals(dest, installedFile)
            assertTrue(dest.isFile)
            assertTrue(sawProgress)
            assertEquals(listOf(UpdateCopy.RECEIVING, UpdateCopy.CHECKING), phases)
        } finally {
            dest.delete()
        }
    }

    @Test
    fun wrongHashDoesNotInstallAndDeletesTheFile() {
        val dest = temp("bad.apk")
        var called = false
        try {
            val outcome = run(
                bytes = "tampered".toByteArray(),
                sums = "${"0".repeat(64)}  bungae-alert-v0.2.0-vc4.apk\n",
                dest = dest,
                archive = newer,
            ) { called = true }
            assertEquals(UpdateCopy.BAD_FILE, (outcome as UpdateOutcome.Failed).message)
            assertFalse(called)
            assertFalse(dest.exists())
        } finally {
            dest.delete()
        }
    }

    @Test
    fun certificateMismatchDoesNotInstall() {
        val bytes = "other-signer".toByteArray()
        val dest = temp("other.apk")
        var called = false
        try {
            val outcome = run(
                bytes = bytes,
                sums = "${Checksums.sha256(bytes)}  bungae-alert-v0.2.0-vc4.apk\n",
                dest = dest,
                archive = newer.copy(certificateSha256 = CertificateDigest.of(listOf("nope".toByteArray()))),
            ) { called = true }
            assertEquals(UpdateCopy.WRONG_AUTHOR, (outcome as UpdateOutcome.Failed).message)
            assertFalse(called)
            assertFalse(dest.exists())
        } finally {
            dest.delete()
        }
    }

    @Test
    fun refusesAFileThatIsNotNewer() {
        val bytes = "same-version".toByteArray()
        val dest = temp("same.apk")
        try {
            val outcome = run(
                bytes = bytes,
                sums = "${Checksums.sha256(bytes)}  bungae-alert-v0.2.0-vc4.apk\n",
                dest = dest,
                archive = installed.copy(versionCode = 3),
            ) { }
            assertEquals(UpdateCopy.NOT_NEWER, (outcome as UpdateOutcome.Failed).message)
            assertFalse(dest.exists())
        } finally {
            dest.delete()
        }
    }

    @Test
    fun refusesAnAddressThatIsNotHttps() {
        val dest = temp("skip.apk")
        var downloaded = false
        try {
            val outcome = UpdatePipeline.run(
                plan = UpdatePlan("http://example.com/app.apk", "app.apk", "https://example.com/SHA256SUMS"),
                dest = dest,
                download = UpdateDownloaderFn { _, _, _ -> downloaded = true },
                fetchText = UpdateTextFn { "" },
                archive = { newer },
                installed = installed,
                expectedPackage = "app.bungaealert",
                install = UpdateInstallFn { },
            )
            assertEquals(UpdateCopy.NO_FILE, (outcome as UpdateOutcome.Failed).message)
            assertFalse(downloaded)
        } finally {
            dest.delete()
        }
    }

    private fun run(
        bytes: ByteArray,
        sums: String,
        dest: File,
        archive: ArchiveIdentity?,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onPhase: (String) -> Unit = {},
        install: (File) -> Unit,
    ): UpdateOutcome {
        return UpdatePipeline.run(
            plan = UpdatePlan(
                apkUrl = "https://example.com/bungae-alert-v0.2.0-vc4.apk",
                apkName = "bungae-alert-v0.2.0-vc4.apk",
                checksumUrl = "https://example.com/SHA256SUMS",
            ),
            dest = dest,
            download = UpdateDownloaderFn { _, file, progress ->
                file.writeBytes(bytes)
                progress(bytes.size.toLong(), bytes.size.toLong())
            },
            fetchText = UpdateTextFn { sums },
            archive = { archive },
            installed = installed,
            expectedPackage = "app.bungaealert",
            install = UpdateInstallFn(install),
            onProgress = onProgress,
            onPhase = onPhase,
        )
    }

    private fun temp(name: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "bungae-update-test")
        dir.mkdirs()
        return File(dir, name)
    }
}
