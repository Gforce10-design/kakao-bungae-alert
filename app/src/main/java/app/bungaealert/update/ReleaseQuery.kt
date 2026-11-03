package app.bungaealert.update

import app.bungaealert.net.Http
import app.bungaealert.net.HttpResult
import org.json.JSONObject

data class GithubRelease(val tag: String, val htmlUrl: String)

sealed interface ReleaseQuery {
    data class Found(val tag: String, val htmlUrl: String) : ReleaseQuery
    data object NotFound : ReleaseQuery
    data object RateLimited : ReleaseQuery
    data class Failed(val detail: String) : ReleaseQuery
}

object ReleaseJson {
    fun parse(body: String): GithubRelease? {
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return null
        }
        val tag = json.optString("tag_name").trim()
        val url = json.optString("html_url").trim()
        if (tag.isEmpty() || url.isEmpty() || !url.startsWith("https://")) return null
        return GithubRelease(tag, url)
    }
}

fun interface ReleaseSource {
    fun latest(): ReleaseQuery
}

class GithubReleaseSource(
    private val get: (String, Map<String, String>) -> HttpResult = { url, headers -> Http.get(url, headers) },
) : ReleaseSource {
    override fun latest(): ReleaseQuery {
        val result = try {
            get(LATEST_URL, HEADERS)
        } catch (t: Throwable) {
            return ReleaseQuery.Failed(t.message ?: t.javaClass.simpleName)
        }
        return when {
            result.code == 404 -> ReleaseQuery.NotFound
            result.code == 403 || result.code == 429 -> ReleaseQuery.RateLimited
            result.code < 0 -> ReleaseQuery.Failed(result.error ?: "network")
            !result.ok -> ReleaseQuery.Failed(result.error ?: "HTTP ${result.code}")
            else -> {
                val parsed = ReleaseJson.parse(result.body)
                if (parsed == null) ReleaseQuery.Failed("bad json") else ReleaseQuery.Found(parsed.tag, parsed.htmlUrl)
            }
        }
    }

    companion object {
        const val LATEST_URL = "https://api.github.com/repos/Gforce10-design/kakao-bungae-alert/releases/latest"
        private val HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to "kakao-bungae-alert",
            "X-GitHub-Api-Version" to "2022-11-28",
        )
    }
}
