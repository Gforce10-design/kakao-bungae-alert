package app.bungaealert.ai

import app.bungaealert.net.Http
import app.bungaealert.net.HttpResult
import java.net.URLEncoder

/** API-key providers whose model id can be chosen in settings. */
enum class ModelProvider(val label: String, val recommended: List<String>) {
    Gemini("Gemini", listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro")),
    OpenAI("OpenAI", listOf("gpt-4.1-mini", "gpt-4.1-nano", "gpt-4.1", "gpt-4o-mini")),
    Claude("Claude", listOf("claude-sonnet-4-5", "claude-haiku-4-5", "claude-opus-4-1")),
}

/**
 * Lightweight check of an API key + model id: a single GET on the provider's "get model" endpoint
 * (no tokens are generated). [get] is injectable for tests.
 */
object ModelValidator {
    private val SAFE_ID = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,99}")

    fun normalize(provider: ModelProvider, raw: String): String {
        val trimmed = raw.trim()
        return if (provider == ModelProvider.Gemini) trimmed.removePrefix("models/") else trimmed
    }

    /** Returns null when the model is valid, otherwise a Korean error message for the UI. */
    fun validate(
        provider: ModelProvider,
        key: String,
        model: String,
        get: (url: String, headers: Map<String, String>) -> HttpResult = { url, headers -> Http.get(url, headers) },
    ): String? {
        val id = normalize(provider, model)
        if (!SAFE_ID.matches(id)) return "모델 이름이 올바르지 않습니다"
        if (key.isBlank()) return "${provider.label} 키를 먼저 입력하세요"
        val encoded = URLEncoder.encode(id, "UTF-8")
        val (url, headers) = when (provider) {
            ModelProvider.Gemini ->
                "https://generativelanguage.googleapis.com/v1beta/models/$encoded" to mapOf("x-goog-api-key" to key)
            ModelProvider.OpenAI ->
                "https://api.openai.com/v1/models/$encoded" to mapOf("Authorization" to "Bearer $key")
            ModelProvider.Claude ->
                "https://api.anthropic.com/v1/models/$encoded" to mapOf(
                    "x-api-key" to key,
                    "anthropic-version" to "2023-06-01",
                )
        }
        return interpret(provider, get(url, headers))
    }

    fun interpret(provider: ModelProvider, result: HttpResult): String? = when {
        result.ok -> null
        result.code == -1 -> "확인하지 못했습니다. 네트워크를 확인하세요"
        result.code == 401 || result.code == 403 -> "${provider.label} 키가 거부되었습니다 (${result.code})"
        result.code == 404 -> "${provider.label}에 없는 모델입니다"
        result.code == 400 && provider == ModelProvider.Gemini && result.body.contains("API key", ignoreCase = true) ->
            "${provider.label} 키가 올바르지 않습니다 (400)"
        result.code == 429 -> "요청이 많아 확인하지 못했습니다 (429). 잠시 후 다시 시도하세요"
        else -> "모델을 확인하지 못했습니다 (${result.code})"
    }
}
