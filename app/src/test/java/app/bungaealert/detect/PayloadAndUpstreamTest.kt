package app.bungaealert.detect

import app.bungaealert.auth.upstream.CopilotDeviceFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadAndUpstreamTest {
    @Test
    fun verdictParsesFencedJson() {
        val verdict = VerdictParser.parse(
            """
            설명은 버리고
            ```json
            {"isMatch": true, "type": "giveaway", "confidence": 0.91, "reason": "선착순 기프티콘"}
            ```
            """.trimIndent(),
            "gemini_api",
        )
        assertEquals(true, verdict?.isMatch)
        assertEquals(PostType.GIVEAWAY, verdict?.type)
        assertEquals("선착순 기프티콘", verdict?.reason)
    }

    @Test
    fun grokPayloadHasTheAgreedFieldsAndNoFixedTopic() {
        val json = GrokPayload.build(
            requestId = "req-1",
            chatName = "10월의 수다방",
            sender = "김민수",
            text = "오늘 번개",
            postTypes = setOf(PostType.MEETUP),
            score = 61,
            replyTopic = "bungae-abc123",
            replyServer = "https://ntfy.example",
        )
        assertEquals("req-1", json.getString("requestId"))
        assertEquals("10월의 수다방", json.getString("chatName"))
        assertEquals("김민수", json.getString("sender"))
        assertEquals("오늘 번개", json.getString("text"))
        assertEquals(61, json.getInt("score"))
        assertEquals("bungae-abc123", json.getString("replyTopic"))
        assertEquals("https://ntfy.example", json.getString("replyServer"))
        assertEquals("meetup", json.getJSONArray("postTypes").getString(0))
    }

    @Test
    fun routinePromptUsesTheUsersTopic() {
        val prompt = GrokRoutinePrompt.korean("https://ntfy.example/", "bungae-user")
        assertTrue(prompt.contains("https://ntfy.example/bungae-user"))
        assertTrue(prompt.contains("requestId"))
        assertTrue(prompt.contains("meetup"))
        assertTrue(prompt.contains("giveaway"))
        assertFalse(prompt.contains("hooks.example"))
    }

    @Test
    fun topicGeneratorIsNotConstant() {
        val a = TopicGenerator.random(java.util.Random(1))
        val b = TopicGenerator.random(java.util.Random(2))
        assertNotEquals(a, b)
        assertTrue(a.startsWith("bungae-"))
        assertTrue(TopicGenerator.isSafeTopic(a))
        assertFalse(TopicGenerator.isSafeTopic("a/../../etc"))
        assertFalse(TopicGenerator.isSafeTopic(""))
    }

    @Test
    fun ntfyLineMatchesRequest() {
        val line = JSONObject()
            .put("event", "message")
            .put("topic", "bungae-user")
            .put(
                "message",
                """{"requestId":"req-9","isMatch":true,"type":"meetup","confidence":0.7,"reason":"모집"}""",
            )
            .toString()
        val verdict = NtfyEvents.verdictFromLine(line, "grok_webhook")
        assertEquals("req-9", verdict?.requestId)
        assertEquals(PostType.MEETUP, verdict?.type)
        assertNull(NtfyEvents.verdictFromLine("""{"event":"keepalive"}""", "grok_webhook"))
        assertEquals(
            "https://ntfy.sh/bungae-user/json",
            NtfyEvents.streamUrl("https://ntfy.sh/", "bungae-user"),
        )
        assertNull(NtfyEvents.streamUrl("https://ntfy.sh", "bad topic"))
    }

    @Test
    fun sensitivityMovesTheCutoff() {
        assertEquals(80, Sensitivity.cutoff(0))
        assertEquals(35, Sensitivity.cutoff(100))
        assertTrue(Sensitivity.cutoff(40) in 55..70)
    }

    @Test
    fun copilotDeviceFlowClassifiesPolls() {
        val auth = CopilotDeviceFlow.parseDeviceAuthorization(
            JSONObject()
                .put("device_code", "dev")
                .put("user_code", "ABCD-1234")
                .put("verification_uri", "https://github.com/login/device")
                .put("expires_in", 600)
                .put("interval", 5),
        )
        assertEquals("ABCD-1234", auth?.userCode)
        assertEquals(5L, auth?.intervalSeconds)

        val pending = CopilotDeviceFlow.classifyPoll(
            JSONObject().put("error", "authorization_pending"),
            httpOK = true,
        )
        assertTrue(pending is CopilotDeviceFlow.PollResult.Pending)

        val ok = CopilotDeviceFlow.classifyPoll(
            JSONObject().put("access_token", "gho_example"),
            httpOK = true,
        )
        assertTrue(ok is CopilotDeviceFlow.PollResult.Success)

        val session = CopilotDeviceFlow.parseSessionToken(
            JSONObject().put("token", "sid").put("expires_at", 2_000L),
            nowEpochSeconds = 1_000L,
        )
        assertEquals("sid", session?.token)
        assertTrue(session!!.needsRefresh(1_950L))
        assertFalse(session.needsRefresh(1_000L))
        assertEquals(10L, CopilotDeviceFlow.bumpedInterval(5))

        val headers = CopilotDeviceFlow.tokenExchangeHeaders("gho_example")
        assertTrue(headers.getValue("Authorization").startsWith("token "))
        assertFalse(headers.getValue("Authorization").startsWith("Bearer"))
    }
}
