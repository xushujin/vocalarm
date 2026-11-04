package com.vocalarm.app.update

import com.vocalarm.app.domain.VersionComparator
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
)

data class ReleaseInfo(
    val tag: String,
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    val apk: ReleaseAsset?,
)

/**
 * Minimal GitHub Releases client. Uses HttpURLConnection on purpose: no third-party HTTP stack is
 * pulled into the app for a single request.
 */
class UpdateApi(
    private val owner: String,
    private val repo: String,
) {

    val isConfigured: Boolean
        get() = owner.isNotBlank() && repo.isNotBlank()

    suspend fun latestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext Result.failure(IOException("not-configured"))
        var connection: HttpURLConnection? = null
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "Vocalarm-Android")
            }
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(IOException("http-$code"))
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val tag = json.optString("tag_name").ifBlank {
                return@withContext Result.failure(IOException("missing-tag"))
            }
            val assetsJson = json.optJSONArray("assets")
            var apk: ReleaseAsset? = null
            if (assetsJson != null) {
                for (index in 0 until assetsJson.length()) {
                    val asset = assetsJson.optJSONObject(index) ?: continue
                    val name = asset.optString("name")
                    val downloadUrl = asset.optString("browser_download_url")
                    if (!name.endsWith(".apk", ignoreCase = true) || downloadUrl.isBlank()) continue
                    apk = ReleaseAsset(
                        name = name,
                        downloadUrl = downloadUrl,
                        size = asset.optLong("size", 0L),
                    )
                    break
                }
            }
            Result.success(
                ReleaseInfo(
                    tag = tag,
                    version = VersionComparator.normalize(tag),
                    title = json.optString("name").ifBlank { tag },
                    notes = json.optString("body").trim(),
                    pageUrl = json.optString("html_url"),
                    publishedAt = json.optString("published_at"),
                    apk = apk,
                ),
            )
        } catch (error: Exception) {
            Result.failure(IOException(error.message ?: "network", error))
        } finally {
            connection?.disconnect()
        }
    }
}
