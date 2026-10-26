package com.example.taptix.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)

class UpdateManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())

    fun getCurrentVersionCode(): Long {
        return try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
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
        } catch (e: Exception) {
            1L
        }
    }

    fun checkForUpdate(serverUrl: String, onResult: (UpdateInfo?) -> Unit) {
        if (!serverUrl.startsWith("https://", ignoreCase = true)) {
            Log.e("UpdateManager", "Rejected insecure HTTP update URL: $serverUrl")
            onResult(null)
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
                    if (remoteVersionCode > currentCode && apkUrl.isNotEmpty()) {
                        val updateInfo = UpdateInfo(remoteVersionCode, remoteVersionName, apkUrl, releaseNotes)
                        mainHandler.post { onResult(updateInfo) }
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
        if (!apkUrl.startsWith("https://", ignoreCase = true)) {
            onError("Insecure HTTP update URL rejected. Strict HTTPS required.")
            return
        }
        thread {
            try {
                var currentUrl = apkUrl
                var conn: HttpURLConnection
                var redirectCount = 0

                // Follow HTTP 301/302 redirects (required for GitHub Releases -> CDN)
                while (true) {
                    val url = URL(currentUrl)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 20000
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                    }

                    val code = conn.responseCode
                    if (code == HttpURLConnection.HTTP_MOVED_TEMP ||
                        code == HttpURLConnection.HTTP_MOVED_PERM ||
                        code == HttpURLConnection.HTTP_SEE_OTHER ||
                        code == 307 || code == 308
                    ) {
                        val newLocation = conn.getHeaderField("Location")
                        if (!newLocation.isNullOrEmpty() && redirectCount < 6) {
                            currentUrl = newLocation
                            redirectCount++
                            continue
                        }
                    }
                    break
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

                // Verify downloaded file size (should be > 1 MB)
                if (apkFile.length() < 1_000_000) {
                    mainHandler.post { onError("Incomplete APK download (${apkFile.length()} bytes)") }
                    return@thread
                }

                mainHandler.post {
                    onComplete()
                    launchApkInstaller(apkFile)
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Download APK failed: ${e.message}")
                mainHandler.post { onError(e.message ?: "Download failed") }
            }
        }
    }

    fun launchApkInstaller(apkFile: File) {
        try {
            // Check if app has permission to install unknown apps on Android 8.0+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                Toast.makeText(context, "Please allow 'Install unknown apps' permission for Taptix", Toast.LENGTH_LONG).show()
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }

            // Explicitly grant read URI permission to all package installer handlers
            val resInfoList = context.packageManager.queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY)
            for (resolveInfo in resInfoList) {
                val packageName = resolveInfo.activityInfo.packageName
                context.grantUriPermission(packageName, contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to launch installer: ${e.message}")
            Toast.makeText(context, "Installer failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
