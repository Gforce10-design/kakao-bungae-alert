package app.bungaealert.update

data class ArchiveIdentity(
    val packageName: String,
    val versionCode: Long,
    val certificateSha256: Set<String>,
)

object CertificateDigest {
    fun of(encodedCerts: Collection<ByteArray>): Set<String> {
        return encodedCerts.map { Checksums.sha256(it) }.toSet()
    }
}

object ApkTrust {
    sealed interface Verdict {
        data object Trusted : Verdict
        data class Rejected(val message: String) : Verdict
    }

    fun evaluate(
        archive: ArchiveIdentity?,
        installed: ArchiveIdentity?,
        expectedPackage: String,
    ): Verdict {
        if (installed == null || installed.packageName.isBlank() || installed.versionCode <= 0L) {
            return Verdict.Rejected(UpdateCopy.INSTALLED_UNKNOWN)
        }
        if (archive == null || archive.packageName.isBlank() || archive.versionCode <= 0L) {
            return Verdict.Rejected(UpdateCopy.UNREADABLE)
        }
        if (archive.packageName != expectedPackage || archive.packageName != installed.packageName) {
            return Verdict.Rejected(UpdateCopy.WRONG_APP)
        }
        if (archive.certificateSha256.isEmpty() || installed.certificateSha256.isEmpty()) {
            return Verdict.Rejected(UpdateCopy.UNREADABLE)
        }
        val archiveCerts = archive.certificateSha256.map { it.lowercase() }.toSet()
        val installedCerts = installed.certificateSha256.map { it.lowercase() }.toSet()
        if (archiveCerts != installedCerts) {
            return Verdict.Rejected(UpdateCopy.WRONG_AUTHOR)
        }
        if (!VersionCodes.isNewer(archive.versionCode, installed.versionCode)) {
            return Verdict.Rejected(UpdateCopy.NOT_NEWER)
        }
        return Verdict.Trusted
    }
}
