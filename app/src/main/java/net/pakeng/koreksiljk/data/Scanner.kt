package net.pakeng.koreksiljk.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import net.pakeng.koreksiljk.omr.Gray
import net.pakeng.koreksiljk.omr.Omr
import net.pakeng.koreksiljk.omr.SheetResult
import net.pakeng.koreksiljk.omr.Thresholds
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Memuat foto, membaca LJK, dan menyimpan gambar lurus untuk tinjauan. */
object Scanner {
    private const val MAX_DIM = 2000

    fun loadBitmap(ctx: Context, uri: Uri): Bitmap {
        val opt = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opt) }
        if (opt.outWidth <= 0) throw IllegalArgumentException("File bukan gambar yang bisa dibuka (gunakan JPG/PNG).")
        var sample = 1
        while (max(opt.outWidth, opt.outHeight) / (sample * 2) >= MAX_DIM) sample *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, o2) }
            ?: throw IllegalArgumentException("Gambar tidak bisa dibuka.")
        val m = max(bmp.width, bmp.height)
        if (m <= MAX_DIM) return bmp
        val sc = MAX_DIM.toDouble() / m
        val out = Bitmap.createScaledBitmap(bmp, (bmp.width * sc).roundToInt(), (bmp.height * sc).roundToInt(), true)
        if (out != bmp) bmp.recycle()
        return out
    }

    class Scan(val sheet: SheetResult, val ans: List<Ans>)

    /** Membaca LJK; bila berhasil menyimpan gambar lurus ke [imgFile]. */
    fun scan(ctx: Context, uri: Uri, n: Int, th: Thresholds, imgFile: File): Scan {
        val bmp = loadBitmap(ctx, uri)
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        val r = Omr.readSheet(Gray.fromArgb(w, h, px), n, th)
        if (!r.ok) return Scan(r, emptyList())
        val (ww, wh, wp) = Omr.warp(w, h, px, r.H!!, 3)
        val out = Bitmap.createBitmap(wp, ww, wh, Bitmap.Config.ARGB_8888)
        imgFile.parentFile?.mkdirs()
        imgFile.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 75, it) }
        out.recycle()
        val code = mapOf("kosong" to '.', "x" to 'x', "tanda" to 't', "coret" to 'c')
        val ans = r.answers.map { a -> Ans(a.choice, a.status, a.marks.map { code[it] ?: '.' }.joinToString(""), a.note) }
        return Scan(r, ans)
    }
}
