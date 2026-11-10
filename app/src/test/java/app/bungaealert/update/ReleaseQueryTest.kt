package app.bungaealert.update

import app.bungaealert.net.HttpResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseQueryTest {
    private val releaseUrl = "https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v1.4.0"
    private val body = """
        {
          "tag_name": "v1.4.0",
          "html_url": "$releaseUrl",
          "name": "1.4.0",
          "body": "번개알림 v1.4.0\n\nversionCode: 42\nversionName: 1.4.0\n",
          "assets": [
            {
              "name": "bungae-alert-v1.4.0-vc42.apk",
              "browser_download_url": "https://github.com/Gforce10-design/kakao-bungae-alert/releases/download/v1.4.0/bungae-alert-v1.4.0-vc42.apk"
            },
            {"name": "SHA256SUMS", "browser_download_url": "https://example.invalid/sums"}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesLatestReleaseJson() {
        val release = ReleaseJson.parse(body)
        assertEquals("v1.4.0", release!!.tag)
        assertEquals(releaseUrl, release.htmlUrl)
        assertEquals(42, release.versionCode)
        assertEquals("bungae-alert-v1.4.0-vc42.apk", release.apkName)
        assertEquals(
            "https://github.com/Gforce10-design/kakao-bungae-alert/releases/download/v1.4.0/bungae-alert-v1.4.0-vc42.apk",
            release.apkUrl,
        )
        assertEquals("https://example.invalid/sums", release.checksumUrl)
    }

    @Test
    fun picksTheApkWhoseVersionMatchesAndDropsUnsafeUrls() {
        val json = """
            {
              "tag_name": "v1.4.0",
              "html_url": "$releaseUrl",
              "body": "versionCode: 42",
              "assets": [
                {"name": "bungae-alert-v1.0.0-vc7.apk", "browser_download_url": "https://example.com/old.apk"},
                {"name": "notes.txt", "browser_download_url": "https://example.com/notes.txt"},
                {"name": "bungae-alert-v1.4.0-vc42.apk", "browser_download_url": "https://example.com/new.apk"},
                {"name": "SHA256SUMS", "browser_download_url": "http://example.com/SHA256SUMS"},
                {"name": "other.apk", "browser_download_url": "https://user:pass@example.com/other.apk"}
              ]
            }
        """.trimIndent()
        val release = ReleaseJson.parse(json)!!
        assertEquals("https://example.com/new.apk", release.apkUrl)
        assertEquals("bungae-alert-v1.4.0-vc42.apk", release.apkName)
        assertNull(release.checksumUrl)
    }

    @Test
    fun parsesVersionCodeFromTheAssetNameWhenTheBodyOmitsIt() {
        val json = """
            {
              "tag_name": "v1.4.0",
              "html_url": "$releaseUrl",
              "body": "notes without a code",
              "assets": [{"name": "bungae-alert-v1.4.0-vc18.apk"}]
            }
        """.trimIndent()
        assertEquals(18, ReleaseJson.parse(json)!!.versionCode)
    }

    @Test
    fun rejectsMissingOrUnsafeUrls() {
        assertNull(ReleaseJson.parse("""{"tag_name":"v1.0.0","body":"versionCode: 2"}"""))
        assertNull(ReleaseJson.parse("""{"tag_name":"v1.0.0","html_url":"http://example.com","body":"versionCode: 2"}"""))
        assertNull(ReleaseJson.parse("not json"))
    }

    @Test
    fun sourceMapsHttpResults() {
        assertEquals(
            ReleaseQuery.Found(
                tag = "v1.4.0",
                htmlUrl = releaseUrl,
                versionCode = 42,
                apkUrl = "https://github.com/Gforce10-design/kakao-bungae-alert/releases/download/v1.4.0/bungae-alert-v1.4.0-vc42.apk",
                apkName = "bungae-alert-v1.4.0-vc42.apk",
                checksumUrl = "https://example.invalid/sums",
            ),
            GithubReleaseSource { _, _ -> HttpResult(200, body, null) }.latest(),
        )
        assertEquals(ReleaseQuery.NotFound, GithubReleaseSource { _, _ -> HttpResult(404, "", null) }.latest())
        assertEquals(ReleaseQuery.RateLimited, GithubReleaseSource { _, _ -> HttpResult(403, "", null) }.latest())
        assertEquals(ReleaseQuery.RateLimited, GithubReleaseSource { _, _ -> HttpResult(429, "", null) }.latest())
        val failed = GithubReleaseSource { _, _ -> HttpResult(-1, "", "timeout") }.latest()
        assertTrue(failed is ReleaseQuery.Failed)
        val missingCode = """
            {"tag_name":"v1.0.0","html_url":"$releaseUrl","body":"no code","assets":[]}
        """.trimIndent()
        val noCode = GithubReleaseSource { _, _ -> HttpResult(200, missingCode, null) }.latest()
        assertTrue(noCode is ReleaseQuery.Failed)
        val headers = mutableListOf<Map<String, String>>()
        GithubReleaseSource { called, header ->
            assertEquals(UpdateEndpoint.latestUrl(repo = UpdateEndpoint.DEFAULT_REPO, explicitUrl = ""), called)
            headers += header
            HttpResult(404, "", null)
        }.latest()
        assertEquals("kakao-bungae-alert", headers.single()["User-Agent"])
        assertEquals("application/vnd.github+json", headers.single()["Accept"])
    }

    @Test
    fun privateRepoStatusesDoNotThrowAndAreLogged() {
        val logs = mutableListOf<String>()
        val unauthorized = GithubReleaseSource(log = { logs += it }) { _, _ ->
            HttpResult(401, """{"message":"Requires authentication"}""", null)
        }.latest()
        val missing = GithubReleaseSource(log = { logs += it }) { _, _ ->
            HttpResult(404, """{"message":"Not Found"}""", null)
        }.latest()
        assertEquals(ReleaseQuery.Unauthorized, unauthorized)
        assertEquals(ReleaseQuery.NotFound, missing)
        assertTrue(logs.any { it.contains("401") })
        assertTrue(logs.any { it.contains("404") })
    }

    @Test
    fun thrownRequestsBecomeFailures() {
        val failed = GithubReleaseSource { _, _ ->
            throw IllegalStateException("boom")
        }.latest()
        assertTrue(failed is ReleaseQuery.Failed)
        assertEquals("boom", (failed as ReleaseQuery.Failed).detail)
    }

    @Test
    fun endpointIsConfigurable() {
        val seen = mutableListOf<String>()
        GithubReleaseSource(latestUrl = "https://example.invalid/releases/latest") { url, _ ->
            seen += url
            HttpResult(404, "", null)
        }.latest()
        assertEquals(listOf("https://example.invalid/releases/latest"), seen)
        assertEquals(
            "https://api.github.com/repos/acme/app/releases/latest",
            UpdateEndpoint.latestUrl(repo = "acme/app", explicitUrl = ""),
        )
        assertEquals(
            "https://example.invalid/latest",
            UpdateEndpoint.latestUrl(repo = "acme/app", explicitUrl = "https://example.invalid/latest"),
        )
        assertEquals(
            "https://api.github.com/repos/${UpdateEndpoint.DEFAULT_REPO}/releases/latest",
            UpdateEndpoint.latestUrl(repo = "  ", explicitUrl = "http://insecure.example"),
        )
    }
}
