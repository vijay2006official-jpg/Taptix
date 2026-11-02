package com.example.taptix.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
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
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
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
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache")
                    instanceFollowRedirects = true
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val response = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val remoteVersionCode = json.optInt("versionCode", 1)
                    val remoteVersionName = json.optString("versionName", "1.0")
                    val apkUrl = json.optString("apkUrl", "")
                    val releaseNotes = json.optString(
                        "releaseNotes",
                        "New performance and driver enhancements."
                    )

                    val currentCode = getCurrentVersionCode()
                    if (remoteVersionCode > currentCode) {
                        val updateInfo = UpdateInfo(
                            remoteVersionCode,
                            remoteVersionName,
                            apkUrl,
                            releaseNotes
                        )
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
        onError: (String) -> Unit,
        onStatusChange: ((String) -> Unit)? = null
    ) {
        mainHandler.post {
            onProgress(100)
            onStatusChange?.invoke("Opening official store listing...")
            onComplete()
            openOfficialStoreListing()
        }
    }

    fun hasDownloadedApk(): Boolean = false

    fun installDownloadedApk(onStatusChange: ((String) -> Unit)? = null) {
        onStatusChange?.invoke("Opening official store listing...")
        openOfficialStoreListing()
    }

    fun resumePendingInstallIfPermitted(onStatusChange: ((String) -> Unit)? = null): Boolean = false

    fun openOfficialStoreListing() {
        val packageName = context.packageName
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")).apply {
                if (context !is android.app.Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(webIntent)
        }
    }
}
