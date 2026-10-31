/*
 * Copyright (C) OpenMinis contributors.
 * SPDX-License-Identifier: GPL-3.0-only
 *
 * Adapted from OpenMinis (GPL-3.0), https://github.com/OpenMinis/OpenMinis
 * Upstream:
 *   src/android/app/src/main/java/com/openminis/app/auth/ClaudeOAuthManager.kt
 *   src/android/app/src/main/java/com/openminis/app/auth/OpenAIOAuthManager.kt
 *   src/android/app/src/main/java/com/openminis/app/auth/CopilotOAuthManager.kt
 *   src/android/app/src/main/java/com/openminis/app/auth/OAuthManager.kt
 *
 * The protocol (client ids issued to other official clients, PKCE loopback,
 * Claude CLI fingerprint, Copilot device flow and session token) is unchanged.
 * Storage goes through this app's encrypted preferences. Nothing here runs
 * until the user accepts the in-app risk notice.
 */
package app.bungaealert.auth

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import app.bungaealert.BuildConfig
import app.bungaealert.auth.upstream.ClaudeCliMimicryHeaders
import app.bungaealert.auth.upstream.CopilotDeviceFlow
import app.bungaealert.auth.upstream.applyClaudeCliMimicryHeaders
import app.bungaealert.data.SecretStore
import app.bungaealert.net.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean

object AppVisibility {
    @Volatile
    var foreground: Boolean = false
        private set

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                foreground = true
            }

            override fun onStop(owner: LifecycleOwner) {
                foreground = false
            }
        })
    }

    suspend fun awaitForeground(timeoutMs: Long = 90_000) {
        val start = System.currentTimeMillis()
        while (!foreground && System.currentTimeMillis() - start < timeoutMs) {
            delay(200)
        }
    }
}

object Pkce {
    fun pair(byteLength: Int): Pair<String, String> {
        val bytes = ByteArray(byteLength)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
        )
        return verifier to challenge
    }

    fun state(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

/**
 * One-shot loopback listener for the OAuth redirect. Adapted from OpenMinis
 * OAuthCallbackServer: the provider redirects to localhost and this process
 * captures code and state.
 */
class LoopbackCodeServer(
    private val port: Int,
    private val onResult: (code: String?, state: String?, error: String?) -> Unit,
) {
    private var socket: ServerSocket? = null
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        val server = ServerSocket()
        server.reuseAddress = true
        server.bind(InetSocketAddress("127.0.0.1", port), 1)
        socket = server
        Thread {
            try {
                val client = server.accept()
                client.soTimeout = 8_000
                client.use { sock ->
                    val reader = BufferedReader(InputStreamReader(sock.getInputStream()))
                    val request = reader.readLine().orEmpty()
                    val query = request.substringAfter("?", "").substringBefore(" ")
                    val params = query.split("&").mapNotNull { part ->
                        val bits = part.split("=", limit = 2)
                        if (bits.size == 2) {
                            URLDecoder.decode(bits[0], "UTF-8") to URLDecoder.decode(bits[1], "UTF-8")
                        } else {
                            null
                        }
                    }.toMap()
                    val html = "<html><body><p>로그인 화면을 닫고 앱으로 돌아가 주세요.</p></body></html>"
                    val bytes = html.toByteArray()
                    val response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
                    val out = sock.getOutputStream()
                    out.write(response.toByteArray())
                    out.write(bytes)
                    out.flush()
                    onResult(params["code"], params["state"], params["error"])
                }
            } catch (_: Exception) {
                onResult(null, null, "callback_closed")
            }
        }.apply { isDaemon = true; name = "oauth-loopback"; start() }
    }

    fun stop() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
    }
}

object CustomTab {
    fun open(context: Context, url: String) {
        CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, Uri.parse(url))
    }
}

data class LoginResult(val ok: Boolean, val message: String)

object CodexSubscription {
    const val AUTH_URL = "https://auth.openai.com/oauth/authorize"
    const val TOKEN_URL = "https://auth.openai.com/oauth/token"
    const val CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val REDIRECT = "http://localhost:1455/auth/callback"
    private const val PORT = 1455

    suspend fun login(context: Context, secrets: SecretStore): LoginResult = withContext(Dispatchers.IO) {
        val (verifier, challenge) = Pkce.pair(64)
        val state = Pkce.state()
        val url = AUTH_URL + "?" + listOf(
            "client_id=$CLIENT_ID",
            "redirect_uri=${Uri.encode(REDIRECT)}",
            "response_type=code",
            "scope=${Uri.encode("openid profile email offline_access")}",
            "state=$state",
            "code_challenge=$challenge",
            "code_challenge_method=S256",
            "codex_cli_simplified_flow=true",
            "originator=codex_cli_rs",
            "id_token_add_organizations=true",
        ).joinToString("&")
        val code = waitForCode(context, PORT, url, state) ?: return@withContext LoginResult(false, "로그인이 취소되었거나 시간이 지났습니다.")
        val body = JSONObject().apply {
            put("grant_type", "authorization_code")
            put("client_id", CLIENT_ID)
            put("code", code)
            put("redirect_uri", REDIRECT)
            put("code_verifier", verifier)
        }
        val exchanged = postToken(TOKEN_URL, body, claudeFingerprint = false)
        if (!exchanged.ok) return@withContext LoginResult(false, "토큰 교환 실패 (${exchanged.code})")
        secrets.put(SecretStore.CODEX_TOKENS, exchanged.body)
        LoginResult(true, "ChatGPT·Codex 구독에 연결했습니다.")
    }

    suspend fun accessToken(secrets: SecretStore): String? = withContext(Dispatchers.IO) {
        val raw = secrets.get(SecretStore.CODEX_TOKENS)
        if (raw.isBlank()) return@withContext null
        val json = JSONObject(raw)
        val token = json.optString("access_token")
        if (token.isBlank()) return@withContext null
        val expireAt = json.optLong("expire_at", 0)
        if (expireAt > 0 && expireAt - System.currentTimeMillis() < 60_000) {
            val refresh = json.optString("refresh_token")
            if (refresh.isBlank()) return@withContext if (System.currentTimeMillis() < expireAt) token else null
            val body = JSONObject().apply {
                put("grant_type", "refresh_token")
                put("client_id", CLIENT_ID)
                put("refresh_token", refresh)
            }
            val refreshed = postToken(TOKEN_URL, body, claudeFingerprint = false)
            if (!refreshed.ok) return@withContext if (System.currentTimeMillis() < expireAt) token else null
            val next = JSONObject(refreshed.body)
            if (!next.has("refresh_token")) next.put("refresh_token", refresh)
            if (next.optString("id_token").isBlank() && json.optString("id_token").isNotBlank()) {
                next.put("id_token", json.optString("id_token"))
            }
            secrets.put(SecretStore.CODEX_TOKENS, next.toString())
            return@withContext next.optString("access_token").ifBlank { null }
        }
        token
    }

    fun accountId(secrets: SecretStore): String? {
        val raw = secrets.get(SecretStore.CODEX_TOKENS)
        if (raw.isBlank()) return null
        val json = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return null
        }
        val fromId = claims(json.optString("id_token"))
        val fromAccess = claims(json.optString("access_token"))
        return fromId?.first ?: fromAccess?.first
    }

    private fun claims(token: String): Pair<String?, String?>? {
        val parts = token.split(".")
        if (parts.size < 2) return null
        return try {
            val payload = JSONObject(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8))
            val auth = payload.optJSONObject("https://api.openai.com/auth")
            fun claim(name: String) = auth?.optString(name)?.takeIf { it.isNotEmpty() }
                ?: payload.optString(name).takeIf { it.isNotEmpty() }
            claim("chatgpt_account_id") to claim("chatgpt_plan_type")
        } catch (_: Exception) {
            null
        }
    }
}

object ClaudeSubscription {
    const val AUTH_URL = "https://claude.ai/oauth/authorize"
    const val TOKEN_URL = "https://claude.ai/v1/oauth/token"
    const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val REDIRECT = "http://localhost:54545/callback"
    private const val PORT = 54545

    fun identifierOrNull(): String? = BuildConfig.ANTHROPIC_OAUTH_IDENTIFIER_PROMPT.trim().ifBlank { null }

    suspend fun login(context: Context, secrets: SecretStore): LoginResult = withContext(Dispatchers.IO) {
        val (verifier, challenge) = Pkce.pair(96)
        val state = Pkce.state()
        val url = AUTH_URL + "?" + listOf(
            "client_id=$CLIENT_ID",
            "redirect_uri=${Uri.encode(REDIRECT)}",
            "response_type=code",
            "scope=${Uri.encode("org:create_api_key user:profile user:inference")}",
            "state=$state",
            "code_challenge=$challenge",
            "code_challenge_method=S256",
        ).joinToString("&")
        val code = waitForCode(context, PORT, url, state) ?: return@withContext LoginResult(false, "로그인이 취소되었거나 시간이 지났습니다.")
        val body = JSONObject().apply {
            put("grant_type", "authorization_code")
            put("client_id", CLIENT_ID)
            put("code", code)
            put("redirect_uri", REDIRECT)
            put("code_verifier", verifier)
            put("state", state)
        }
        val exchanged = postToken(TOKEN_URL, body, claudeFingerprint = true)
        if (!exchanged.ok) return@withContext LoginResult(false, "토큰 교환 실패 (${exchanged.code})")
        secrets.put(SecretStore.CLAUDE_TOKENS, exchanged.body)
        LoginResult(true, "Claude 구독에 연결했습니다.")
    }

    suspend fun accessToken(secrets: SecretStore): String? = withContext(Dispatchers.IO) {
        val raw = secrets.get(SecretStore.CLAUDE_TOKENS)
        if (raw.isBlank()) return@withContext null
        val json = JSONObject(raw)
        val token = json.optString("access_token")
        if (token.isBlank()) return@withContext null
        val expireAt = json.optLong("expire_at", 0)
        if (expireAt > 0 && expireAt - System.currentTimeMillis() <= 5 * 60 * 1000L) {
            val refresh = json.optString("refresh_token")
            if (refresh.isBlank()) return@withContext if (System.currentTimeMillis() < expireAt) token else null
            val body = JSONObject().apply {
                put("grant_type", "refresh_token")
                put("client_id", CLIENT_ID)
                put("refresh_token", refresh)
            }
            val refreshed = postToken(TOKEN_URL, body, claudeFingerprint = true)
            if (!refreshed.ok) return@withContext if (System.currentTimeMillis() < expireAt) token else null
            val next = JSONObject(refreshed.body)
            if (!next.has("refresh_token")) next.put("refresh_token", refresh)
            secrets.put(SecretStore.CLAUDE_TOKENS, next.toString())
            return@withContext next.optString("access_token").ifBlank { null }
        }
        token
    }
}

object CopilotSubscription {
    suspend fun startDevice(): CopilotDeviceFlow.DeviceAuthorization = withContext(Dispatchers.IO) {
        val form = formPost(
            CopilotDeviceFlow.DEVICE_CODE_URL,
            mapOf("client_id" to CopilotDeviceFlow.CLIENT_ID, "scope" to CopilotDeviceFlow.SCOPE),
        )
        if (!form.ok) throw IllegalStateException(form.body.ifBlank { form.error ?: "기기 코드를 받지 못했습니다." })
        CopilotDeviceFlow.parseDeviceAuthorization(JSONObject(form.body))
            ?: throw IllegalStateException("기기 코드 응답이 올바르지 않습니다.")
    }

    suspend fun finishDevice(
        secrets: SecretStore,
        auth: CopilotDeviceFlow.DeviceAuthorization,
    ): LoginResult = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + auth.expiresInSeconds * 1000
        var interval = auth.intervalSeconds
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000)
            val form = formPost(
                CopilotDeviceFlow.ACCESS_TOKEN_URL,
                mapOf(
                    "client_id" to CopilotDeviceFlow.CLIENT_ID,
                    "device_code" to auth.deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
            val json = try {
                JSONObject(form.body)
            } catch (_: Exception) {
                JSONObject()
            }
            when (val poll = CopilotDeviceFlow.classifyPoll(json, form.ok)) {
                is CopilotDeviceFlow.PollResult.Success -> {
                    secrets.put(SecretStore.COPILOT_GITHUB, poll.accessToken)
                    val session = exchangeSession(poll.accessToken, secrets)
                    return@withContext if (session != null) {
                        LoginResult(true, "GitHub Copilot에 연결했습니다.")
                    } else {
                        LoginResult(false, "GitHub에는 연결됐지만 Copilot 세션을 받지 못했습니다. 구독이 있는지 확인해 주세요.")
                    }
                }
                CopilotDeviceFlow.PollResult.Pending -> Unit
                CopilotDeviceFlow.PollResult.SlowDown -> interval = CopilotDeviceFlow.bumpedInterval(interval)
                is CopilotDeviceFlow.PollResult.Denied -> return@withContext LoginResult(false, "로그인이 거부되었습니다.")
                is CopilotDeviceFlow.PollResult.Expired -> return@withContext LoginResult(false, "코드가 만료되었습니다. 다시 시도해 주세요.")
                is CopilotDeviceFlow.PollResult.Fatal -> return@withContext LoginResult(false, poll.description)
            }
        }
        LoginResult(false, "기다리는 시간이 끝났습니다.")
    }

    suspend fun sessionToken(secrets: SecretStore): String? = withContext(Dispatchers.IO) {
        val github = secrets.get(SecretStore.COPILOT_GITHUB)
        if (github.isBlank()) return@withContext null
        val cached = secrets.get(SecretStore.COPILOT_SESSION)
        val exp = secrets.get(SecretStore.COPILOT_SESSION_EXP).toLongOrNull()
        val now = System.currentTimeMillis() / 1000
        if (cached.isNotBlank() && exp != null && !CopilotDeviceFlow.SessionToken(cached, exp).needsRefresh(now)) {
            return@withContext cached
        }
        exchangeSession(github, secrets)
    }

    private fun exchangeSession(githubToken: String, secrets: SecretStore): String? {
        val request = Request.Builder().url(CopilotDeviceFlow.COPILOT_TOKEN_URL).get()
        CopilotDeviceFlow.tokenExchangeHeaders(githubToken).forEach { (k, v) -> request.header(k, v) }
        val response = try {
            Http.client.newCall(request.build()).execute()
        } catch (_: Exception) {
            return null
        }
        return response.use {
            if (!it.isSuccessful) return@use null
            val json = JSONObject(it.body?.string().orEmpty())
            val session = CopilotDeviceFlow.parseSessionToken(json, System.currentTimeMillis() / 1000)
                ?: return@use null
            secrets.put(SecretStore.COPILOT_SESSION, session.token)
            secrets.put(SecretStore.COPILOT_SESSION_EXP, session.expiresAtEpochSeconds.toString())
            session.token
        }
    }
}

private data class TokenHttp(val ok: Boolean, val code: Int, val body: String)

private fun postToken(url: String, body: JSONObject, claudeFingerprint: Boolean): TokenHttp {
    val requestBuilder = Request.Builder()
        .url(url)
        .post(body.toString().toRequestBody("application/json".toMediaType()))
    if (claudeFingerprint) requestBuilder.applyClaudeCliMimicryHeaders()
    val response = try {
        Http.client.newCall(requestBuilder.build()).execute()
    } catch (e: Exception) {
        return TokenHttp(false, -1, e.message.orEmpty())
    }
    response.use {
        val text = it.body?.string().orEmpty()
        if (!it.isSuccessful) return TokenHttp(false, it.code, text)
        val json = JSONObject(text)
        val expiresIn = json.optLong("expires_in", 0)
        if (expiresIn > 0) json.put("expire_at", System.currentTimeMillis() + expiresIn * 1000)
        return TokenHttp(true, it.code, json.toString())
    }
}

private fun formPost(url: String, fields: Map<String, String>): app.bungaealert.net.HttpResult {
    val form = okhttp3.FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()
    val request = Request.Builder()
        .url(url)
        .post(form)
        .header("Accept", "application/json")
        .header("User-Agent", CopilotDeviceFlow.USER_AGENT)
        .build()
    return try {
        Http.client.newCall(request).execute().use { response ->
            app.bungaealert.net.HttpResult(response.code, response.body?.string().orEmpty(), null)
        }
    } catch (e: Exception) {
        app.bungaealert.net.HttpResult(-1, "", e.message)
    }
}

private suspend fun waitForCode(context: Context, port: Int, url: String, expectedState: String): String? {
    val holder = kotlinx.coroutines.CompletableDeferred<Triple<String?, String?, String?>>()
    val server = LoopbackCodeServer(port) { code, state, error ->
        if (!holder.isCompleted) holder.complete(Triple(code, state, error))
    }
    server.start()
    try {
        withContext(Dispatchers.Main) { CustomTab.open(context, url) }
        val (code, state, error) = kotlinx.coroutines.withTimeoutOrNull(180_000) { holder.await() }
            ?: return null
        if (!error.isNullOrBlank() || code.isNullOrBlank()) return null
        if (state != null && state != expectedState) return null
        AppVisibility.awaitForeground()
        return code
    } finally {
        server.stop()
    }
}
