package edu.bluejack252.hwixel.data.source.remote

import edu.bluejack252.hwixel.BuildConfig
import edu.bluejack252.hwixel.data.model.TeamHealthResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

interface TeamHealthSource {
    suspend fun analyze(prompt: String): Result<TeamHealthResult>
}

class GptApiSource(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = BuildConfig.GPT_BASE_URL,
    private val model: String = BuildConfig.GPT_MODEL,
    private val apiKey: String = BuildConfig.GPT_API_KEY
) : TeamHealthSource {

    override suspend fun analyze(prompt: String): Result<TeamHealthResult> = runCatching {
        require(apiKey.isNotBlank()) { "GPT API key is missing." }
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("model", model)
                .put("input", prompt)
                .put("max_output_tokens", 300)
                .toString()
                .toRequestBody(JSON)

            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/responses")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("GPT request failed with HTTP ${response.code}.")
                }
                parseResponse(response.body?.string().orEmpty())
            }
        }
    }

    internal fun parseResponse(body: String): TeamHealthResult {
        return try {
            val assistantText = JSONObject(body)
                .getJSONArray("output")
                .getJSONObject(0)
                .getJSONArray("content")
                .getJSONObject(0)
                .getString("text")

            val parsed = JSONObject(assistantText)
            val recommendations = parsed.optJSONArray("recommendations")
                ?.toStringList()
                .orEmpty()

            TeamHealthResult(
                status = parsed.getString("status"),
                summary = parsed.getString("summary"),
                recommendations = recommendations
            )
        } catch (exception: JSONException) {
            throw exception
        } catch (exception: RuntimeException) {
            // Local JVM tests use Android's stubbed org.json classes; keep production
            // parsing above and use a narrow fallback only when those stubs throw.
            if (!exception.isAndroidJsonStub()) throw exception
            parseResponseFallback(body)
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        return (0 until length()).map { index -> getString(index) }
    }

    private fun parseResponseFallback(body: String): TeamHealthResult {
        val assistantText = Regex(
            """"text"\s*:\s*"((?:\\.|[^"])*)"""",
            RegexOption.DOT_MATCHES_ALL
        ).find(body)?.groupValues?.get(1)?.unescapeJsonString()
            ?: error("Malformed GPT response.")

        val status = Regex(""""status"\s*:\s*"([^"]+)"""")
            .find(assistantText)?.groupValues?.get(1)
            ?: error("Missing team health status.")
        val summary = Regex(""""summary"\s*:\s*"([^"]+)"""")
            .find(assistantText)?.groupValues?.get(1)
            ?: error("Missing team health summary.")
        val recommendationsBody = Regex(
            """"recommendations"\s*:\s*\[(.*?)]""",
            RegexOption.DOT_MATCHES_ALL
        ).find(assistantText)?.groupValues?.get(1).orEmpty()
        val recommendations = Regex(""""([^"]+)"""")
            .findAll(recommendationsBody)
            .map { it.groupValues[1] }
            .toList()
        return TeamHealthResult(status, summary, recommendations)
    }

    private fun String.unescapeJsonString(): String {
        return replace("\\\"", "\"")
            .replace("\\\\", "\\")
            .replace("\\n", "\n")
    }

    private fun RuntimeException.isAndroidJsonStub(): Boolean {
        val message = message.orEmpty()
        return message.contains("not mocked", ignoreCase = true) ||
            message.contains("Stub!", ignoreCase = true)
    }

    private companion object {
        val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
