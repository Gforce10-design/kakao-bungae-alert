package app.bungaealert.update

import java.io.File
import java.net.URI

object UpdateCopy {
    const val NO_FILE = "설치 파일 주소를 찾지 못했습니다."
    const val DOWNLOAD_FAILED = "설치 파일을 받지 못했습니다."
    const val CHECKSUM_FAILED = "확인 파일을 받지 못했습니다."
    const val BAD_FILE = "받은 파일이 올바르지 않습니다."
    const val UNREADABLE = "설치 파일을 확인하지 못했습니다."
    const val WRONG_APP = "이 파일은 이 앱이 아닙니다."
    const val WRONG_AUTHOR = "이 파일은 지금 설치된 앱과 같은 제작자의 파일이 아닙니다."
    const val NOT_NEWER = "이 파일은 지금 앱보다 새 버전이 아닙니다."
    const val INSTALLED_UNKNOWN = "설치된 앱 정보를 읽지 못했습니다."
    const val INSTALL_FAILED = "설치를 시작하지 못했습니다."
    const val CONFIRM = "설치 확인 창에서 설치를 눌러 주세요."
    const val ALLOW = "업데이트를 설치하려면 다음 화면에서 이 앱을 허용해 주세요."
    const val ALLOW_DENIED = "설치 허용이 꺼져 있어 업데이트를 진행하지 못했습니다."
    const val ALLOW_SCREEN = "설치 허용 화면을 열지 못했습니다."
    const val TOO_BIG = "설치 파일이 너무 큽니다."
    const val RECEIVING = "설치 파일을 받고 있습니다."
    const val CHECKING = "파일을 확인하고 있습니다."
    const val INSTALL_REJECTED = "설치가 되지 않았습니다."
    const val CONFIRM_FAILED = "설치 확인 창을 열지 못했습니다."
}

class UpdateTransferException(
    val kind: Kind,
    message: String? = null,
) : java.io.IOException(message) {
    enum class Kind { DOWNLOAD, CHECKSUM, TOO_BIG }
}

object HttpsOnly {
    fun accept(url: String?): Boolean {
        val value = url?.trim().orEmpty()
        if (!value.startsWith("https://")) return false
        if (value.any { it.isWhitespace() || it == '@' }) return false
        return try {
            val uri = URI(value)
            uri.scheme == "https" && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}

data class UpdatePlan(
    val apkUrl: String,
    val apkName: String,
    val checksumUrl: String,
)

sealed interface UpdateOutcome {
    data object Started : UpdateOutcome
    data class Failed(val message: String) : UpdateOutcome
}

fun interface UpdateDownloaderFn {
    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit)
}

fun interface UpdateTextFn {
    fun fetch(url: String): String
}

fun interface UpdateInstallFn {
    fun install(apk: File)
}

object UpdatePipeline {
    fun run(
        plan: UpdatePlan,
        dest: File,
        download: UpdateDownloaderFn,
        fetchText: UpdateTextFn,
        digest: (File) -> String = { file -> Checksums.sha256(file) },
        archive: (File) -> ArchiveIdentity?,
        installed: ArchiveIdentity?,
        expectedPackage: String,
        install: UpdateInstallFn,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onPhase: (String) -> Unit = {},
    ): UpdateOutcome {
        if (!HttpsOnly.accept(plan.apkUrl) || !HttpsOnly.accept(plan.checksumUrl) || plan.apkName.isBlank()) {
            return UpdateOutcome.Failed(UpdateCopy.NO_FILE)
        }
        dest.parentFile?.mkdirs()
        onPhase(UpdateCopy.RECEIVING)
        try {
            download.download(plan.apkUrl, dest, onProgress)
        } catch (e: UpdateTransferException) {
            dest.delete()
            val message = if (e.kind == UpdateTransferException.Kind.TOO_BIG) UpdateCopy.TOO_BIG else UpdateCopy.DOWNLOAD_FAILED
            return UpdateOutcome.Failed(message)
        } catch (_: Exception) {
            dest.delete()
            return UpdateOutcome.Failed(UpdateCopy.DOWNLOAD_FAILED)
        }
        if (!dest.isFile || dest.length() <= 0L) {
            dest.delete()
            return UpdateOutcome.Failed(UpdateCopy.DOWNLOAD_FAILED)
        }
        onPhase(UpdateCopy.CHECKING)
        val sums = try {
            fetchText.fetch(plan.checksumUrl)
        } catch (_: Exception) {
            dest.delete()
            return UpdateOutcome.Failed(UpdateCopy.CHECKSUM_FAILED)
        }
        val actual = try {
            digest(dest)
        } catch (_: Exception) {
            dest.delete()
            return UpdateOutcome.Failed(UpdateCopy.BAD_FILE)
        }
        if (!Checksums.matches(sums, plan.apkName, actual)) {
            dest.delete()
            return UpdateOutcome.Failed(UpdateCopy.BAD_FILE)
        }
        val identity = try {
            archive(dest)
        } catch (_: Exception) {
            dest.delete()
            return UpdateOutcome.Failed(UpdateCopy.UNREADABLE)
        }
        when (val verdict = ApkTrust.evaluate(identity, installed, expectedPackage)) {
            is ApkTrust.Verdict.Rejected -> {
                dest.delete()
                return UpdateOutcome.Failed(verdict.message)
            }
            ApkTrust.Verdict.Trusted -> Unit
        }
        return try {
            install.install(dest)
            UpdateOutcome.Started
        } catch (_: Exception) {
            UpdateOutcome.Failed(UpdateCopy.INSTALL_FAILED)
        }
    }
}
