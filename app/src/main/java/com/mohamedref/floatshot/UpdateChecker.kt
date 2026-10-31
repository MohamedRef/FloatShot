package com.mohamedref.floatshot

import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal sealed interface UpdateResult {
    data class Available(val version: String) : UpdateResult
    data object UpToDate : UpdateResult
    data object NoPublishedRelease : UpdateResult
    data object InvalidVersion : UpdateResult
    data object Failed : UpdateResult
}

/**
 * Reads only official public release metadata. Does not download or install updates.
 */
internal object UpdateChecker {
    private const val API = "https://api.github.com/repos/MohamedRef/FloatShot/releases/latest"
    const val RELEASE_PAGE = "https://github.com/MohamedRef/FloatShot/releases/latest"
    private const val RESPONSE_LIMIT = 512 * 1024

    fun check(installedVersion: String): UpdateResult {
        val connection = try {
            URL(API).openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return UpdateResult.Failed
        }
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 7000
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "FloatShot-Android")
            when (connection.responseCode) {
                HttpURLConnection.HTTP_NOT_FOUND -> UpdateResult.NoPublishedRelease
                HttpURLConnection.HTTP_OK -> {
                    val release = JSONObject(readLimited(connection.inputStream))
                    if (release.optBoolean("draft") || release.optBoolean("prerelease")) {
                        UpdateResult.Failed
                    } else {
                        val tag = release.optString("tag_name")
                        when (VersionComparator.isNewer(tag, installedVersion)) {
                            true -> UpdateResult.Available(tag)
                            false -> UpdateResult.UpToDate
                            null -> UpdateResult.InvalidVersion
                        }
                    }
                }
                else -> UpdateResult.Failed
            }
        } catch (_: IOException) {
            UpdateResult.Failed
        } catch (_: JSONException) {
            UpdateResult.Failed
        } finally {
            connection.disconnect()
        }
    }

    private fun readLimited(stream: InputStream): String = stream.use {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        while (true) {
            val count = it.read(chunk)
            if (count < 0) break
            if (output.size() + count > RESPONSE_LIMIT) {
                throw IOException("Release metadata exceeds allowed size")
            }
            output.write(chunk, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }
}
