package edu.bluejack25_2.hwixel.data.source.remote

import edu.bluejack25_2.hwixel.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

interface AttachmentUploadSource {
    /** Uploads bytes and returns the public URL. */
    suspend fun upload(
        taskId: String,
        originalFileName: String,
        bytes: ByteArray,
        contentType: String
    ): Result<String>

    /** Removes a previously-uploaded object given its public URL. No-op for non-Supabase URLs. */
    suspend fun delete(publicUrl: String): Result<Unit>

    /** True when [url] points at this storage backend. */
    fun isOwnedUrl(url: String): Boolean
}

class SupabaseStorageSource(
    private val baseUrl: String = BuildConfig.SUPABASE_URL,
    private val anonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val bucket: String = BuildConfig.SUPABASE_BUCKET,
    private val client: OkHttpClient = defaultClient()
) : AttachmentUploadSource {

    override suspend fun upload(
        taskId: String,
        originalFileName: String,
        bytes: ByteArray,
        contentType: String
    ): Result<String> = runCatching {
        require(baseUrl.isNotBlank()) { "Supabase URL missing — set supabase.url in local.properties." }
        require(anonKey.isNotBlank()) { "Supabase anon key missing — set supabase.anon.key in local.properties." }

        val safeName = sanitizeFileName(originalFileName)
        val objectPath = "${taskId.ifBlank { "misc" }}/${UUID.randomUUID()}_$safeName"
        val media = contentType.takeIf { it.isNotBlank() }?.toMediaTypeOrNull()
            ?: "application/octet-stream".toMediaTypeOrNull()

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/storage/v1/object/$bucket/$objectPath")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .addHeader("Content-Type", contentType.ifBlank { "application/octet-stream" })
            .addHeader("x-upsert", "false")
            .post(bytes.toRequestBody(media))
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = response.body?.string().orEmpty()
                    throw IOException("Supabase upload failed: HTTP ${response.code}. $body")
                }
                publicUrlFor(objectPath)
            }
        }
    }

    override suspend fun delete(publicUrl: String): Result<Unit> = runCatching {
        if (!isOwnedUrl(publicUrl)) return@runCatching
        val prefix = "${baseUrl.trimEnd('/')}/storage/v1/object/public/$bucket/"
        if (!publicUrl.startsWith(prefix)) return@runCatching
        val objectPath = publicUrl.removePrefix(prefix)
        if (objectPath.isBlank()) return@runCatching

        val request = Request.Builder()
            .url("${baseUrl.trimEnd('/')}/storage/v1/object/$bucket/$objectPath")
            .addHeader("apikey", anonKey)
            .addHeader("Authorization", "Bearer $anonKey")
            .delete()
            .build()

        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 404) {
                    throw IOException("Supabase delete failed: HTTP ${response.code}.")
                }
            }
        }
    }

    override fun isOwnedUrl(url: String): Boolean {
        if (baseUrl.isBlank()) return false
        return url.startsWith("${baseUrl.trimEnd('/')}/storage/v1/object/public/$bucket/")
    }

    private fun publicUrlFor(objectPath: String): String =
        "${baseUrl.trimEnd('/')}/storage/v1/object/public/$bucket/$objectPath"

    private fun sanitizeFileName(name: String): String {
        val trimmed = name.trim().ifBlank { "file" }
        return trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80)
    }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
