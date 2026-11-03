package app.bungaealert.update

import app.bungaealert.net.HttpResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseQueryTest {
    private val body = """
        {
          "tag_name": "v1.4.0",
          "html_url": "https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v1.4.0",
          "name": "1.4.0",
          "assets": []
        }
    """.trimIndent()

    @Test
    fun parsesLatestReleaseJson() {
        val release = ReleaseJson.parse(body)
        assertEquals("v1.4.0", release!!.tag)
        assertEquals(
            "https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v1.4.0",
            release.htmlUrl,
        )
    }

    @Test
    fun rejectsMissingOrUnsafeUrls() {
        assertNull(ReleaseJson.parse("""{"tag_name":"v1.0.0"}"""))
        assertNull(ReleaseJson.parse("""{"tag_name":"v1.0.0","html_url":"http://example.com"}"""))
        assertNull(ReleaseJson.parse("not json"))
    }

    @Test
    fun sourceMapsHttpResults() {
        val url = "https://github.com/Gforce10-design/kakao-bungae-alert/releases/tag/v1.4.0"
        assertEquals(
            ReleaseQuery.Found("v1.4.0", url),
            GithubReleaseSource { _, _ -> HttpResult(200, body, null) }.latest(),
        )
        assertEquals(ReleaseQuery.NotFound, GithubReleaseSource { _, _ -> HttpResult(404, "", null) }.latest())
        assertEquals(ReleaseQuery.RateLimited, GithubReleaseSource { _, _ -> HttpResult(403, "", null) }.latest())
        assertEquals(ReleaseQuery.RateLimited, GithubReleaseSource { _, _ -> HttpResult(429, "", null) }.latest())
        val failed = GithubReleaseSource { _, _ -> HttpResult(-1, "", "timeout") }.latest()
        assertTrue(failed is ReleaseQuery.Failed)
        val headers = mutableListOf<Map<String, String>>()
        GithubReleaseSource { called, header ->
            assertEquals(GithubReleaseSource.LATEST_URL, called)
            headers += header
            HttpResult(404, "", null)
        }.latest()
        assertEquals("kakao-bungae-alert", headers.single()["User-Agent"])
        assertEquals("application/vnd.github+json", headers.single()["Accept"])
    }
}
