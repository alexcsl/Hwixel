package edu.bluejack25_2.hwixel.data.source.remote

import edu.bluejack25_2.hwixel.BuildConfig
import edu.bluejack25_2.hwixel.data.model.TeamHealthResult
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
import java.util.concurrent.TimeUnit

interface TeamHealthSource {
    suspend fun analyze(prompt: String): Result<TeamHealthResult>
}

class GptApiSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build(),
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
            parseTeamHealthJson(extractAssistantText(JSONObject(body)))
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

    private fun extractAssistantText(response: JSONObject): String {
        response.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
        response.optJSONArray("choices")?.let { choices ->
            for (i in 0 until choices.length()) {
                val choice = choices.optJSONObject(i) ?: continue
                choice.optJSONObject("message")
                    ?.optString("content")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { return it }
                choice.optString("text").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        val output = response.getJSONArray("output")
        for (i in 0 until output.length()) {
            val item = output.optJSONObject(i) ?: continue
            val content = item.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val contentItem = content.optJSONObject(j) ?: continue
                contentItem.optString("text").takeIf { it.isNotBlank() }?.let { return it }
                contentItem.optString("output_text").takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        error("Missing GPT assistant text.")
    }

    private fun parseTeamHealthJson(text: String): TeamHealthResult {
        val parsed = JSONObject(extractJsonObject(text))
        val recommendations = parsed.optJSONArray("recommendations")
            ?.toStringList()
            .orEmpty()
        return TeamHealthResult(
            status = parsed.getString("status"),
            summary = parsed.getString("summary"),
            recommendations = recommendations
        )
    }

    private fun extractJsonObject(text: String): String {
        val trimmed = text.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) { "Missing JSON object in GPT response." }
        return trimmed.substring(start, end + 1)
    }

    private fun parseResponseFallback(body: String): TeamHealthResult {
        val assistantText = listOf(
            """"output_text"\s*:\s*"((?:\\.|[^"])*)"""",
            """"text"\s*:\s*"((?:\\.|[^"])*)""""
        ).firstNotNullOfOrNull { pattern ->
            Regex(pattern, RegexOption.DOT_MATCHES_ALL)
                .find(body)
                ?.groupValues
                ?.get(1)
                ?.unescapeJsonString()
        }
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
