package com.example.taptix.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

private val allowedUpdateHosts = setOf("github.com", "raw.githubusercontent.com", "api.github.com")

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

class UpdateManager(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private fun isSecureUrl(urlString: String): Boolean {
        return try {
            val uri = Uri.parse(urlString)
            val host = uri.host?.lowercase() ?: return false
            uri.scheme == "https" && host in allowedUpdateHosts
        } catch (_: Exception) {
            false
        }
    }

    fun checkForUpdate(serverUrl: String, onResult: (UpdateInfo?) -> Unit) {
        if (!isSecureUrl(serverUrl)) {
            mainHandler.post { onResult(null) }
            return
        }

        thread {
            try {
                val url = URL(serverUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val remoteVersionCode = json.optInt("versionCode", 1)
                    val remoteVersionName = json.optString("versionName", "1.0")
                    val apkUrl = json.optString("apkUrl", "")
                    val releaseNotes = json.optString("releaseNotes", "New performance and driver enhancements.")

                    val currentCode = getCurrentVersionCode()
                    if (remoteVersionCode > currentCode && apkUrl.isNotEmpty() && isSecureUrl(apkUrl)) {
                        mainHandler.post { onResult(UpdateInfo(remoteVersionCode, remoteVersionName, apkUrl, releaseNotes)) }
                        return@thread
                    }
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Check update failed: ${e.message}")
            }
            mainHandler.post { onResult(null) }
        }
    }

    fun downloadAndInstallApk(
        apkUrl: String,
        onProgress: (Int) -> Unit,
        onComplete: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isSecureUrl(apkUrl)) {
            mainHandler.post { onError("HTTPS-only update URL is required.") }
            return
        }

        thread {
            try {
                val url = URL(apkUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 20000
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                }

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    mainHandler.post { onError("Server error: HTTP ${conn.responseCode}") }
                    return@thread
                }

                val fileLength = conn.contentLength
                val cacheDir = context.externalCacheDir ?: context.cacheDir
                val apkFile = File(cacheDir, "taptix_update.apk")
                if (apkFile.exists()) apkFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        val buffer = ByteArray(8192)
                        var total: Long = 0
                        var count: Int
                        while (input.read(buffer).also { count = it } != -1) {
                            total += count
                            output.write(buffer, 0, count)
                            if (fileLength > 0) {
                                val progress = ((total * 100) / fileLength).toInt().coerceIn(0, 100)
                                mainHandler.post { onProgress(progress) }
                            }
                        }
                    }
                }

                if (apkFile.length() < 1_000_000L) {
                    mainHandler.post { onError("Incomplete APK download (${apkFile.length()} bytes)") }
                    return@thread
                }

                mainHandler.post { onComplete() }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Download APK failed: ${e.message}")
                mainHandler.post { onError(e.message ?: "Download failed") }
            }
        }
    }

    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }
        } catch (_: Exception) {
            1L
        }
    }
}
