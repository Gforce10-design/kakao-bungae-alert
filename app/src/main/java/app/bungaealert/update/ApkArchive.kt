package app.bungaealert.update

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build

object ApkArchive {
    fun read(pm: PackageManager, apkPath: String): ArchiveIdentity? {
        val info = pm.getPackageArchiveInfo(apkPath, flags()) ?: return null
        info.applicationInfo?.let { appInfo ->
            appInfo.sourceDir = apkPath
            appInfo.publicSourceDir = apkPath
        }
        return identity(info)
    }

    fun installed(pm: PackageManager, packageName: String): ArchiveIdentity? {
        val info = try {
            pm.getPackageInfo(packageName, flags())
        } catch (_: PackageManager.NameNotFoundException) {
            return null
        }
        return identity(info)
    }

    @Suppress("DEPRECATION")
    private fun flags(): Int {
        return if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
    }

    fun identity(info: PackageInfo): ArchiveIdentity? {
        val name = info.packageName.trim()
        if (name.isEmpty()) return null
        val version = versionCode(info)
        if (version <= 0L) return null
        return ArchiveIdentity(name, version, CertificateDigest.of(certificateBytes(info)))
    }

    @Suppress("DEPRECATION")
    fun versionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    @Suppress("DEPRECATION")
    fun certificateBytes(info: PackageInfo): List<ByteArray> {
        if (Build.VERSION.SDK_INT >= 28) {
            val signing = info.signingInfo
            if (signing != null) {
                val current = signing.apkContentsSigners
                if (!current.isNullOrEmpty()) return current.map { it.toByteArray() }
                val history = signing.signingCertificateHistory
                if (!history.isNullOrEmpty()) return listOf(history.last().toByteArray())
            }
        }
        val legacy = info.signatures
        if (!legacy.isNullOrEmpty()) return legacy.map { it.toByteArray() }
        return emptyList()
    }
}
