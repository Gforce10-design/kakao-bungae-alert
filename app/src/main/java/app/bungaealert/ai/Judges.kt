package app.bungaealert.ai

import android.content.Context
import app.bungaealert.BuildConfig
import app.bungaealert.auth.ClaudeSubscription
import app.bungaealert.auth.CodexSubscription
import app.bungaealert.auth.CopilotSubscription
import app.bungaealert.auth.upstream.ClaudeCliMimicryHeaders
import app.bungaealert.auth.upstream.CopilotDeviceFlow
import app.bungaealert.auth.upstream.applyClaudeCliMimicryHeaders
import app.bungaealert.data.AppSettings
import app.bungaealert.data.SecretStore
import app.bungaealert.detect.AiJudge
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.GrokPayload
import app.bungaealert.detect.JudgePrompt
import app.bungaealert.detect.JudgeRequest
import app.bungaealert.detect.JudgeVerdict
import app.bungaealert.detect.NtfyEvents
import app.bungaealert.detect.VerdictParser
import app.bungaealert.net.Http
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ReplyHub(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val earlyTtlMs: Long = EARLY_TTL_MS,
    private val earlyMax: Int = EARLY_MAX,
) {
    private class Early(val verdict: JudgeVerdict, val at: Long)

    private val waiters = ConcurrentHashMap<String, CompletableDeferred<JudgeVerdict>>()
    private val early = LinkedHashMap<String, Early>()

    fun register(requestId: String): CompletableDeferred<JudgeVerdict> {
        val deferred = CompletableDeferred<JudgeVerdict>()
        val raced = synchronized(early) {
            evictLocked()
            early.remove(requestId)
        }
        if (raced != null) deferred.complete(raced.verdict)
        waiters[requestId] = deferred
        return deferred
    }

    fun deliver(verdict: JudgeVerdict) {
        val id = verdict.requestId ?: return
        val waiter = waiters.remove(id)
        if (waiter != null) {
            waiter.complete(verdict)
            return
        }
        synchronized(early) {
            early.remove(id)
            early[id] = Early(verdict, clock())
            evictLocked()
        }
    }

    fun cancel(requestId: String) {
        waiters.remove(requestId)?.cancel()
    }

    /** Number of unclaimed replies currently held (after dropping expired ones). */
    fun earlySize(): Int = synchronized(early) {
        evictLocked()
        early.size
    }

    // Callers hold the lock. Entries are in insertion (= arrival) order, so the oldest come first.
    private fun evictLocked() {
        val now = clock()
        val it = early.entries.iterator()
        while (it.hasNext()) {
            if (now - it.next().value.at >= earlyTtlMs) it.remove() else break
        }
        while (early.size > earlyMax) {
            val oldest = early.entries.iterator()
            oldest.next()
            oldest.remove()
        }
    }

    companion object {
        const val EARLY_TTL_MS = 2 * 60_000L
        const val EARLY_MAX = 64
    }
}

class GeminiNanoJudge(
    private val context: Context,
    private val timeoutMs: Long,
    private val enabled: Boolean,
) : AiJudge {
    override val id: String = BackendIds.GEMINI_NANO
    override fun timeoutMillis(): Long = timeoutMs

    override suspend fun isReady(): Boolean {
        if (!enabled) return false
        val now = System.currentTimeMillis()
        cached?.let { if (now - cachedAt < 10 * 60_000) return it }
        val available = withContext(Dispatchers.IO) {
            try {
                val model = Generation.getClient()
                try {
                    model.checkStatus() == FeatureStatus.AVAILABLE
                } finally {
                    model.close()
                }
            } catch (_: Throwable) {
                false
            }
        }
        cached = available
        cachedAt = now
        return available
    }

    override suspend fun judge(request: JudgeRequest): JudgeVerdict? = withContext(Dispatchers.IO) {
        try {
            val model = Generation.getClient()
            try {
                if (model.checkStatus() != FeatureStatus.AVAILABLE) return@withContext null
                val prompt = JudgePrompt.systemText() + "\n\n" + JudgePrompt.userText(request)
                val response = model.generateContent(prompt)
                val text = response.candidates.firstOrNull()?.text
                VerdictParser.parse(text, id)
            } finally {
                model.close()
            }
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        @Volatile private var cached: Boolean? = null
        @Volatile private var cachedAt: Long = 0L
    }
}

class CloudKeyJudge(
    override val id: String,
    private val timeoutMs: Long,
    private val readyCheck: () -> Boolean,
    private val call: suspend (JudgeRequest) -> String?,
) : AiJudge {
    override fun timeoutMillis(): Long = timeoutMs
    override suspend fun isReady(): Boolean = readyCheck()
    override suspend fun judge(request: JudgeRequest): JudgeVerdict? {
        val text = try {
            call(request)
        } catch (_: Exception) {
            null
        } ?: return null
        return VerdictParser.parse(text, id)
    }
}

class GrokWebhookJudge(
    private val context: Context,
    private val settings: AppSettings,
    private val secrets: SecretStore,
    private val replies: ReplyHub,
) : AiJudge {
    override val id: String = BackendIds.GROK_WEBHOOK
    override fun timeoutMillis(): Long = settings.grokTimeoutSeconds * 1000L

    override suspend fun isReady(): Boolean {
        val url = secrets.get(SecretStore.GROK_URL)
        return url.startsWith("http://") || url.startsWith("https://")
    }

    override suspend fun judge(request: JudgeRequest): JudgeVerdict? = withContext(Dispatchers.IO) {
        val webhook = secrets.get(SecretStore.GROK_URL)
        if (!isReady()) return@withContext null
        val topic = settings.ntfyTopic
        if (NtfyEvents.streamUrl(settings.ntfyServer, topic) == null) return@withContext null
        val requestId = UUID.randomUUID().toString()
        val waiter = replies.register(requestId)
        NtfySubscribeService.start(context)
        val payload = GrokPayload.build(
            requestId = requestId,
            chatName = request.chatName,
            sender = request.sender,
            text = request.text,
            postTypes = request.enabledTypes,
            score = request.score,
            replyTopic = topic,
            replyServer = settings.ntfyServer.trimEnd('/'),
        )
        val headers = buildMap {
            val name = settings.grokHeaderName.trim()
            val value = secrets.get(SecretStore.GROK_HEADER)
            if (name.isNotEmpty() && value.isNotEmpty()) put(name, value)
        }
        val posted = Http.postJson(webhook, payload.toString(), headers, 10_000)
        if (!posted.ok) {
            replies.cancel(requestId)
            return@withContext null
        }
        withTimeoutOrNull(timeoutMillis()) { waiter.await() }
    }
}

object JudgeFactory {
    fun build(
        context: Context,
        settings: AppSettings,
        secrets: SecretStore,
        replies: ReplyHub,
    ): List<AiJudge> {
        val timeout = settings.shortJudgeTimeoutMs.toLong()
        val byId = linkedMapOf<String, AiJudge>(
            BackendIds.GEMINI_NANO to GeminiNanoJudge(
                context,
                timeout,
                BackendIds.GEMINI_NANO in settings.enabledBackends,
            ),
            BackendIds.GEMINI_API to CloudKeyJudge(BackendIds.GEMINI_API, timeout, {
                secrets.get(SecretStore.GEMINI_KEY).isNotBlank()
            }) { request ->
                geminiApi(secrets.get(SecretStore.GEMINI_KEY), settings.geminiModel, request, timeout)
            },
            BackendIds.OPENAI_API to CloudKeyJudge(BackendIds.OPENAI_API, timeout, {
                secrets.get(SecretStore.OPENAI_KEY).isNotBlank()
            }) { request ->
                openAiChat(
                    "https://api.openai.com/v1/chat/completions",
                    secrets.get(SecretStore.OPENAI_KEY),
                    settings.openaiModel,
                    request,
                    timeout,
                    extraHeaders = emptyMap(),
                )
            },
            BackendIds.CLAUDE_API to CloudKeyJudge(BackendIds.CLAUDE_API, timeout, {
                secrets.get(SecretStore.CLAUDE_KEY).isNotBlank()
            }) { request ->
                claudeApi(secrets.get(SecretStore.CLAUDE_KEY), settings.claudeModel, request, timeout, oauth = false)
            },
            BackendIds.CHATGPT_SUB to CloudKeyJudge(BackendIds.CHATGPT_SUB, timeout, {
                settings.subscriptionRiskAccepted && secrets.get(SecretStore.CODEX_TOKENS).isNotBlank()
            }) { request ->
                codex(secrets, settings.codexModel, request, timeout)
            },
            BackendIds.CLAUDE_SUB to CloudKeyJudge(BackendIds.CLAUDE_SUB, timeout, {
                settings.subscriptionRiskAccepted &&
                    secrets.get(SecretStore.CLAUDE_TOKENS).isNotBlank() &&
                    ClaudeSubscription.identifierOrNull() != null
            }) { request ->
                val token = ClaudeSubscription.accessToken(secrets) ?: return@CloudKeyJudge null
                claudeApi(token, settings.claudeModel, request, timeout, oauth = true)
            },
            BackendIds.COPILOT_SUB to CloudKeyJudge(BackendIds.COPILOT_SUB, timeout, {
                settings.subscriptionRiskAccepted && secrets.get(SecretStore.COPILOT_GITHUB).isNotBlank()
            }) { request ->
                copilot(secrets, settings.copilotModel, request, timeout)
            },
            BackendIds.GROK_WEBHOOK to GrokWebhookJudge(context, settings, secrets, replies),
        )
        return settings.backendOrder.mapNotNull { id ->
            if (id !in settings.enabledBackends) null else byId[id]
        }
    }
}

private suspend fun geminiApi(key: String, model: String, request: JudgeRequest, timeout: Long): String? {
    val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$key"
    val body = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put(
            "text",
            JudgePrompt.systemText() + "\n\n" + JudgePrompt.userText(request),
        )))))
        put("generationConfig", JSONObject().put("temperature", 0.1).put("maxOutputTokens", 256))
    }
    val result = Http.postJson(url, body.toString(), emptyMap(), timeout)
    if (!result.ok) return null
    val json = JSONObject(result.body)
    return json.optJSONArray("candidates")
        ?.optJSONObject(0)
        ?.optJSONObject("content")
        ?.optJSONArray("parts")
        ?.optJSONObject(0)
        ?.optString("text")
}

private suspend fun openAiChat(
    url: String,
    token: String,
    model: String,
    request: JudgeRequest,
    timeout: Long,
    extraHeaders: Map<String, String>,
): String? {
    val body = JSONObject().apply {
        put("model", model)
        put("temperature", 0.1)
        put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", JudgePrompt.systemText()))
            .put(JSONObject().put("role", "user").put("content", JudgePrompt.userText(request))))
    }
    val headers = buildMap {
        put("Authorization", "Bearer $token")
        putAll(extraHeaders)
    }
    val result = Http.postJson(url, body.toString(), headers, timeout)
    if (!result.ok) return null
    return JSONObject(result.body)
        .optJSONArray("choices")
        ?.optJSONObject(0)
        ?.optJSONObject("message")
        ?.optString("content")
}

private suspend fun claudeApi(
    credential: String,
    model: String,
    request: JudgeRequest,
    timeout: Long,
    oauth: Boolean,
): String? = withContext(Dispatchers.IO) {
    val user = JudgePrompt.userText(request)
    val system = if (oauth) {
        val prefix = BuildConfig.ANTHROPIC_OAUTH_IDENTIFIER_PROMPT
        JSONArray()
            .put(JSONObject().put("type", "text").put("text", prefix))
            .put(JSONObject().put("type", "text").put("text", JudgePrompt.systemText()))
    } else {
        JudgePrompt.systemText()
    }
    val body = JSONObject().apply {
        put("model", model)
        put("max_tokens", 256)
        put("system", system)
        put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", user)))
    }
    val bytes = body.toString().toByteArray()
    val media = "application/json".toMediaType()
    val requestBody = object : okhttp3.RequestBody() {
        override fun contentType() = media
        override fun contentLength() = bytes.size.toLong()
        override fun writeTo(sink: okio.BufferedSink) {
            sink.write(bytes)
        }
    }
    val builder = Request.Builder()
        .url("https://api.anthropic.com/v1/messages")
        .post(requestBody)
        .header("anthropic-version", "2023-06-01")
    if (oauth) {
        builder.applyClaudeCliMimicryHeaders()
        builder.header(
            "anthropic-beta",
            listOf(
                "claude-code-20250219",
                "oauth-2025-04-20",
                "interleaved-thinking-2025-05-14",
                "prompt-caching-scope-2026-01-05",
                "effort-2025-11-24",
            ).joinToString(","),
        )
        builder.header("Authorization", "Bearer $credential")
    } else {
        builder.header("x-api-key", credential)
    }
    val call = Http.client.newBuilder()
        .callTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
        .build()
    val response = try {
        call.newCall(builder.build()).execute()
    } catch (_: Exception) {
        return@withContext null
    }
    response.use {
        if (!it.isSuccessful) return@use null
        val json = JSONObject(it.body?.string().orEmpty())
        val content = json.optJSONArray("content") ?: return@use null
        buildString {
            for (i in 0 until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }.ifBlank { null }
    }
}

private suspend fun codex(secrets: SecretStore, model: String, request: JudgeRequest, timeout: Long): String? =
    withContext(Dispatchers.IO) {
        val token = CodexSubscription.accessToken(secrets) ?: return@withContext null
        val body = JSONObject().apply {
            put("model", model)
            put("store", false)
            put("stream", false)
            put("instructions", JudgePrompt.systemText())
            put(
                "input",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put(
                            "content",
                            JSONArray().put(
                                JSONObject().put("type", "input_text").put("text", JudgePrompt.userText(request)),
                            ),
                        ),
                ),
            )
            put("reasoning", JSONObject().put("effort", "low").put("summary", "auto"))
        }
        val bytes = body.toString().toByteArray()
        val media = "application/json".toMediaType()
        val requestBody = object : okhttp3.RequestBody() {
            override fun contentType() = media
            override fun contentLength() = bytes.size.toLong()
            override fun writeTo(sink: okio.BufferedSink) {
                sink.write(bytes)
            }
        }
        val builder = Request.Builder()
            .url("https://chatgpt.com/backend-api/codex/responses")
            .post(requestBody)
            .header("Authorization", "Bearer $token")
            .header("Version", "0.159.0")
            .header("Openai-Beta", "responses=experimental")
            .header("User-Agent", "codex_cli_rs/0.159.0 (Android; arm64)")
            .header("Originator", "codex_cli_rs")
        CodexSubscription.accountId(secrets)?.let { builder.header("Chatgpt-Account-Id", it) }
        val call = Http.client.newBuilder()
            .callTimeout(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
        val response = try {
            call.newCall(builder.build()).execute()
        } catch (_: Exception) {
            return@withContext null
        }
        response.use {
            if (!it.isSuccessful) return@use null
            collectText(JSONObject(it.body?.string().orEmpty()))
        }
    }

private suspend fun copilot(secrets: SecretStore, model: String, request: JudgeRequest, timeout: Long): String? {
    val session = CopilotSubscription.sessionToken(secrets) ?: return null
    val body = JSONObject().apply {
        put("model", model)
        put("temperature", 0.1)
        put("stream", false)
        put(
            "messages",
            JSONArray()
                .put(JSONObject().put("role", "system").put("content", JudgePrompt.systemText()))
                .put(JSONObject().put("role", "user").put("content", JudgePrompt.userText(request))),
        )
    }
    val headers = CopilotDeviceFlow.staticChatHeaders().toMutableMap()
    headers["Authorization"] = "Bearer $session"
    headers["X-Initiator"] = "user"
    headers["X-Request-Id"] = UUID.randomUUID().toString()
    val result = Http.postJson("${CopilotDeviceFlow.API_BASE}/chat/completions", body.toString(), headers, timeout)
    if (!result.ok) return null
    return JSONObject(result.body).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
}

private fun collectText(json: JSONObject): String? {
    json.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
    val output = json.optJSONArray("output") ?: return null
    val text = buildString {
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val bit = part.optString("text")
                if (bit.isNotBlank()) append(bit)
            }
        }
    }
    return text.ifBlank { null }
}

@Suppress("unused")
private val fingerprintKept = ClaudeCliMimicryHeaders.ALL
