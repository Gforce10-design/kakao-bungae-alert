package app.bungaealert.update

import android.util.Log
import app.bungaealert.BuildConfig
import app.bungaealert.net.Http
import app.bungaealert.net.HttpResult
import org.json.JSONObject

data class GithubRelease(
    val tag: String,
    val htmlUrl: String,
    val versionCode: Int?,
    val apkName: String? = null,
    val apkUrl: String? = null,
    val checksumUrl: String? = null,
)

sealed interface ReleaseQuery {
    data class Found(
        val tag: String,
        val htmlUrl: String,
        val versionCode: Int,
        val apkUrl: String? = null,
        val apkName: String? = null,
        val checksumUrl: String? = null,
    ) : ReleaseQuery
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
        val links = ReleaseAssets.links(json)
        val versionCode = VersionCodes.parse(notes, links.map { it.name })
        val apk = ReleaseAssets.apk(links, versionCode)
        val sums = ReleaseAssets.checksums(links)
        return GithubRelease(
            tag = tag,
            htmlUrl = url,
            versionCode = versionCode,
            apkName = apk?.name,
            apkUrl = apk?.url,
            checksumUrl = sums?.url,
        )
    }
}

internal data class ReleaseLink(val name: String, val url: String)

internal object ReleaseAssets {
    fun links(json: JSONObject): List<ReleaseLink> {
        val assets = json.optJSONArray("assets") ?: return emptyList()
        val links = mutableListOf<ReleaseLink>()
        for (index in 0 until assets.length()) {
            val item = assets.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("browser_download_url").trim()
            if (name.isNotEmpty()) links += ReleaseLink(name, url)
        }
        return links
    }

    fun apk(links: List<ReleaseLink>, versionCode: Int?): ReleaseLink? {
        val apks = links.filter { it.name.endsWith(".apk", ignoreCase = true) && HttpsOnly.accept(it.url) }
        if (versionCode != null) {
            apks.firstOrNull { link ->
                link.name.startsWith("bungae-alert-") && VersionCodes.parse("", listOf(link.name)) == versionCode
            }?.let { return it }
        }
        return apks.firstOrNull { it.name.startsWith("bungae-alert-") } ?: apks.firstOrNull()
    }

    fun checksums(links: List<ReleaseLink>): ReleaseLink? {
        return links.firstOrNull { it.name == "SHA256SUMS" && HttpsOnly.accept(it.url) }
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
                    else -> ReleaseQuery.Found(
                        tag = parsed.tag,
                        htmlUrl = parsed.htmlUrl,
                        versionCode = parsed.versionCode,
                        apkUrl = parsed.apkUrl,
                        apkName = parsed.apkName,
                        checksumUrl = parsed.checksumUrl,
                    )
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
