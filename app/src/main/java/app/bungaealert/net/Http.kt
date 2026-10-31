package app.bungaealert.net

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .build()

    val streamClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun postJson(
        url: String,
        json: String,
        headers: Map<String, String>,
        timeoutMs: Long,
    ): HttpResult {
        val body = json.toRequestBody("application/json".toMediaType())
        val builder = Request.Builder().url(url).post(body)
        headers.forEach { (name, value) ->
            if (name.isNotBlank() && value.isNotBlank()) builder.header(name, value)
        }
        val callClient = client.newBuilder()
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()
        return execute(callClient, builder.build())
    }

    fun get(url: String, headers: Map<String, String>, timeoutMs: Long = 15_000): HttpResult {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (name, value) -> builder.header(name, value) }
        val callClient = client.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
        return execute(callClient, builder.build())
    }

    private fun execute(callClient: OkHttpClient, request: Request): HttpResult {
        return try {
            callClient.newCall(request).execute().use { response ->
                HttpResult(response.code, response.body?.string().orEmpty(), null)
            }
        } catch (e: Exception) {
            HttpResult(-1, "", e.message ?: e.javaClass.simpleName)
        }
    }
}

data class HttpResult(val code: Int, val body: String, val error: String?) {
    val ok: Boolean get() = code in 200..299
}
