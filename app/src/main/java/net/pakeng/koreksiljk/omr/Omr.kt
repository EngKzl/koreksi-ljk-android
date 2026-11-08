package net.pakeng.koreksiljk.omr

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/** Persegi dalam satuan mm pada lembar A4. */
data class Rect(val x: Double, val y: Double, val w: Double, val h: Double)

/** Gambar abu-abu 0..255. */
class Gray(val w: Int, val h: Int, val g: IntArray) {
    companion object {
        fun fromArgb(w: Int, h: Int, px: IntArray): Gray {
            val g = IntArray(w * h)
            for (i in g.indices) {
                val c = px[i]
                g[i] = (((c shr 16) and 255) * 77 + ((c shr 8) and 255) * 150 + (c and 255) * 29) shr 8
            }
            return Gray(w, h, g)
        }
    }
}

/** Tata letak LJK (mm, A4). Harus sama persis dengan versi web. */
object Layout {
    const val W = 210.0
    const val H = 297.0
    const val MS = 10.0
    val markers = arrayOf(doubleArrayOf(13.0, 13.0), doubleArrayOf(197.0, 13.0), doubleArrayOf(197.0, 284.0), doubleArrayOf(13.0, 284.0))
    val orient = Rect(22.0, 10.0, 18.0, 5.0)
    val orientOpp = Rect(170.0, 282.0, 18.0, 5.0)
    const val BOX = 5.5
    const val INSET = 1.0
    const val PX = 9.0
    const val PY = 8.0
    const val LM = 3.5
    val ansX0 = doubleArrayOf(22.0, 84.0, 146.0)
    const val ANS_Y0 = 100.0
    const val ROWS = 20
    val idY = doubleArrayOf(58.0, 70.0)
    const val ID_X = 43.0
    const val PAKET_X = 148.0
    const val PAKET_Y = 58.0

    fun ansBox(q: Int, o: Int): Rect {
        val c = q / ROWS
        val r = q % ROWS
        return Rect(ansX0[c] + 11 + PX * o, ANS_Y0 + r * PY, BOX, BOX)
    }

    fun idBox(row: Int, d: Int) = Rect(ID_X + PX * d, idY[row], BOX, BOX)
    fun paketBox(i: Int) = Rect(PAKET_X + PX * i, PAKET_Y, BOX, BOX)
    fun blockMarkers(c: Int): Array<DoubleArray> {
        val x0 = ansX0[c]
        val yT = ANS_Y0 - 5
        val yB = ANS_Y0 + (ROWS - 1) * PY + BOX + 4.5
        return arrayOf(doubleArrayOf(x0 + 1.75, yT), doubleArrayOf(x0 + 50.75, yT), doubleArrayOf(x0 + 50.75, yB), doubleArrayOf(x0 + 1.75, yB))
    }

    fun idMarkers() = arrayOf(doubleArrayOf(24.0, 50.0), doubleArrayOf(194.0, 50.0), doubleArrayOf(194.0, 80.0), doubleArrayOf(24.0, 80.0))
}

data class Thresholds(val tEmpty: Double = 0.07, val tFull: Double = 0.62, val tStrike: Double = 0.78, val tDiag: Double = 0.42)

data class Cell(val st: String, val fill: Double, val diag: Double, val rowMax: Double)

/** marks: "kosong" | "x" | "tanda" | "coret" ; status: ok | ragu | ganda | kosong */
data class Decision(val choice: String?, val status: String, val note: String, val marks: List<String>)

data class SheetResult(
    val ok: Boolean,
    val error: String? = null,
    val H: DoubleArray? = null,
    val absen: Int? = null,
    val absenNote: String = "",
    val paket: String? = null,
    val paketNote: String = "",
    val answers: List<Decision> = emptyList(),
)

/** Konteks ambang adaptif berbasis citra integral. */
class Ctx(val G: Gray, rDiv: Int) {
    private val w1 = G.w + 1
    private val I = IntArray(w1 * (G.h + 1))
    val r: Int = max(8, (max(G.w, G.h).toDouble() / rDiv).roundToInt())
    private val k = 0.78
    private val c = 22.0

    init {
        for (y in 0 until G.h) {
            var s = 0
            val row = y * G.w
            for (x in 0 until G.w) {
                s += G.g[row + x]
                I[(y + 1) * w1 + x + 1] = I[y * w1 + x + 1] + s
            }
        }
    }

    private fun meanAt(x: Int, y: Int): Double {
        val x0 = max(0, x - r)
        val y0 = max(0, y - r)
        val x1 = min(G.w, x + r + 1)
        val y1 = min(G.h, y + r + 1)
        val s = I[y1 * w1 + x1].toLong() - I[y0 * w1 + x1] - I[y1 * w1 + x0] + I[y0 * w1 + x0]
        return s.toDouble() / ((x1 - x0) * (y1 - y0))
    }

    fun dark(fx: Double, fy: Double): Int {
        if (fx.isNaN() || fy.isNaN()) return 0
        val x = fx.roundToInt()
        val y = fy.roundToInt()
        if (x < 0 || y < 0 || x >= G.w || y >= G.h) return 0
        val v = G.g[y * G.w + x]
        val m = meanAt(x, y)
        return if (v < m * k && v < m - c) 1 else 0
    }
}

object Omr {
    val LET = listOf("A", "B", "C", "D", "E")

    // ---------- Homografi ----------
    fun homography(src: Array<DoubleArray>, dst: Array<DoubleArray>): DoubleArray? {
        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)
        for (i in 0 until 4) {
            val x = src[i][0]; val y = src[i][1]; val u = dst[i][0]; val v = dst[i][1]
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y); b[2 * i] = u
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y); b[2 * i + 1] = v
        }
        for (c in 0 until 8) {
            var p = c
            for (r in c + 1 until 8) if (abs(a[r][c]) > abs(a[p][c])) p = r
            if (abs(a[p][c]) < 1e-12) return null
            val t = a[c]; a[c] = a[p]; a[p] = t
            val tb = b[c]; b[c] = b[p]; b[p] = tb
            for (r in 0 until 8) if (r != c) {
                val f = a[r][c] / a[c][c]
                for (k in c until 8) a[r][k] -= f * a[c][k]
                b[r] -= f * b[c]
            }
        }
        return DoubleArray(8) { b[it] / a[it][it] }
    }

    fun apply(H: DoubleArray, x: Double, y: Double): DoubleArray {
        val d = H[6] * x + H[7] * y + 1
        return doubleArrayOf((H[0] * x + H[1] * y + H[2]) / d, (H[3] * x + H[4] * y + H[5]) / d)
    }

    private fun downscale(G: Gray, maxDim: Int): Pair<Gray, Int> {
        val f = max(1, ceil(max(G.w, G.h).toDouble() / maxDim).toInt())
        val w = G.w / f
        val h = G.h / f
        val g = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (j in 0 until f) for (i in 0 until f) s += G.g[(y * f + j) * G.w + x * f + i]
            g[y * w + x] = s / (f * f)
        }
        return Pair(Gray(w, h, g), f)
    }

    private class Sq(val x: Double, val y: Double, val a: Int)

    private fun findSquares(D: Gray, ctx: Ctx): List<Sq> {
        val w = D.w; val h = D.h; val n = w * h
        val bin = ByteArray(n)
        for (y in 0 until h) for (x in 0 until w) bin[y * w + x] = ctx.dark(x.toDouble(), y.toDouble()).toByte()
        val lab = IntArray(n)
        val stack = IntArray(n)
        val out = ArrayList<Sq>()
        var id = 0
        val maxA = 0.02 * n
        for (s in 0 until n) {
            if (bin[s].toInt() == 0 || lab[s] != 0) continue
            id++
            var sp = 0
            stack[sp++] = s; lab[s] = id
            var cnt = 0; var sx = 0.0; var sy = 0.0
            var x0 = w; var x1 = 0; var y0 = h; var y1 = 0
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % w; val py = p / w
                cnt++; sx += px; sy += py
                if (px < x0) x0 = px; if (px > x1) x1 = px; if (py < y0) y0 = py; if (py > y1) y1 = py
                if (px > 0) { val q = p - 1; if (bin[q].toInt() == 1 && lab[q] == 0) { lab[q] = id; stack[sp++] = q } }
                if (px < w - 1) { val q = p + 1; if (bin[q].toInt() == 1 && lab[q] == 0) { lab[q] = id; stack[sp++] = q } }
                if (py > 0) { val q = p - w; if (bin[q].toInt() == 1 && lab[q] == 0) { lab[q] = id; stack[sp++] = q } }
                if (py < h - 1) { val q = p + w; if (bin[q].toInt() == 1 && lab[q] == 0) { lab[q] = id; stack[sp++] = q } }
            }
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            if (cnt < 20 || cnt > maxA || bw < 5 || bh < 5) continue
            val asp = bw.toDouble() / bh
            val fill = cnt.toDouble() / (bw * bh)
            if (asp < 0.5 || asp > 2 || fill < 0.55) continue
            out.add(Sq(sx / cnt, sy / cnt, cnt))
        }
        return out
    }

    private fun quadArea(p: List<Sq>): Double {
        var s = 0.0
        for (i in 0 until 4) { val a = p[i]; val b = p[(i + 1) % 4]; s += a.x * b.y - b.x * a.y }
        return abs(s) / 2
    }

    private fun isConvex(p: List<Sq>): Boolean {
        var sg = 0.0
        for (i in 0 until 4) {
            val a = p[i]; val b = p[(i + 1) % 4]; val c = p[(i + 2) % 4]
            val z = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
            if (z == 0.0) return false
            if (sg == 0.0) sg = sign(z) else if (sign(z) != sg) return false
        }
        return true
    }

    private fun regionDark(ctx: Ctx, H: DoubleArray, R: Rect, n: Int = 10): Double {
        var c = 0
        for (j in 0 until n) for (i in 0 until n) {
            val p = apply(H, R.x + 0.6 + (i + 0.5) * (R.w - 1.2) / n, R.y + 0.6 + (j + 0.5) * (R.h - 1.2) / n)
            c += ctx.dark(p[0], p[1])
        }
        return c.toDouble() / (n * n)
    }

    private fun locateSheet(full: Ctx): Pair<DoubleArray?, String?> {
        val (D, f) = downscale(full.G, 900)
        val dctx = Ctx(D, 16)
        val cands = findSquares(D, dctx).sortedByDescending { it.a }.take(16)
        if (cands.size < 4) return Pair(null, "Penanda sudut LJK tidak ditemukan. Pastikan keempat kotak hitam di sudut terlihat jelas.")
        class Combo(val q: List<Sq>, val qa: Double)
        val combos = ArrayList<Combo>()
        val n = cands.size
        for (a in 0 until n) for (b in a + 1 until n) for (c in b + 1 until n) for (d in c + 1 until n) {
            val q0 = listOf(cands[a], cands[b], cands[c], cands[d])
            val mx = q0.maxOf { it.a }; val mn = q0.minOf { it.a }
            if (mx.toDouble() / mn > 3) continue
            val cx = q0.sumOf { it.x } / 4; val cy = q0.sumOf { it.y } / 4
            val q = q0.sortedBy { atan2(it.y - cy, it.x - cx) }
            if (!isConvex(q)) continue
            val qa = quadArea(q)
            if (qa < 0.08 * D.w * D.h) continue
            val meanA = q.sumOf { it.a }.toDouble() / 4
            if (meanA > qa * 0.01) continue
            combos.add(Combo(q, qa))
        }
        combos.sortByDescending { it.qa }
        for (cb in combos.take(12)) {
            val pts = cb.q.map { doubleArrayOf(it.x * f + f / 2.0, it.y * f + f / 2.0) }
            var bestH: DoubleArray? = null; var bestSc = -9.0; var bestOd = 0.0; var bestOo = 1.0
            for (rot in 0 until 4) {
                val dst = Array(4) { pts[(it + rot) % 4] }
                val H = homography(Layout.markers, dst) ?: continue
                val od = regionDark(full, H, Layout.orient)
                val oo = regionDark(full, H, Layout.orientOpp)
                if (od - oo > bestSc) { bestSc = od - oo; bestH = H; bestOd = od; bestOo = oo }
            }
            if (bestH != null && bestOd > 0.55 && bestOo < 0.3) return Pair(bestH, null)
        }
        return Pair(null, "LJK tidak dikenali (orientasi/penanda). Foto ulang dengan seluruh lembar terlihat dan tidak terpotong.")
    }

    // ---------- Perbaikan lokal per blok ----------
    private fun pxPerMm(H: DoubleArray, x: Double, y: Double): Double {
        val a = apply(H, x, y); val b = apply(H, x + 10, y)
        return hypot(b[0] - a[0], b[1] - a[1]) / 10
    }

    private fun refinePoint(ctx: Ctx, H: DoubleArray, cx: Double, cy: Double): Pair<DoubleArray, Boolean> {
        val s = pxPerMm(H, cx, cy)
        val p0 = apply(H, cx, cy)
        var u = p0[0]; var v = p0[1]
        val R = 3.2 * s
        val expA = (Layout.LM * s) * (Layout.LM * s)
        repeat(3) {
            var c = 0; var sx = 0.0; var sy = 0.0
            val x0 = (u - R).toInt() - 1; val x1 = (u + R).toInt() + 1
            val y0 = (v - R).toInt() - 1; val y1 = (v + R).toInt() + 1
            for (y in y0..y1) for (x in x0..x1) {
                val dx = x - u; val dy = y - v
                if (dx * dx + dy * dy > R * R) continue
                if (ctx.dark(x.toDouble(), y.toDouble()) == 1) { c++; sx += x; sy += y }
            }
            if (c < expA * 0.35 || c > expA * 2.2) return Pair(p0, false)
            u = sx / c; v = sy / c
        }
        return Pair(doubleArrayOf(u, v), true)
    }

    private fun localH(ctx: Ctx, H: DoubleArray, marks: Array<DoubleArray>): DoubleArray {
        val r = marks.map { refinePoint(ctx, H, it[0], it[1]) }
        if (r.count { it.second } < 4) return H
        return homography(marks, Array(4) { r[it].first }) ?: H
    }

    // ---------- Analisis kotak ----------
    fun analyzeCell(ctx: Ctx, H: DoubleArray, R: Rect, th: Thresholds): Cell {
        val n = 18
        val ins = Layout.INSET
        val s = R.w - 2 * ins
        val m = IntArray(n * n)
        var cnt = 0
        for (j in 0 until n) for (i in 0 until n) {
            val p = apply(H, R.x + ins + (i + 0.5) * s / n, R.y + ins + (j + 0.5) * s / n)
            val d = ctx.dark(p[0], p[1]); m[j * n + i] = d; cnt += d
        }
        val fill = cnt.toDouble() / (n * n)
        var rowMax = 0.0
        for (j in 2 until n - 2) {
            var r = 0
            for (i in 0 until n) r += m[j * n + i]
            rowMax = max(rowMax, r.toDouble() / n)
        }
        val b = 4
        var d1 = 0; var d2 = 0
        for (i in 0 until n) {
            var h1 = 0; var h2 = 0
            for (k in -b..b) {
                val a = i + k; val c = n - 1 - i + k
                if (a in 0 until n && m[i * n + a] == 1) h1 = 1
                if (c in 0 until n && m[i * n + c] == 1) h2 = 1
            }
            d1 += h1; d2 += h2
        }
        val diag = min(d1, d2).toDouble() / n
        val st = when {
            fill < th.tEmpty -> "kosong"
            fill >= th.tFull -> "coret"
            rowMax >= th.tStrike && fill > 0.18 -> "coret"
            diag >= th.tDiag -> "x"
            else -> "tanda"
        }
        return Cell(st, fill, diag, rowMax)
    }

    fun decide(cells: List<Cell>, labels: List<String>): Decision {
        val xs = ArrayList<Int>(); val tn = ArrayList<Int>(); val co = ArrayList<Int>()
        cells.forEachIndexed { i, c -> when (c.st) { "x" -> xs.add(i); "tanda" -> tn.add(i); "coret" -> co.add(i) } }
        var choice: Int? = null
        val status: String
        var note = ""
        when {
            xs.size == 1 -> {
                choice = xs[0]; status = if (tn.isNotEmpty()) "ragu" else "ok"
                if (co.isNotEmpty()) note = "ada coretan, dipakai silang"
                if (tn.isNotEmpty()) note = "ada tanda lain"
            }
            xs.size > 1 -> { status = "ganda"; note = "silang " + xs.joinToString(",") { labels[it] } }
            tn.size == 1 -> { choice = tn[0]; status = "ragu"; note = "tanda bukan silang tegas" + if (co.isNotEmpty()) ", ada coretan" else "" }
            tn.size > 1 -> { status = "ganda"; note = "tanda " + tn.joinToString(",") { labels[it] } }
            else -> { status = "kosong"; if (co.isNotEmpty()) note = "semua dicoret/dibatalkan" }
        }
        return Decision(choice?.let { labels[it] }, status, note, cells.map { it.st })
    }

    // ---------- Baca LJK lengkap ----------
    fun readSheet(G: Gray, n: Int, nPaket: Int, th: Thresholds = Thresholds()): SheetResult {
        val ctx = Ctx(G, 11)
        val (H, err) = locateSheet(ctx)
        if (H == null) return SheetResult(false, err)
        val idH = localH(ctx, H, Layout.idMarkers())
        fun digitRow(r: Int) = decide((0 until 10).map { analyzeCell(ctx, idH, Layout.idBox(r, it), th) }, (0 until 10).map { it.toString() })
        val t = digitRow(0); val u = digitRow(1)
        var absen: Int? = null
        if (u.choice != null && (t.choice != null || t.status == "kosong")) absen = (t.choice?.toInt() ?: 0) * 10 + u.choice.toInt()
        val absenNote = when {
            t.status == "ganda" || u.status == "ganda" -> "No. absen ganda"
            absen == null -> "No. absen tidak terbaca"
            else -> ""
        }
        var paket: String? = "A"
        var paketNote = ""
        if (nPaket > 1) {
            val p = decide((0 until nPaket).map { analyzeCell(ctx, idH, Layout.paketBox(it), th) }, LET.take(nPaket))
            if (p.choice != null) paket = p.choice else { paket = null; paketNote = if (p.status == "ganda") "Paket ganda" else "Paket tidak terbaca" }
        }
        val blocks = (n + Layout.ROWS - 1) / Layout.ROWS
        val hs = (0 until blocks).map { localH(ctx, H, Layout.blockMarkers(it)) }
        val answers = (0 until n).map { q ->
            val hb = hs[q / Layout.ROWS]
            decide((0 until 5).map { analyzeCell(ctx, hb, Layout.ansBox(q, it), th) }, LET)
        }
        return SheetResult(true, null, H, absen, absenNote, paket, paketNote, answers)
    }

    /** Render LJK lurus (untuk tinjauan): menghasilkan piksel ARGB berukuran (210*scale)x(297*scale). */
    fun warp(srcW: Int, srcH: Int, src: IntArray, H: DoubleArray, scale: Int): Triple<Int, Int, IntArray> {
        val w = (Layout.W * scale).toInt(); val h = (Layout.H * scale).toInt()
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val p = apply(H, (x + 0.5) / scale, (y + 0.5) / scale)
            val xi = p[0].toInt(); val yi = p[1].toInt()
            out[y * w + x] = if (p[0] >= 0 && p[1] >= 0 && xi < srcW && yi < srcH) src[yi * srcW + xi] or (0xFF shl 24) else 0xFFC8C8C8.toInt()
        }
        return Triple(w, h, out)
    }
}
