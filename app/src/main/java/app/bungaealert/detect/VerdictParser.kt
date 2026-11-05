package app.bungaealert.detect

import org.json.JSONObject

object VerdictParser {
    fun parse(raw: String?, backendId: String, requestId: String? = null): JudgeVerdict? {
        if (raw.isNullOrBlank()) return null
        val jsonText = extractObject(raw) ?: return null
        val json = try {
            JSONObject(jsonText)
        } catch (_: Exception) {
            return null
        }
        if (!json.has("isMatch") && !json.has("match") && !json.has("is_match")) return null
        val match = when {
            json.has("isMatch") -> json.optBoolean("isMatch", false)
            json.has("is_match") -> json.optBoolean("is_match", false)
            else -> json.optBoolean("match", false)
        }
        val type = PostType.fromWire(json.optString("type", "none")).takeIf { match || json.optString("type").isNotBlank() }
            ?.takeIf { it == PostType.MEETUP || it == PostType.GIVEAWAY }
        val confidence = json.optDouble("confidence", if (match) 0.7 else 0.3).coerceIn(0.0, 1.0)
        val reason = json.optString("reason").ifBlank { json.optString("why") }
        val id = json.optString("requestId").ifBlank { json.optString("request_id") }.ifBlank { requestId }
        return JudgeVerdict(
            isMatch = match,
            type = if (match) type else null,
            confidence = confidence,
            reason = reason,
            backendId = backendId,
            requestId = id?.takeIf { it.isNotBlank() },
        )
    }

    fun extractObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return raw.substring(start, end + 1)
    }
}

object JudgePrompt {
    fun systemText(): String = """
        카카오톡 단톡방 글 하나를 의미로 판정한다. 키워드가 있어도 지난 이야기, 짧은 대답, 단순 질문은 해당하지 않는다.
        meetup: 지금 사람을 모으는 번개모임. 인원, 날짜, 시간, 장소 중 일부가 있고 참석을 권한다.
        giveaway: 선착순으로 기프티콘이나 상품을 주는 글. 'n분께 드려요' 같은 증정이다.
        JSON만 답한다. 다른 말은 붙이지 않는다.
        {"isMatch":true,"type":"meetup","confidence":0.9,"reason":"한 줄"}
        type은 meetup, giveaway, none 중 하나다. 해당하지 않으면 isMatch는 false, type은 none이다.
    """.trimIndent()

    fun userText(request: JudgeRequest): String = buildString {
        append("채팅방: ").append(request.chatName).append('\n')
        if (!request.sender.isNullOrBlank()) append("보낸 사람: ").append(request.sender).append('\n')
        append("기기 점수: ").append(request.score).append('\n')
        append("보고 있는 종류: ")
        append(request.enabledTypes.joinToString(",") { it.wire })
        append("\n본문:\n")
        append(request.text)
    }
}

object GrokPayload {
    fun build(
        requestId: String,
        chatName: String,
        sender: String?,
        text: String,
        postTypes: Set<PostType>,
        score: Int,
        replyTopic: String,
        replyServer: String,
    ): JSONObject = JSONObject().apply {
        put("requestId", requestId)
        put("chatName", chatName)
        put("sender", sender ?: "")
        put("text", text)
        put("postTypes", org.json.JSONArray(postTypes.map { it.wire }))
        put("score", score)
        put("replyTopic", replyTopic)
        put("replyServer", replyServer.trimEnd('/'))
    }
}

object GrokRoutinePrompt {
    fun korean(replyServer: String, replyTopic: String): String {
        val server = replyServer.trimEnd('/')
        val topic = replyTopic.trim()
        return """
            당신은 카카오톡 단톡방 메시지를 판정하는 심사자입니다.
            입력으로 JSON이 옵니다. text를 의미로 보고 두 종류 중 무엇인지 판단하세요.
            1) meetup: 번개모임 모집. 사람을 모으고, 인원·날짜·시간·장소 중 일부가 있습니다. '저요' 같은 짧은 대답, 지난 모임 잡담, 질문만 있는 글은 해당하지 않습니다.
            2) giveaway: 선착순 기프티콘·상품 나눔. 'n분께 드려요', '선착순'처럼 먼저 다는 사람에게 무언가를 주는 글입니다.
            postTypes에 없는 종류는 고르지 마세요.

            반드시 아래 JSON 한 줄만 만드세요. requestId는 입력의 requestId를 그대로 넣습니다.
            {"requestId":"<입력 requestId>","isMatch":true,"type":"meetup","confidence":0.9,"reason":"한 줄 이유"}
            해당하지 않으면 isMatch는 false, type은 none입니다.

            그 JSON을 ntfy로 발행하세요.
            서버: $server
            토픽: $topic
            POST $server/$topic
            Content-Type: text/plain; charset=utf-8
            본문은 JSON 한 줄입니다. 제목이나 다른 설명은 붙이지 마세요.
            선착순 글은 시간이 중요합니다. 가능하면 바로 발행하세요.
            이 경로는 수십 초가 걸릴 수 있고 Grok Bot 사용량을 씁니다.
        """.trimIndent()
    }
}

object NtfyEvents {
    /**
     * [since] is an ntfy `since` value: a message id or a unix timestamp in seconds. When set, the
     * server replays messages published after it so replies sent during a disconnect are not lost.
     */
    fun streamUrl(server: String, topic: String, since: String? = null): String? {
        if (!TopicGenerator.isSafeTopic(topic)) return null
        val base = server.trim().trimEnd('/')
        if (!base.startsWith("https://") && !base.startsWith("http://")) return null
        val cursor = since?.trim().orEmpty()
        val safe = cursor.isNotEmpty() && cursor.all { it.isLetterOrDigit() || it == '_' || it == '-' }
        return if (safe) "$base/$topic/json?since=$cursor" else "$base/$topic/json"
    }

    /** Resume point carried by one ntfy event line: the message id, else its unix time in seconds. */
    fun cursorFromLine(line: String): String? {
        val event = try {
            JSONObject(line.trim())
        } catch (_: Exception) {
            return null
        }
        if (event.optString("event") != "message") return null
        val id = event.optString("id")
        if (id.isNotEmpty() && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }) return id
        val time = event.optLong("time", 0L)
        return if (time > 0L) time.toString() else null
    }

    fun verdictFromLine(line: String, backendId: String): JudgeVerdict? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed == ":") return null
        val event = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return VerdictParser.parse(trimmed, backendId)
        }
        val kind = event.optString("event")
        if (kind.isNotEmpty() && kind != "message") return null
        val message = event.optString("message").ifBlank { event.optString("body") }
        if (message.isBlank()) return VerdictParser.parse(trimmed, backendId)
        return VerdictParser.parse(message, backendId)
    }
}
