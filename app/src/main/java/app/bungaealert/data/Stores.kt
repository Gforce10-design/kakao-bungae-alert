package app.bungaealert.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import app.bungaealert.detect.AlertMode
import app.bungaealert.detect.BackendIds
import app.bungaealert.detect.KeywordCodec
import app.bungaealert.detect.MatchKeyword
import app.bungaealert.detect.MatchMode
import app.bungaealert.detect.PostType
import app.bungaealert.detect.TopicGenerator
import app.bungaealert.detect.WatchRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class AppSettings(
    val onboardingDone: Boolean = false,
    val sensitivity: Int = 40,
    val borderlineBand: Int = 12,
    val maxRingSeconds: Int = 180,
    val shortJudgeTimeoutMs: Int = 8_000,
    val grokTimeoutSeconds: Int = 45,
    val ntfyServer: String = "https://ntfy.sh",
    val ntfyTopic: String = "",
    val backendOrder: List<String> = BackendIds.DEFAULT_ORDER,
    val enabledBackends: Set<String> = setOf(BackendIds.GEMINI_NANO),
    val geminiModel: String = "gemini-2.5-flash",
    val openaiModel: String = "gpt-4.1-mini",
    val claudeModel: String = "claude-sonnet-4-5",
    val codexModel: String = "gpt-5.4",
    val copilotModel: String = "gpt-4.1",
    val grokHeaderName: String = "Authorization",
    val subscriptionRiskAccepted: Boolean = false,
    val listenerKeepAlive: Boolean = true,
)

data class DetectionRecord(
    val id: String,
    val atMillis: Long,
    val chatName: String,
    val sender: String?,
    val text: String,
    val score: Int,
    val meetupScore: Int,
    val giveawayScore: Int,
    val alerted: Boolean,
    val alertType: String?,
    val summary: String,
)

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bungae_settings", Context.MODE_PRIVATE)
    private val stateFlow = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = stateFlow

    fun current(): AppSettings {
        val value = stateFlow.value.let { settings ->
            if (settings.ntfyTopic.isNotBlank()) settings
            else settings.copy(ntfyTopic = TopicGenerator.random())
        }
        if (value != stateFlow.value) update(value)
        return stateFlow.value
    }

    fun update(settings: AppSettings) {
        val normalized = settings.copy(
            sensitivity = settings.sensitivity.coerceIn(0, 100),
            borderlineBand = settings.borderlineBand.coerceIn(0, 40),
            maxRingSeconds = settings.maxRingSeconds.coerceIn(15, 30 * 60),
            shortJudgeTimeoutMs = settings.shortJudgeTimeoutMs.coerceIn(2_000, 30_000),
            grokTimeoutSeconds = settings.grokTimeoutSeconds.coerceIn(5, 180),
            ntfyServer = settings.ntfyServer.trim().ifBlank { "https://ntfy.sh" },
            backendOrder = (settings.backendOrder + BackendIds.DEFAULT_ORDER).distinct()
                .filter { it in BackendIds.DEFAULT_ORDER },
        )
        prefs.edit()
            .putString("json", toJson(normalized).toString())
            .commit()
        stateFlow.value = normalized
    }

    private fun read(): AppSettings {
        val raw = prefs.getString("json", null) ?: return AppSettings()
        return try {
            fromJson(JSONObject(raw))
        } catch (_: Exception) {
            AppSettings()
        }
    }

    companion object {
        fun toJson(settings: AppSettings): JSONObject = JSONObject().apply {
            put("onboardingDone", settings.onboardingDone)
            put("sensitivity", settings.sensitivity)
            put("borderlineBand", settings.borderlineBand)
            put("maxRingSeconds", settings.maxRingSeconds)
            put("shortJudgeTimeoutMs", settings.shortJudgeTimeoutMs)
            put("grokTimeoutSeconds", settings.grokTimeoutSeconds)
            put("ntfyServer", settings.ntfyServer)
            put("ntfyTopic", settings.ntfyTopic)
            put("backendOrder", JSONArray(settings.backendOrder))
            put("enabledBackends", JSONArray(settings.enabledBackends.toList()))
            put("geminiModel", settings.geminiModel)
            put("openaiModel", settings.openaiModel)
            put("claudeModel", settings.claudeModel)
            put("codexModel", settings.codexModel)
            put("copilotModel", settings.copilotModel)
            put("grokHeaderName", settings.grokHeaderName)
            put("subscriptionRiskAccepted", settings.subscriptionRiskAccepted)
            put("listenerKeepAlive", settings.listenerKeepAlive)
        }

        fun fromJson(json: JSONObject): AppSettings = AppSettings(
            onboardingDone = json.optBoolean("onboardingDone"),
            sensitivity = json.optInt("sensitivity", 40),
            borderlineBand = json.optInt("borderlineBand", 12),
            maxRingSeconds = json.optInt("maxRingSeconds", 180),
            shortJudgeTimeoutMs = json.optInt("shortJudgeTimeoutMs", 8_000),
            grokTimeoutSeconds = json.optInt("grokTimeoutSeconds", 45),
            ntfyServer = json.optString("ntfyServer", "https://ntfy.sh"),
            ntfyTopic = json.optString("ntfyTopic"),
            backendOrder = json.optJSONArray("backendOrder").toStringList().ifEmpty { BackendIds.DEFAULT_ORDER },
            enabledBackends = json.optJSONArray("enabledBackends")?.toStringList()?.toSet()
                ?: setOf(BackendIds.GEMINI_NANO),
            geminiModel = json.optString("geminiModel", "gemini-2.5-flash"),
            openaiModel = json.optString("openaiModel", "gpt-4.1-mini"),
            claudeModel = json.optString("claudeModel", "claude-sonnet-4-5"),
            codexModel = json.optString("codexModel", "gpt-5.4"),
            copilotModel = json.optString("copilotModel", "gpt-4.1"),
            grokHeaderName = json.optString("grokHeaderName", "Authorization"),
            subscriptionRiskAccepted = json.optBoolean("subscriptionRiskAccepted"),
            listenerKeepAlive = json.optBoolean("listenerKeepAlive", true),
        )
    }
}

class RulesRepository(context: Context) {
    private val file = File(context.filesDir, "watch-rules.json")
    private val stateFlow = MutableStateFlow(read())
    val state: StateFlow<List<WatchRule>> = stateFlow

    fun current(): List<WatchRule> = stateFlow.value

    fun save(rules: List<WatchRule>) {
        val array = JSONArray()
        rules.forEach { rule ->
            array.put(JSONObject().apply {
                put("id", rule.id)
                put("pattern", rule.pattern)
                put("matchMode", rule.matchMode.wire)
                put("detectMeetup", rule.detectMeetup)
                put("detectGiveaway", rule.detectGiveaway)
                put("alertMode", rule.alertMode.wire)
                put("enabled", rule.enabled)
            })
        }
        file.writeText(array.toString())
        stateFlow.value = rules
    }

    private fun read(): List<WatchRule> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        WatchRule(
                            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
                            pattern = obj.optString("pattern"),
                            matchMode = MatchMode.fromWire(obj.optString("matchMode")),
                            detectMeetup = obj.optBoolean("detectMeetup", true),
                            detectGiveaway = obj.optBoolean("detectGiveaway", true),
                            alertMode = AlertMode.fromWire(obj.optString("alertMode")),
                            enabled = obj.optBoolean("enabled", true),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class KeywordsRepository(context: Context) {
    private val file = File(context.filesDir, "match-keywords.json")
    private val stateFlow = MutableStateFlow(read())
    val state: StateFlow<List<MatchKeyword>> = stateFlow

    fun current(): List<MatchKeyword> = stateFlow.value

    fun save(keywords: List<MatchKeyword>) {
        write(keywords)
        stateFlow.value = keywords
    }

    private fun read(): List<MatchKeyword> {
        val raw = if (!file.exists()) {
            null
        } else {
            try {
                file.readText()
            } catch (_: Exception) {
                null
            }
        }
        val loaded = KeywordCodec.load(raw)
        if (raw == null || KeywordCodec.isCorrupt(raw)) write(loaded)
        return loaded
    }

    private fun write(keywords: List<MatchKeyword>) {
        try {
            file.writeText(KeywordCodec.toJson(keywords))
        } catch (_: Exception) {
            // The in-memory list still applies for this process.
        }
    }
}

class DetectionLogRepository(context: Context) {
    private val file = File(context.filesDir, "detections.json")
    private val stateFlow = MutableStateFlow(read())
    val state: StateFlow<List<DetectionRecord>> = stateFlow

    @Synchronized
    fun add(record: DetectionRecord) {
        val next = (listOf(record) + stateFlow.value).take(100)
        val array = JSONArray()
        next.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("atMillis", item.atMillis)
                put("chatName", item.chatName)
                put("sender", item.sender ?: "")
                put("text", item.text)
                put("score", item.score)
                put("meetupScore", item.meetupScore)
                put("giveawayScore", item.giveawayScore)
                put("alerted", item.alerted)
                put("alertType", item.alertType ?: "")
                put("summary", item.summary)
            })
        }
        file.writeText(array.toString())
        stateFlow.value = next
    }

    private fun read(): List<DetectionRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    add(
                        DetectionRecord(
                            id = obj.optString("id"),
                            atMillis = obj.optLong("atMillis"),
                            chatName = obj.optString("chatName"),
                            sender = obj.optString("sender").ifBlank { null },
                            text = obj.optString("text"),
                            score = obj.optInt("score"),
                            meetupScore = obj.optInt("meetupScore"),
                            giveawayScore = obj.optInt("giveawayScore"),
                            alerted = obj.optBoolean("alerted"),
                            alertType = obj.optString("alertType").ifBlank { null },
                            summary = obj.optString("summary"),
                        ),
                    )
                }
            }.take(100)
        } catch (_: Exception) {
            emptyList()
        }
    }
}

class SecretStore(context: Context) {
    private val prefs = create(context)

    fun get(key: String): String = prefs?.getString(key, "").orEmpty()
    fun put(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.commit()
    }
    fun clear(key: String) = put(key, "")

    val available: Boolean get() = prefs != null

    companion object {
        const val GEMINI_KEY = "gemini_api_key"
        const val OPENAI_KEY = "openai_api_key"
        const val CLAUDE_KEY = "claude_api_key"
        const val GROK_URL = "grok_webhook_url"
        const val GROK_HEADER = "grok_auth_header"
        const val CODEX_TOKENS = "codex_tokens"
        const val CLAUDE_TOKENS = "claude_tokens"
        const val COPILOT_GITHUB = "copilot_github_token"
        const val COPILOT_SESSION = "copilot_session_token"
        const val COPILOT_SESSION_EXP = "copilot_session_expires"

        private fun create(context: Context) = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "bungae_secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            null
        }
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
        }
    }
}
