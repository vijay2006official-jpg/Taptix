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

    @Volatile
    private var pendingPermissionInstall: Boolean = false

    fun getDownloadedApkFile(): File {
        val cacheDir = context.externalCacheDir ?: context.cacheDir
        return File(cacheDir, "taptix_update.apk")
    }

    fun hasDownloadedApk(): Boolean {
        val file = getDownloadedApkFile()
        return file.exists() && file.length() >= 1_000_000L
    }

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
                    if (remoteVersionCode > currentCode && apkUrl.isNotEmpty()) {
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
        if (!apkUrl.startsWith("https://", ignoreCase = true)) {
            onError("Insecure HTTP update URL rejected. Strict HTTPS required.")
            return
        }
        thread {
            try {
                var currentUrl = apkUrl
                var conn: HttpURLConnection
                var redirectCount = 0

                // Follow HTTP 301/302/307/308 redirects (required for GitHub Releases -> CDN)
                while (true) {
                    val url = URL(currentUrl)
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 15000
                        readTimeout = 25000
                        requestMethod = "GET"
                        useCaches = false
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

                val fileLength = conn.contentLengthLong
                val apkFile = getDownloadedApkFile()
                val tempFile = File(apkFile.parentFile, "taptix_update.apk.tmp")
                if (tempFile.exists()) tempFile.delete()

                conn.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16384)
                        var total: Long = 0
                        var count: Int
                        var lastReportedPct = -1
                        while (input.read(buffer).also { count = it } != -1) {
                            total += count
                            output.write(buffer, 0, count)
                            if (fileLength > 0L) {
                                val progress = ((total * 100L) / fileLength).toInt().coerceIn(0, 100)
                                if (progress != lastReportedPct) {
                                    lastReportedPct = progress
                                    mainHandler.post { onProgress(progress) }
                                }
                            }
                        }
                        output.flush()
                        try {
                            output.fd.sync()
                        } catch (_: Exception) {
                        }
                    }
                }

                if (tempFile.length() < 1_000_000L || (fileLength > 0L && tempFile.length() != fileLength)) {
                    val badLen = tempFile.length()
                    tempFile.delete()
                    mainHandler.post {
                        onError("Incomplete APK download ($badLen bytes)")
                    }
                    return@thread
                }

                if (apkFile.exists()) apkFile.delete()
                if (!tempFile.renameTo(apkFile)) {
                    tempFile.copyTo(apkFile, overwrite = true)
                    tempFile.delete()
                }

                mainHandler.post {
                    onProgress(100)
                    onComplete()
                    launchApkInstaller(apkFile, onStatusChange)
                }
            } catch (e: Exception) {
                Log.e("UpdateManager", "Download APK failed: ${e.message}")
                mainHandler.post { onError(e.message ?: "Download failed") }
            }
        }
    }

    fun installDownloadedApk(onStatusChange: ((String) -> Unit)? = null) {
        val apkFile = getDownloadedApkFile()
        if (!apkFile.exists() || apkFile.length() < 1_000_000L) {
            onStatusChange?.invoke("Error: Downloaded APK missing. Tap Retry.")
            return
        }
        launchApkInstaller(apkFile, onStatusChange)
    }

    fun resumePendingInstallIfPermitted(onStatusChange: ((String) -> Unit)? = null): Boolean {
        if (!pendingPermissionInstall) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            return false
        }
        val apkFile = getDownloadedApkFile()
        if (apkFile.exists() && apkFile.length() >= 1_000_000L) {
            pendingPermissionInstall = false
            launchApkInstaller(apkFile, onStatusChange)
            return true
        }
        return false
    }

    fun launchApkInstaller(apkFile: File, onStatusChange: ((String) -> Unit)? = null) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                pendingPermissionInstall = true
                onStatusChange?.invoke("Allow 'Install unknown apps', then tap Install Update Now")
                Toast.makeText(
                    context,
                    "Allow 'Install unknown apps' for Taptix to finish updating",
                    Toast.LENGTH_LONG
                ).show()
                val settingsIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
                return
            }

            pendingPermissionInstall = false

            val contentUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val knownNativePackages = setOf(
                "com.google.android.packageinstaller",
                "com.android.packageinstaller"
            )

            val installers = context.packageManager.queryIntentActivities(
                viewIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )

            for (resolveInfo in installers) {
                val pkg = resolveInfo.activityInfo?.packageName ?: continue
                try {
                    context.grantUriPermission(
                        pkg,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
            }

            val nativeInstaller = installers.firstOrNull { info ->
                val pkg = info.activityInfo?.packageName?.lowercase() ?: ""
                pkg in knownNativePackages && info.activityInfo.exported
            }

            var launched = false
            if (nativeInstaller != null) {
                try {
                    val targetedIntent = Intent(viewIntent).apply {
                        setClassName(
                            nativeInstaller.activityInfo.packageName,
                            nativeInstaller.activityInfo.name
                        )
                    }
                    context.startActivity(targetedIntent)
                    launched = true
                    Log.d(
                        "UpdateManager",
                        "Launched native package installer: ${nativeInstaller.activityInfo.packageName}"
                    )
                } catch (e: Exception) {
                    Log.w("UpdateManager", "Explicit installer launch failed, falling back: ${e.message}")
                }
            }

            if (!launched) {
                try {
                    context.startActivity(viewIntent)
                    launched = true
                } catch (e: Exception) {
                    Log.w("UpdateManager", "ACTION_VIEW installer fallback failed: ${e.message}")
                }
            }

            if (!launched) {
                @Suppress("DEPRECATION")
                val legacyInstallIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = contentUri
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(Intent.EXTRA_RETURN_RESULT, false)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(legacyInstallIntent)
            }

            onStatusChange?.invoke("Update downloaded • Tap 'Update' in Android prompt (or tap button below)")
        } catch (e: Exception) {
            Log.e("UpdateManager", "Failed to launch installer: ${e.message}")
            onStatusChange?.invoke("Error launching installer: ${e.message}")
            Toast.makeText(context, "Installer error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
