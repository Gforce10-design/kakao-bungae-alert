package app.bungaealert.update

sealed interface UpdateDecision {
    val message: String
    val pageUrl: String?
        get() = null

    data class Notify(
        val tag: String,
        val versionCode: Int,
        val htmlUrl: String,
        override val message: String,
        val apkUrl: String? = null,
        val apkName: String? = null,
        val checksumUrl: String? = null,
    ) : UpdateDecision {
        override val pageUrl: String get() = htmlUrl
    }

    data class Current(override val message: String) : UpdateDecision

    data class AlreadySent(
        val tag: String,
        val versionCode: Int,
        val htmlUrl: String,
        override val message: String,
        val apkUrl: String? = null,
        val apkName: String? = null,
        val checksumUrl: String? = null,
    ) : UpdateDecision {
        override val pageUrl: String get() = htmlUrl
    }

    data class NeedsPermission(
        val tag: String,
        val versionCode: Int,
        val htmlUrl: String,
        override val message: String,
        val apkUrl: String? = null,
        val apkName: String? = null,
        val checksumUrl: String? = null,
    ) : UpdateDecision {
        override val pageUrl: String get() = htmlUrl
    }

    data class Unavailable(override val message: String) : UpdateDecision
}

object UpdatePolicy {
    fun decide(
        query: ReleaseQuery,
        installedVersionCode: Int,
        notifiedVersionCode: Int?,
        canNotify: Boolean,
    ): UpdateDecision {
        return when (query) {
            ReleaseQuery.NotFound, ReleaseQuery.Unauthorized ->
                UpdateDecision.Unavailable("릴리스를 확인하지 못했습니다")
            ReleaseQuery.RateLimited ->
                UpdateDecision.Unavailable("GitHub 요청 한도에 걸렸습니다. 나중에 다시 확인하세요")
            is ReleaseQuery.Failed -> UpdateDecision.Unavailable("업데이트를 확인하지 못했습니다")
            is ReleaseQuery.Found -> compare(query, installedVersionCode, notifiedVersionCode, canNotify)
        }
    }

    private fun compare(
        query: ReleaseQuery.Found,
        installedVersionCode: Int,
        notifiedVersionCode: Int?,
        canNotify: Boolean,
    ): UpdateDecision {
        if (!VersionCodes.isNewer(query.versionCode, installedVersionCode)) {
            val message = if (query.versionCode == installedVersionCode) {
                "최신 버전입니다"
            } else {
                "설치된 버전이 더 높습니다"
            }
            return UpdateDecision.Current(message)
        }
        val label = "${SemanticVersion.display(query.tag)} (${query.versionCode})"
        if (notifiedVersionCode == query.versionCode) {
            return UpdateDecision.AlreadySent(
                tag = query.tag,
                versionCode = query.versionCode,
                htmlUrl = query.htmlUrl,
                message = "새 버전 $label 알림을 이미 보냈습니다",
                apkUrl = query.apkUrl,
                apkName = query.apkName,
                checksumUrl = query.checksumUrl,
            )
        }
        if (!canNotify) {
            return UpdateDecision.NeedsPermission(
                tag = query.tag,
                versionCode = query.versionCode,
                htmlUrl = query.htmlUrl,
                message = "새 버전 ${label}가 있습니다. 알림 허용 후 다시 확인하세요",
                apkUrl = query.apkUrl,
                apkName = query.apkName,
                checksumUrl = query.checksumUrl,
            )
        }
        return UpdateDecision.Notify(
            tag = query.tag,
            versionCode = query.versionCode,
            htmlUrl = query.htmlUrl,
            message = "새 버전 $label 알림을 보냈습니다",
            apkUrl = query.apkUrl,
            apkName = query.apkName,
            checksumUrl = query.checksumUrl,
        )
    }
}

object UpdateThrottle {
    const val START_INTERVAL_MS = 6L * 60L * 60L * 1000L

    fun due(lastCheckAtMillis: Long, nowMillis: Long, intervalMs: Long = START_INTERVAL_MS): Boolean {
        if (lastCheckAtMillis <= 0L) return true
        if (nowMillis < lastCheckAtMillis) return true
        return nowMillis - lastCheckAtMillis >= intervalMs
    }
}
