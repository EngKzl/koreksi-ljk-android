package net.pakeng.koreksiljk.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import net.pakeng.koreksiljk.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Pembaruan aplikasi dari Cloudflare (ljk.pak-eng.net) — versi terbaru diterbitkan lewat GitHub Release. */
object Updater {
    const val ENDPOINT = "https://ljk.pak-eng.net/api/android/latest"

    data class Info(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

    private fun open(url: String): HttpURLConnection {
        var u = URL(url)
        repeat(6) {
            val c = (u.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = false
                setRequestProperty("User-Agent", "KoreksiLJK-Android/${BuildConfig.VERSION_NAME}")
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: throw RuntimeException("Redirect tanpa tujuan")
                c.disconnect(); u = URL(u, loc); return@repeat
            }
            if (code !in 200..299) { c.disconnect(); throw RuntimeException("Server menjawab $code") }
            return c
        }
        throw RuntimeException("Terlalu banyak pengalihan")
    }

    /** Mengembalikan info versi baru bila tersedia, null bila sudah terbaru. Dipanggil di thread latar. */
    fun check(): Info? {
        val c = open(ENDPOINT)
        val body = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val o = JSONObject(body)
        val vc = o.optInt("versionCode", 0)
        val url = o.optString("apkUrl")
        if (vc <= BuildConfig.VERSION_CODE || url.isBlank() || url == "null") return null
        return Info(vc, o.optString("versionName"), url, o.optString("notes"))
    }

    /** Mengunduh APK ke cache; [progress] 0..1. Dipanggil di thread latar. */
    fun download(ctx: Context, info: Info, progress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "update").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val f = File(dir, "KoreksiLJK-${info.versionName}.apk")
        val c = open(info.apkUrl)
        val total = c.contentLengthLong
        c.inputStream.use { inp ->
            f.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val r = inp.read(buf); if (r < 0) break
                    out.write(buf, 0, r); done += r
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
        c.disconnect()
        return f
    }

    /** True bila izin "instal aplikasi tidak dikenal" sudah diberikan. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
