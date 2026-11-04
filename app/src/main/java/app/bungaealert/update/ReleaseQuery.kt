package app.bungaealert.update

import android.util.Log
import app.bungaealert.BuildConfig
import app.bungaealert.net.Http
import app.bungaealert.net.HttpResult
import org.json.JSONObject

data class GithubRelease(val tag: String, val htmlUrl: String, val versionCode: Int?)

sealed interface ReleaseQuery {
    data class Found(val tag: String, val htmlUrl: String, val versionCode: Int) : ReleaseQuery
    data object NotFound : ReleaseQuery
    data object Unauthorized : ReleaseQuery
    data object RateLimited : ReleaseQuery
    data class Failed(val detail: String) : ReleaseQuery
}

object UpdateEndpoint {
    const val DEFAULT_REPO = "Gforce10-design/kakao-bungae-alert"

    fun latestUrl(
        repo: String = BuildConfig.UPDATE_REPO,
        explicitUrl: String = BuildConfig.UPDATE_RELEASES_URL,
    ): String {
        val override = explicitUrl.trim()
        if (override.startsWith("https://")) return override
        val ownerRepo = repo.trim().trim('/').ifBlank { DEFAULT_REPO }
        return "https://api.github.com/repos/$ownerRepo/releases/latest"
    }
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
        val notes = json.optString("body")
        val names = assetNames(json)
        return GithubRelease(tag, url, VersionCodes.parse(notes, names))
    }

    private fun assetNames(json: JSONObject): List<String> {
        val assets = json.optJSONArray("assets") ?: return emptyList()
        val names = mutableListOf<String>()
        for (index in 0 until assets.length()) {
            val name = assets.optJSONObject(index)?.optString("name").orEmpty().trim()
            if (name.isNotEmpty()) names += name
        }
        return names
    }
}

fun interface ReleaseSource {
    fun latest(): ReleaseQuery
}

class GithubReleaseSource(
    private val latestUrl: String = UpdateEndpoint.latestUrl(),
    private val log: (String) -> Unit = { message -> Log.i(TAG, message) },
    private val get: (String, Map<String, String>) -> HttpResult = { url, headers -> Http.get(url, headers) },
) : ReleaseSource {
    override fun latest(): ReleaseQuery {
        val result = try {
            get(latestUrl, HEADERS)
        } catch (t: Throwable) {
            val detail = t.message ?: t.javaClass.simpleName
            log("GitHub releases request failed: $detail")
            return ReleaseQuery.Failed(detail)
        }
        return when {
            result.code == 401 -> {
                log("GitHub releases returned 401; skipping update check")
                ReleaseQuery.Unauthorized
            }
            result.code == 404 -> {
                log("GitHub releases returned 404; skipping update check")
                ReleaseQuery.NotFound
            }
            result.code == 403 || result.code == 429 -> ReleaseQuery.RateLimited
            result.code < 0 -> ReleaseQuery.Failed(result.error ?: "network")
            !result.ok -> ReleaseQuery.Failed(result.error ?: "HTTP ${result.code}")
            else -> {
                val parsed = ReleaseJson.parse(result.body)
                when {
                    parsed == null -> ReleaseQuery.Failed("bad json")
                    parsed.versionCode == null -> ReleaseQuery.Failed("versionCode missing")
                    else -> ReleaseQuery.Found(parsed.tag, parsed.htmlUrl, parsed.versionCode)
                }
            }
        }
    }

    companion object {
        const val TAG = "BungaeUpdate"
        private val HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
            "User-Agent" to "kakao-bungae-alert",
            "X-GitHub-Api-Version" to "2022-11-28",
        )
    }
}
