package app.bungaealert.update

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import app.bungaealert.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

object UpdatePermissions {
    fun allowed(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSources(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
    }
}

data class UpdateUi(
    val busy: Boolean = false,
    val progress: Int? = null,
    val message: String = "",
)

object UpdateController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateFlow = MutableStateFlow(UpdateUi())
    val state: StateFlow<UpdateUi> = stateFlow

    fun dismiss() {
        if (!stateFlow.value.busy) stateFlow.value = UpdateUi()
    }

    fun show(message: String) {
        if (stateFlow.value.busy) return
        stateFlow.value = UpdateUi(message = message)
    }

    fun start(context: Context) {
        if (stateFlow.value.busy) return
        val app = context.applicationContext
        scope.launch {
            stateFlow.value = UpdateUi(busy = true, message = UpdateCopy.RECEIVING)
            val outcome = try {
                withContext(Dispatchers.IO) {
                    InAppUpdate.run(
                        context = app,
                        onProgress = { read, total ->
                            val progress = if (total > 0L) ((read * 100) / total).toInt().coerceIn(0, 100) else null
                            val message = if (progress == null) UpdateCopy.RECEIVING else "${UpdateCopy.RECEIVING} $progress%"
                            stateFlow.value = UpdateUi(busy = true, message = message, progress = progress)
                        },
                        onPhase = { message ->
                            val progress = stateFlow.value.progress
                            stateFlow.value = UpdateUi(busy = true, message = message, progress = progress)
                        },
                    )
                }
            } catch (t: Throwable) {
                Log.w(TAG, "in-app update failed", t)
                UpdateOutcome.Failed(UpdateCopy.INSTALL_FAILED)
            }
            when (outcome) {
                UpdateOutcome.Started -> stateFlow.value = UpdateUi(message = UpdateCopy.CONFIRM)
                is UpdateOutcome.Failed -> {
                    Log.i(TAG, "in-app update stopped: ${outcome.message}")
                    stateFlow.value = UpdateUi(message = outcome.message)
                }
            }
        }
    }

    private const val TAG = "BungaeUpdate"
}

object InAppUpdate {
    fun run(
        context: Context,
        onProgress: (Long, Long) -> Unit = { _, _ -> },
        onPhase: (String) -> Unit = {},
    ): UpdateOutcome {
        val plan = resolve(context) ?: return UpdateOutcome.Failed(UpdateCopy.NO_FILE)
        val dest = File(File(context.cacheDir, "updates"), "update.apk")
        val manager = context.packageManager
        return UpdatePipeline.run(
            plan = plan,
            dest = dest,
            download = UpdateDownloaderFn { url, file, progress -> UpdateDownloader.download(url, file, progress) },
            fetchText = UpdateTextFn { url -> UpdateDownloader.readText(url) },
            archive = { file -> ApkArchive.read(manager, file.absolutePath) },
            installed = ApkArchive.installed(manager, context.packageName),
            expectedPackage = context.packageName,
            install = UpdateInstallFn { file -> SessionInstall.commit(context, file) },
            onProgress = onProgress,
            onPhase = onPhase,
        )
    }

    internal fun resolve(context: Context): UpdatePlan? {
        var saved = UpdateChecks.savedUpdate(context)
        if (saved == null || !HttpsOnly.accept(saved.apkUrl) || !HttpsOnly.accept(saved.checksumUrl)) {
            val latest = try {
                GithubReleaseSource().latest()
            } catch (_: Throwable) {
                null
            }
            if (latest is ReleaseQuery.Found && VersionCodes.isNewer(latest.versionCode, BuildConfig.VERSION_CODE)) {
                val update = AvailableUpdate(
                    versionCode = latest.versionCode,
                    tag = latest.tag,
                    url = latest.htmlUrl,
                    apkUrl = latest.apkUrl?.takeIf(HttpsOnly::accept).orEmpty(),
                    apkName = latest.apkName.orEmpty(),
                    checksumUrl = latest.checksumUrl?.takeIf(HttpsOnly::accept).orEmpty(),
                )
                UpdatePrefs.saveAvailable(context, update)
                saved = update.takeIf { VersionCodes.isNewer(it.versionCode, BuildConfig.VERSION_CODE) }
            }
        }
        val current = saved ?: return null
        if (!HttpsOnly.accept(current.apkUrl) || !HttpsOnly.accept(current.checksumUrl)) return null
        val name = current.apkName.ifBlank { fileName(current.apkUrl) }
        if (name.isBlank() || !name.endsWith(".apk", ignoreCase = true)) return null
        return UpdatePlan(current.apkUrl, name, current.checksumUrl)
    }

    private fun fileName(url: String): String {
        val path = try {
            URI(url).rawPath ?: return ""
        } catch (_: Exception) {
            return ""
        }
        val leaf = path.substringAfterLast('/').substringBefore('?').trim()
        if (leaf.isEmpty() || leaf.contains("..")) return ""
        return try {
            URLDecoder.decode(leaf, Charsets.UTF_8.name())
        } catch (_: Exception) {
            leaf
        }
    }
}

object UpdateDownloader {
    const val MAX_BYTES = 192L * 1024L * 1024L

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        if (!HttpsOnly.accept(url)) throw UpdateTransferException(UpdateTransferException.Kind.DOWNLOAD)
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "kakao-bungae-alert")
            .header("Accept", "application/octet-stream, */*")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw UpdateTransferException(UpdateTransferException.Kind.DOWNLOAD, "HTTP ${response.code}")
                }
                val body = response.body ?: throw UpdateTransferException(UpdateTransferException.Kind.DOWNLOAD)
                val total = body.contentLength()
                if (total > MAX_BYTES) throw UpdateTransferException(UpdateTransferException.Kind.TOO_BIG)
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var readTotal = 0L
                        var lastMark = -1
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            readTotal += count
                            if (readTotal > MAX_BYTES) throw UpdateTransferException(UpdateTransferException.Kind.TOO_BIG)
                            output.write(buffer, 0, count)
                            val mark = if (total > 0L) ((readTotal * 100) / total).toInt() else (readTotal / 262144L).toInt()
                            if (mark != lastMark) {
                                lastMark = mark
                                onProgress(readTotal, total)
                            }
                        }
                        onProgress(readTotal, if (total > 0L) total else readTotal)
                    }
                }
            }
            if (!part.isFile || part.length() <= 0L) throw UpdateTransferException(UpdateTransferException.Kind.DOWNLOAD)
            if (dest.exists() && !dest.delete()) throw UpdateTransferException(UpdateTransferException.Kind.DOWNLOAD)
            if (!part.renameTo(dest)) {
                part.copyTo(dest, overwrite = true)
                part.delete()
            }
        } catch (e: UpdateTransferException) {
            part.delete()
            throw e
        } catch (e: Exception) {
            part.delete()
            throw UpdateTransferException(UpdateTransferException.Kind.DOWNLOAD, e.message)
        }
    }

    fun readText(url: String): String {
        if (!HttpsOnly.accept(url)) throw UpdateTransferException(UpdateTransferException.Kind.CHECKSUM)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "kakao-bungae-alert")
            .header("Accept", "text/plain, */*")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateTransferException(UpdateTransferException.Kind.CHECKSUM)
            val text = response.body?.string().orEmpty()
            if (text.isBlank() || text.length > 1_000_000) {
                throw UpdateTransferException(UpdateTransferException.Kind.CHECKSUM)
            }
            return text
        }
    }
}

object SessionInstall {
    fun commit(context: Context, apk: File) {
        try {
            commitSession(context, apk)
        } catch (t: Throwable) {
            Log.w(TAG, "package session failed; opening the installer", t)
            if (!viewInstall(context, apk)) throw t
        }
    }

    private fun commitSession(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        val session = installer.openSession(sessionId)
        var committed = false
        try {
            session.openWrite("base.apk", 0, apk.length()).use { output ->
                apk.inputStream().use { input -> input.copyTo(output) }
                session.fsync(output)
            }
            val callback = android.app.PendingIntent.getActivity(
                context,
                sessionId,
                Intent(context, UpdateInstallTrampoline::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
            committed = true
        } catch (t: Throwable) {
            if (!committed) {
                try {
                    session.abandon()
                } catch (_: Exception) {
                }
            }
            throw t
        } finally {
            try {
                session.close()
            } catch (_: Exception) {
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun viewInstall(context: Context, apk: File): Boolean {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (start(context, view)) return true
        val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            data = uri
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        }
        return start(context, install)
    }

    private fun start(context: Context, intent: Intent): Boolean {
        return try {
            context.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private const val TAG = "BungaeUpdate"
    private const val APK_MIME = "application/vnd.android.package-archive"
}
