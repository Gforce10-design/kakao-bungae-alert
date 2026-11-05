package app.bungaealert.ai

import app.bungaealert.detect.JudgeVerdict
import app.bungaealert.detect.NtfyEvents
import app.bungaealert.detect.PostType
import app.bungaealert.net.HttpResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyHubTest {
    private fun verdict(id: String) = JudgeVerdict(
        isMatch = true,
        type = PostType.MEETUP,
        confidence = 0.9,
        reason = "t",
        backendId = "grok_webhook",
        requestId = id,
    )

    @Test
    fun unclaimedReplyExpiresAfterTtl() {
        var now = 0L
        val hub = ReplyHub(clock = { now }, earlyTtlMs = 120_000L, earlyMax = 10)
        hub.deliver(verdict("a"))
        assertEquals(1, hub.earlySize())
        now = 119_999L
        assertEquals(1, hub.earlySize())
        now = 120_000L
        assertEquals(0, hub.earlySize())
        assertFalse(hub.register("a").isCompleted)
    }

    @Test
    fun earlyReplyStillClaimedWithinTtl() {
        var now = 0L
        val hub = ReplyHub(clock = { now }, earlyTtlMs = 120_000L, earlyMax = 10)
        hub.deliver(verdict("a"))
        now = 60_000L
        assertTrue(hub.register("a").isCompleted)
        assertEquals(0, hub.earlySize())
    }

    @Test
    fun sizeIsCappedDroppingOldest() {
        val hub = ReplyHub(clock = { 0L }, earlyTtlMs = 120_000L, earlyMax = 3)
        listOf("a", "b", "c", "d", "e").forEach { hub.deliver(verdict(it)) }
        assertEquals(3, hub.earlySize())
        assertFalse(hub.register("a").isCompleted)
        assertFalse(hub.register("b").isCompleted)
        assertTrue(hub.register("c").isCompleted)
        assertTrue(hub.register("e").isCompleted)
    }

    @Test
    fun ntfySinceIsAddedAndSanitised() {
        assertEquals("https://ntfy.sh/t-1/json", NtfyEvents.streamUrl("https://ntfy.sh", "t-1"))
        assertEquals("https://ntfy.sh/t-1/json?since=abc123", NtfyEvents.streamUrl("https://ntfy.sh", "t-1", "abc123"))
        assertEquals("https://ntfy.sh/t-1/json?since=1700000000", NtfyEvents.streamUrl("https://ntfy.sh", "t-1", "1700000000"))
        assertEquals("https://ntfy.sh/t-1/json", NtfyEvents.streamUrl("https://ntfy.sh", "t-1", "a&b=c"))
    }

    @Test
    fun cursorPrefersIdThenTime() {
        assertEquals("xyz", NtfyEvents.cursorFromLine("""{"id":"xyz","time":1700000000,"event":"message","message":"m"}"""))
        assertEquals("1700000000", NtfyEvents.cursorFromLine("""{"time":1700000000,"event":"message","message":"m"}"""))
        assertNull(NtfyEvents.cursorFromLine("""{"id":"k","event":"keepalive"}"""))
        assertNull(NtfyEvents.cursorFromLine("not json"))
    }

    @Test
    fun modelValidationMapsStatuses() {
        fun run(provider: ModelProvider, key: String, model: String, code: Int, urlOut: MutableList<String> = mutableListOf()) =
            ModelValidator.validate(provider, key, model) { url, _ ->
                urlOut += url
                HttpResult(code, "", if (code == -1) "boom" else null)
            }
        assertNull(run(ModelProvider.OpenAI, "k", "gpt-4.1-mini", 200))
        assertNotNull(run(ModelProvider.OpenAI, "k", "nope", 404))
        assertTrue(run(ModelProvider.Claude, "k", "x", 401)!!.contains("거부"))
        assertNotNull(run(ModelProvider.Gemini, "k", "m", -1))
        assertNotNull(run(ModelProvider.Gemini, "", "m", 200))
        assertNotNull(run(ModelProvider.Gemini, "k", "bad model/..", 200))
        val urls = mutableListOf<String>()
        assertNull(run(ModelProvider.Gemini, "k", "models/gemini-2.5-flash", 200, urls))
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash", urls.single())
    }
}
