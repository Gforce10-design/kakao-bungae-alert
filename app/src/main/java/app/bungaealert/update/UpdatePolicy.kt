package app.bungaealert.update

sealed interface UpdateDecision {
    val message: String

    data class Notify(
        val tag: String,
        val normalized: String,
        val htmlUrl: String,
        override val message: String,
    ) : UpdateDecision

    data class Current(override val message: String) : UpdateDecision

    data class AlreadySent(
        val tag: String,
        val htmlUrl: String,
        override val message: String,
    ) : UpdateDecision

    data class NeedsPermission(
        val tag: String,
        val htmlUrl: String,
        override val message: String,
    ) : UpdateDecision

    data class Unavailable(override val message: String) : UpdateDecision
}

object UpdatePolicy {
    fun decide(
        query: ReleaseQuery,
        installedVersion: String,
        notifiedNormalized: String?,
        canNotify: Boolean,
    ): UpdateDecision {
        return when (query) {
            ReleaseQuery.NotFound -> UpdateDecision.Unavailable("배포된 릴리스가 없습니다")
            ReleaseQuery.RateLimited ->
                UpdateDecision.Unavailable("GitHub 요청 한도에 걸렸습니다. 나중에 다시 확인하세요")
            is ReleaseQuery.Failed -> UpdateDecision.Unavailable("업데이트를 확인하지 못했습니다")
            is ReleaseQuery.Found -> compare(query, installedVersion, notifiedNormalized, canNotify)
        }
    }

    private fun compare(
        query: ReleaseQuery.Found,
        installedVersion: String,
        notifiedNormalized: String?,
        canNotify: Boolean,
    ): UpdateDecision {
        val latest = SemanticVersion.parse(query.tag)
        val installed = SemanticVersion.parse(installedVersion)
        if (latest == null || installed == null) {
            return UpdateDecision.Unavailable("버전을 비교하지 못했습니다")
        }
        if (latest <= installed) return UpdateDecision.Current("최신 버전입니다")
        val display = SemanticVersion.display(query.tag)
        val normalized = latest.normalized()
        if (notifiedNormalized == normalized) {
            return UpdateDecision.AlreadySent(
                tag = query.tag,
                htmlUrl = query.htmlUrl,
                message = "새 버전 ${display} 알림을 이미 보냈습니다",
            )
        }
        if (!canNotify) {
            return UpdateDecision.NeedsPermission(
                tag = query.tag,
                htmlUrl = query.htmlUrl,
                message = "새 버전 ${display}가 있습니다. 알림 허용 후 다시 확인하세요",
            )
        }
        return UpdateDecision.Notify(
            tag = query.tag,
            normalized = normalized,
            htmlUrl = query.htmlUrl,
            message = "새 버전 ${display} 알림을 보냈습니다",
        )
    }
}
