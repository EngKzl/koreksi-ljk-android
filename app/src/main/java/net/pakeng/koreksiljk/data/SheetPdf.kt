package net.pakeng.koreksiljk.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import net.pakeng.koreksiljk.omr.Layout
import java.io.File

/** Menggambar LJK A4 (sama dengan versi web) ke PDF. */
object SheetPdf {
    private const val S = 72f / 25.4f // pt per mm

    fun draw(c: Canvas, e: Exam, s: Float = S) {
        val n = e.n.coerceIn(1, 60)
        val p = e.paket.coerceIn(1, 5)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0x66, 0x66, 0x66); style = Paint.Style.STROKE; strokeWidth = 0.3f * s }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 0.25f * s }
        val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0xBB, 0xBB, 0xBB); style = Paint.Style.STROKE; strokeWidth = 0.2f * s; pathEffect = DashPathEffect(floatArrayOf(1f * s, 1f * s), 0f) }
        fun txt(size: Float, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; textSize = size * s; textAlign = align; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        fun rect(x: Double, y: Double, w: Double, h: Double, paint: Paint) = c.drawRect((x * s).toFloat(), (y * s).toFloat(), ((x + w) * s).toFloat(), ((y + h) * s).toFloat(), paint)
        fun text(t: String, x: Double, y: Double, paint: Paint) = c.drawText(t, (x * s).toFloat(), (y * s).toFloat(), paint)

        c.drawColor(Color.WHITE)
        for (m in Layout.markers) rect(m[0] - Layout.MS / 2, m[1] - Layout.MS / 2, Layout.MS, Layout.MS, fill)
        val o = Layout.orient; rect(o.x, o.y, o.w, o.h, fill)

        text("LEMBAR JAWABAN", 105.0, 14.0, txt(5f, true, Paint.Align.CENTER))
        text(e.sekolah, 105.0, 20.0, txt(3.6f, false, Paint.Align.CENTER))
        text(e.nama + if (e.mapel.isNotBlank()) " — " + e.mapel else "", 105.0, 25.0, txt(3.2f, false, Paint.Align.CENTER))
        val f34 = txt(3.4f)
        text("Nama", 24.0, 36.0, f34); c.drawLine(40 * s, 36.5f * s, 120 * s, 36.5f * s, line)
        text("Kelas", 126.0, 36.0, f34); c.drawLine(140 * s, 36.5f * s, 188 * s, 36.5f * s, line)
        text("Mapel", 24.0, 43.0, f34); c.drawLine(40 * s, 43.5f * s, 120 * s, 43.5f * s, line)
        text("Tanggal", 126.0, 43.0, f34); c.drawLine(140 * s, 43.5f * s, 188 * s, 43.5f * s, line)

        fun lm(x: Double, y: Double) = rect(x - Layout.LM / 2, y - Layout.LM / 2, Layout.LM, Layout.LM, fill)
        val labB = txt(3f, true, Paint.Align.RIGHT)
        val labS = txt(2.8f, true, Paint.Align.RIGHT)
        fun boxWithLabel(r: net.pakeng.koreksiljk.omr.Rect, t: String, lp: Paint) {
            rect(r.x, r.y, r.w, r.h, box); text(t, r.x - 1.2, r.y + 4.2, lp)
        }

        for (m in Layout.idMarkers()) lm(m[0], m[1])
        text("NO. ABSEN (silang satu angka tiap baris)", 30.0, 53.5, txt(3f, true))
        listOf("Puluhan", "Satuan").forEachIndexed { r, t ->
            text(t, 24.0, Layout.idY[r] + 4, txt(2.8f))
            for (d in 0 until 10) boxWithLabel(Layout.idBox(r, d), d.toString(), labB)
        }
        if (p > 1) {
            text("PAKET SOAL", Layout.PAKET_X - 3, 53.5, txt(3f, true))
            for (i in 0 until p) boxWithLabel(Layout.paketBox(i), LETTERS[i], labB)
        }

        val blocks = (n + Layout.ROWS - 1) / Layout.ROWS
        val num = txt(3.4f, true, Paint.Align.RIGHT)
        for (cb in 0 until blocks) {
            for (m in Layout.blockMarkers(cb)) lm(m[0], m[1])
            for (r in 0 until Layout.ROWS) {
                val q = cb * Layout.ROWS + r
                if (q >= n) break
                val b0 = Layout.ansBox(q, 0)
                text("${q + 1}.", Layout.ansX0[cb] + 6.5, b0.y + 4.2, num)
                for (k in 0 until 5) boxWithLabel(Layout.ansBox(q, k), LETTERS[k], labS)
                if (r % 5 == 4 && r < Layout.ROWS - 1 && q < n - 1) {
                    val y = (b0.y + 6.75).toFloat() * s
                    c.drawLine((Layout.ansX0[cb] + 4).toFloat() * s, y, (Layout.ansX0[cb] + 49).toFloat() * s, y, dash)
                }
            }
        }
        text("PETUNJUK:", 24.0, 268.0, txt(2.9f, true))
        val pt = txt(2.7f)
        text("1. Beri tanda SILANG (X) di dalam kotak jawaban yang benar. Gunakan pulpen hitam/biru atau pensil 2B.", 24.0, 272.5, pt)
        text("2. Mengganti jawaban: beri tanda SAMA DENGAN (=) di atas silang lama (atau hitamkan penuh), lalu silang jawaban baru.", 24.0, 276.5, pt)
        text("3. Jangan melipat/mengotori lembar dan kotak hitam di sudut. Satu soal hanya boleh SATU silang.", 24.0, 280.5, pt)
    }

    /** Membuat PDF berisi [copies] halaman LJK. */
    fun writePdf(e: Exam, file: File, copies: Int = 1) {
        val doc = PdfDocument()
        for (i in 1..copies.coerceIn(1, 60)) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i).create())
            draw(page.canvas, e)
            doc.finishPage(page)
        }
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }
}
