package net.pakeng.koreksiljk.omr

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

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

/**
 * Tata letak LJK SMK PGRI 2 Kota Jambi (mm, A4): dua tabel 25 baris x 5 kolom (A–E),
 * soal 1–25 di kiri dan 26–50 di kanan. Diukur dari lembar kosong hasil scan.
 */
object Layout {
    const val W = 210.0
    const val H = 297.0
    const val ROWS = 25
    const val MAXQ = 50

    /** Garis tegak tiap tabel (6 garis = 5 kolom). */
    val xLines = arrayOf(
        doubleArrayOf(33.30, 46.02, 58.76, 70.48, 81.16, 93.09),
        doubleArrayOf(123.97, 135.62, 147.56, 159.21, 171.14, 182.98),
    )
    val yTop = doubleArrayOf(91.0, 90.8)
    val yBot = doubleArrayOf(251.3, 251.3)

    /** Area tulisan tangan Nama/Kelas/Mapel. */
    val nameBox = Rect(70.0, 59.0, 56.0, 22.0)

    fun ansBox(q: Int, o: Int): Rect {
        val t = q / ROWS
        val r = q % ROWS
        val rh = (yBot[t] - yTop[t]) / ROWS
        return Rect(xLines[t][o], yTop[t] + r * rh, xLines[t][o + 1] - xLines[t][o], rh)
    }
}

/**
 * tEmpty/tMark = tambahan tinta (di luar huruf cetak) agar kotak dianggap bertanda lemah/tegas,
 * tFull = kotak dihitamkan, tStrike = panjang garis mendatar (pembatalan) dalam satuan tebal goresan.
 */
data class Thresholds(val tEmpty: Double = 0.03, val tMark: Double = 0.055, val tFull: Double = 0.6, val tStrike: Double = 5.2)

data class Cell(val st: String, val fill: Double, val extra: Double, val strike: Double)

/** marks: "kosong" | "x" | "tanda" | "coret" ; status: ok | ragu | ganda | kosong */
data class Decision(val choice: String?, val status: String, val note: String, val marks: List<String>, val cells: List<Cell> = emptyList())

data class SheetResult(
    val ok: Boolean,
    val error: String? = null,
    /** Homografi mm-halaman → piksel foto. */
    val H: DoubleArray? = null,
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

    /** Perkecil dengan mengambil nilai tergelap tiap blok, agar garis tabel yang tipis tidak putus. */
    private fun minPool(G: Gray, maxDim: Int): Pair<Gray, Int> {
        val f = max(1, ceil(max(G.w, G.h).toDouble() / maxDim).toInt())
        val w = G.w / f
        val h = G.h / f
        val g = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var m = 255
            for (j in 0 until f) for (i in 0 until f) { val v = G.g[(y * f + j) * G.w + x * f + i]; if (v < m) m = v }
            g[y * w + x] = m
        }
        return Pair(Gray(w, h, g), f)
    }

    // ---------- Garis & segi empat tabel ----------
    /** Garis a·x + b·y = c dengan (a,b) ternormalisasi. */
    private class Line(val a: Double, val b: Double, val c: Double) {
        fun dist(p: DoubleArray) = abs(a * p[0] + b * p[1] - c)
    }

    private fun lineThrough(p: DoubleArray, q: DoubleArray): Line? {
        val dx = q[0] - p[0]; val dy = q[1] - p[1]
        val n = hypot(dx, dy)
        if (n < 1e-9) return null
        val a = -dy / n; val b = dx / n
        return Line(a, b, a * p[0] + b * p[1])
    }

    /** Garis dominan (RANSAC deterministik + kuadrat terkecil total pada inlier). */
    private fun fitLine(pts: List<DoubleArray>, tol: Double): Line? {
        val n = pts.size
        if (n < 8) return null
        var best: Line? = null
        var bestCnt = 0
        for (k in listOf(n / 2, n / 3, n / 4, n / 6)) {
            if (k < 2) continue
            val step = max(1, n / 40)
            var i = 0
            while (i + k < n) {
                val l = lineThrough(pts[i], pts[i + k])
                if (l != null) {
                    val c = pts.count { l.dist(it) <= tol }
                    if (c > bestCnt) { bestCnt = c; best = l }
                }
                i += step
            }
        }
        val l0 = best ?: return null
        if (bestCnt < n * 0.3) return null
        val inl = pts.filter { l0.dist(it) <= tol }
        val mx = inl.sumOf { it[0] } / inl.size; val my = inl.sumOf { it[1] } / inl.size
        var sxx = 0.0; var sxy = 0.0; var syy = 0.0
        for (p in inl) { val dx = p[0] - mx; val dy = p[1] - my; sxx += dx * dx; sxy += dx * dy; syy += dy * dy }
        // arah utama
        val th = 0.5 * Math.atan2(2 * sxy, sxx - syy)
        val dx = Math.cos(th); val dy = Math.sin(th)
        val a = -dy; val b = dx
        return Line(a, b, a * mx + b * my)
    }

    private fun intersect(l: Line, m: Line): DoubleArray? {
        val d = l.a * m.b - m.a * l.b
        if (abs(d) < 1e-9) return null
        return doubleArrayOf((l.c * m.b - m.c * l.b) / d, (l.a * m.c - m.a * l.c) / d)
    }

    /** Segi empat tabel: pusat, sumbu panjang, panjang sisi, dan 4 sudut. */
    private class Quad(val pts: Array<DoubleArray>) {
        val cx = pts.sumOf { it[0] } / 4
        val cy = pts.sumOf { it[1] } / 4
        private fun len(i: Int, j: Int) = hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1])
        private val s0 = (len(0, 1) + len(2, 3)) / 2
        private val s1 = (len(1, 2) + len(3, 0)) / 2
        val long = max(s0, s1)
        val short = min(s0, s1)
        /** Arah sisi panjang (satuan). */
        val u: DoubleArray = run {
            val (i, j) = if (s0 >= s1) Pair(0, 1) else Pair(1, 2)
            val n = len(i, j)
            doubleArrayOf((pts[j][0] - pts[i][0]) / n, (pts[j][1] - pts[i][1]) / n)
        }
    }

    private fun findTables(D: Gray, ctx: Ctx): List<Quad> {
        val w = D.w; val h = D.h; val n = w * h
        val bin = ByteArray(n)
        for (y in 0 until h) for (x in 0 until w) bin[y * w + x] = ctx.dark(x.toDouble(), y.toDouble()).toByte()
        val lab = IntArray(n)
        val stack = IntArray(n)
        val out = ArrayList<Quad>()
        var id = 0
        for (s in 0 until n) {
            if (bin[s].toInt() == 0 || lab[s] != 0) continue
            id++
            var sp = 0
            stack[sp++] = s; lab[s] = id
            var cnt = 0
            var x0 = w; var x1 = 0; var y0 = h; var y1 = 0
            while (sp > 0) {
                val p = stack[--sp]
                val px = p % w; val py = p / w
                cnt++
                if (px < x0) x0 = px; if (px > x1) x1 = px; if (py < y0) y0 = py; if (py > y1) y1 = py
                for (dy in -1..1) for (dx in -1..1) {
                    val qx = px + dx; val qy = py + dy
                    if (qx < 0 || qy < 0 || qx >= w || qy >= h) continue
                    val q = qy * w + qx
                    if (bin[q].toInt() == 1 && lab[q] == 0) { lab[q] = id; stack[sp++] = q }
                }
            }
            val bw = x1 - x0 + 1; val bh = y1 - y0 + 1
            if (bw.toLong() * bh < 0.015 * n || cnt > 0.5 * bw * bh || min(bw, bh) < 30) continue
            // titik terluar tiap baris/kolom
            val left = ArrayList<DoubleArray>(); val right = ArrayList<DoubleArray>()
            val top = ArrayList<DoubleArray>(); val bot = ArrayList<DoubleArray>()
            for (y in y0..y1) {
                var a = -1; var b = -1
                for (x in x0..x1) if (lab[y * w + x] == id) { if (a < 0) a = x; b = x }
                if (a >= 0) { left.add(doubleArrayOf(a.toDouble(), y.toDouble())); right.add(doubleArrayOf(b.toDouble(), y.toDouble())) }
            }
            for (x in x0..x1) {
                var a = -1; var b = -1
                for (y in y0..y1) if (lab[y * w + x] == id) { if (a < 0) a = y; b = y }
                if (a >= 0) { top.add(doubleArrayOf(x.toDouble(), a.toDouble())); bot.add(doubleArrayOf(x.toDouble(), b.toDouble())) }
            }
            val tol = 2.0
            val tall = bh >= bw
            // sisi panjang dulu; sisi pendek memakai titik yang bukan milik sisi panjang
            val (pa, pb, qa, qb) = if (tall) listOf(left, right, top, bot) else listOf(top, bot, left, right)
            val la = fitLine(pa, tol) ?: continue
            val lb = fitLine(pb, tol) ?: continue
            val rest = { pts: List<DoubleArray> -> pts.filter { la.dist(it) > 2 * tol && lb.dist(it) > 2 * tol } }
            val lc = fitLine(rest(qa), tol) ?: continue
            val ld = fitLine(rest(qb), tol) ?: continue
            val c0 = intersect(la, lc) ?: continue
            val c1 = intersect(lc, lb) ?: continue
            val c2 = intersect(lb, ld) ?: continue
            val c3 = intersect(ld, la) ?: continue
            val q = Quad(arrayOf(c0, c1, c2, c3))
            if (q.short < 20 || q.long / q.short < 1.8 || q.long / q.short > 4.2) continue
            out.add(q)
        }
        return out
    }

    private fun regionDark(ctx: Ctx, H: DoubleArray, R: Rect, nx: Int, ny: Int): Double {
        var c = 0
        for (j in 0 until ny) for (i in 0 until nx) {
            val p = apply(H, R.x + (i + 0.5) * R.w / nx, R.y + (j + 0.5) * R.h / ny)
            c += ctx.dark(p[0], p[1])
        }
        return c.toDouble() / (nx * ny)
    }

    /** Sudut satu tabel berurutan TL,TR,BR,BL menurut sumbu halaman (v = kanan, u = bawah). */
    private fun label(q: Quad, v: DoubleArray, u: DoubleArray): Array<DoubleArray>? {
        val out = arrayOfNulls<DoubleArray>(4)
        for (p in q.pts) {
            val dv = (p[0] - q.cx) * v[0] + (p[1] - q.cy) * v[1]
            val du = (p[0] - q.cx) * u[0] + (p[1] - q.cy) * u[1]
            val i = if (du < 0) (if (dv < 0) 0 else 1) else (if (dv < 0) 3 else 2)
            if (out[i] != null) return null
            out[i] = p
        }
        return Array(4) { out[it]!! }
    }

    private class Located(val page: DoubleArray, val tables: List<Array<DoubleArray>>)

    private fun canonCorners(t: Int) = arrayOf(
        doubleArrayOf(Layout.xLines[t][0], Layout.yTop[t]), doubleArrayOf(Layout.xLines[t][5], Layout.yTop[t]),
        doubleArrayOf(Layout.xLines[t][5], Layout.yBot[t]), doubleArrayOf(Layout.xLines[t][0], Layout.yBot[t]),
    )

    private fun locate(full: Ctx): Located? {
        val (D, f) = minPool(full.G, 1000)
        val quads = findTables(D, Ctx(D, 16))
        // pasangan tabel kiri-kanan terbesar
        var best: Pair<Quad, Quad>? = null
        var bestA = 0.0
        for (i in quads.indices) for (j in i + 1 until quads.size) {
            val a = quads[i]; val b = quads[j]
            val r = a.long / b.long
            if (r < 0.75 || r > 1.33) continue
            if (abs(a.u[0] * b.u[0] + a.u[1] * b.u[1]) < 0.95) continue
            val dx = b.cx - a.cx; val dy = b.cy - a.cy
            val along = abs(dx * a.u[0] + dy * a.u[1])
            val across = abs(-dx * a.u[1] + dy * a.u[0])
            val sh = (a.short + b.short) / 2
            if (across < 1.1 * sh || across > 2.3 * sh || along > 0.25 * a.long) continue
            val area = a.long * a.short + b.long * b.short
            if (area > bestA) { bestA = area; best = Pair(a, b) }
        }
        val (a, b) = best ?: return null
        // sumbu halaman: v dari tabel a ke b (tegak lurus sisi panjang), u = v diputar 90°
        var vx = -a.u[1]; var vy = a.u[0]
        if (vx * (b.cx - a.cx) + vy * (b.cy - a.cy) < 0) { vx = -vx; vy = -vy }
        val v = doubleArrayOf(vx, vy); val u = doubleArrayOf(-vy, vx)
        val la = label(a, v, u) ?: return null
        val lb = label(b, v, u) ?: return null
        val up = { c: Array<DoubleArray> -> Array(4) { doubleArrayOf(c[it][0] * f + f / 2.0, c[it][1] * f + f / 2.0) } }
        val flip = { c: Array<DoubleArray> -> arrayOf(c[2], c[3], c[0], c[1]) }
        // dua kemungkinan arah (tegak / terbalik 180°): pilih yang kop & nomor soalnya berisi tinta
        val hyps = listOf(Pair(up(la), up(lb)), Pair(flip(up(lb)), flip(up(la))))
        var bestH: Located? = null
        var bestS = -1.0
        val c0 = canonCorners(0); val c1 = canonCorners(1)
        for ((l, r) in hyps) {
            val hp = homography(arrayOf(c0[0], c1[1], c1[2], c0[3]), arrayOf(l[0], r[1], r[2], l[3])) ?: continue
            val th = Layout.yBot[0] - Layout.yTop[0]
            val s = regionDark(full, hp, Rect(30.0, 53.0, 150.0, 31.0), 80, 30) +
                regionDark(full, hp, Rect(Layout.xLines[0][0] - 9, Layout.yTop[0], 7.0, th), 12, 150) +
                regionDark(full, hp, Rect(Layout.xLines[1][0] - 9, Layout.yTop[1], 7.0, th), 12, 150)
            if (s > bestS) { bestS = s; bestH = Located(hp, listOf(l, r)) }
        }
        return bestH
    }

    // ---------- Garis kisi di dalam tabel ----------
    /**
     * Cari posisi garis kisi sepanjang satu sumbu tabel (koordinat 0..1).
     * [vertical] = garis tegak (posisi sepanjang u). Mengembalikan null bila terlalu sedikit garis ditemukan.
     */
    private fun gridLines(ctx: Ctx, H: DoubleArray, expected: DoubleArray, vertical: Boolean, win: Double): DoubleArray? {
        val lo = -win - 0.01; val hi = 1 + win + 0.01
        val bins = if (vertical) 700 else 1400
        val m = if (vertical) 150 else 60
        val prof = DoubleArray(bins)
        for (k in 0 until bins) {
            val a = lo + (k + 0.5) * (hi - lo) / bins
            var c = 0
            for (j in 0 until m) {
                val t = 0.01 + (j + 0.5) * 0.98 / m
                val p = if (vertical) apply(H, a, t) else apply(H, t, a)
                c += ctx.dark(p[0], p[1])
            }
            prof[k] = c.toDouble() / m
        }
        val sm = DoubleArray(bins) { k -> prof[k] + (if (k > 0) prof[k - 1] else 0.0) + (if (k < bins - 1) prof[k + 1] else 0.0) }
        val off = arrayOfNulls<Double>(expected.size)
        var found = 0
        for (i in expected.indices) {
            val k0 = (((expected[i] - win) - lo) / (hi - lo) * bins).toInt().coerceIn(0, bins - 1)
            val k1 = (((expected[i] + win) - lo) / (hi - lo) * bins).toInt().coerceIn(0, bins - 1)
            var bk = k0
            for (k in k0..k1) if (sm[k] > sm[bk]) bk = k
            if (sm[bk] >= 0.5) { off[i] = lo + (bk + 0.5) * (hi - lo) / bins - expected[i]; found++ }
        }
        if (found < expected.size * 0.6) return null
        // garis yang tak ditemukan memakai pergeseran garis terdekat yang ditemukan
        return DoubleArray(expected.size) { i ->
            var d = 0
            var o: Double? = off[i]
            while (o == null) { d++; o = off.getOrNull(i - d) ?: off.getOrNull(i + d) }
            expected[i] + o
        }
    }

    // ---------- Analisis kotak ----------
    private const val NX = 60
    private const val NY = 30

    /**
     * Ukuran garis pembatalan: panjang garis mendatar terpanjang (boleh sedikit miring) dibagi tebal goresan.
     * Piksel huruf cetak ([letter]) tidak dihitung dan tidak memutus garis.
     */
    private fun strikeLen(m: IntArray, letter: BooleanArray): Double {
        // tebal goresan: median dari min(panjang lari mendatar, tegak) tiap piksel tinta
        val th = ArrayList<Int>()
        for (y in 0 until NY) for (x in 0 until NX) {
            val i = y * NX + x
            if (m[i] == 0 || letter[i]) continue
            var a = x; while (a > 0 && m[y * NX + a - 1] == 1) a--
            var b = x; while (b < NX - 1 && m[y * NX + b + 1] == 1) b++
            var c = y; while (c > 0 && m[(c - 1) * NX + x] == 1) c--
            var d = y; while (d < NY - 1 && m[(d + 1) * NX + x] == 1) d++
            th.add(min(b - a + 1, d - c + 1))
        }
        val ink = th.size
        if (ink < 8) return 0.0
        th.sort()
        val t = th[ink / 2].toDouble()
        var bestV = 0.0
        for (s in -6..6) {
            val slope = s * 0.05
            for (y0 in 0 until NY) {
                var best = 0; var cur = 0; var gap = 0
                for (x in 0 until NX) {
                    val y = (y0 + slope * (x - NX / 2)).roundToInt()
                    if (y !in 0 until NY) { cur = 0; gap = 0; continue }
                    val i = y * NX + x
                    if (letter[i]) continue
                    if (m[i] == 1) { cur += 1 + gap; gap = 0; if (cur > best) best = cur }
                    else if (cur > 0 && gap < 1) gap++
                    else { cur = 0; gap = 0 }
                }
                val v = best / t
                if (v <= bestV) continue
                // garis coretan memotong tanda: harus ada tinta di atas DAN di bawah garis
                // (busur atas/bawah lingkaran hanya punya tinta di satu sisi)
                var above = 0; var below = 0
                for (x in 0 until NX) {
                    val yl = (y0 + slope * (x - NX / 2)).roundToInt()
                    for (y in 0 until NY) {
                        val i = y * NX + x
                        if (m[i] == 0 || letter[i]) continue
                        if (y < yl - 1) above++ else if (y > yl + 2) below++
                    }
                }
                if (min(above, below) >= 0.15 * ink) bestV = v
            }
        }
        return bestV
    }

    private class Raw(val m: IntArray, val fill: Double)

    private fun rawCell(ctx: Ctx, H: DoubleArray, u0: Double, u1: Double, v0: Double, v1: Double): Raw {
        val du = (u1 - u0) * 0.08; val dv = (v1 - v0) * 0.15
        val m = IntArray(NX * NY)
        var cnt = 0
        for (j in 0 until NY) for (i in 0 until NX) {
            val p = apply(H, u0 + du + (i + 0.5) * (u1 - u0 - 2 * du) / NX, v0 + dv + (j + 0.5) * (v1 - v0 - 2 * dv) / NY)
            val d = ctx.dark(p[0], p[1]); m[j * NX + i] = d; cnt += d
        }
        return Raw(m, cnt.toDouble() / (NX * NY))
    }

    /** Huruf cetak satu kolom = piksel yang gelap di banyak baris, dilebarkan 1 piksel. */
    private fun letterMask(col: List<Raw>): BooleanArray {
        val core = BooleanArray(NX * NY) { i -> col.count { it.m[i] == 1 } >= col.size * 0.4 }
        return BooleanArray(NX * NY) { i ->
            val x = i % NX; val y = i / NX
            var d = false
            for (dy in -1..1) for (dx in -1..1) { val xx = x + dx; val yy = y + dy; if (xx in 0 until NX && yy in 0 until NY && core[yy * NX + xx]) d = true }
            d
        }
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
                if (co.isNotEmpty()) note = "ada coretan, dipakai tanda yang tidak dicoret"
                if (tn.isNotEmpty()) note = "ada tanda lain"
            }
            xs.size > 1 -> { status = "ganda"; note = "tanda di " + xs.joinToString(",") { labels[it] } }
            tn.size == 1 -> { choice = tn[0]; status = "ragu"; note = "tanda kurang tegas" + if (co.isNotEmpty()) ", ada coretan" else "" }
            tn.size > 1 -> { status = "ganda"; note = "tanda di " + tn.joinToString(",") { labels[it] } }
            else -> { status = "kosong"; if (co.isNotEmpty()) note = "semua dicoret/dibatalkan" }
        }
        return Decision(choice?.let { labels[it] }, status, note, cells.map { it.st }, cells)
    }

    // ---------- Baca LJK lengkap ----------
    fun readSheet(G: Gray, n: Int, th: Thresholds = Thresholds()): SheetResult {
        val ctx = Ctx(G, 11)
        val loc = locate(ctx) ?: return SheetResult(false, "Tabel jawaban tidak ditemukan. Pastikan kedua tabel (1–25 dan 26–50) terlihat utuh, lembar rata, dan cahaya cukup.")
        val unit = arrayOf(doubleArrayOf(0.0, 0.0), doubleArrayOf(1.0, 0.0), doubleArrayOf(1.0, 1.0), doubleArrayOf(0.0, 1.0))
        val answers = ArrayList<Decision>()
        for (t in 0 until 2) {
            if (t * Layout.ROWS >= n) break
            val ht = homography(unit, loc.tables[t]) ?: return SheetResult(false, "Tabel jawaban tidak terbaca.")
            val xl0 = Layout.xLines[t]
            val ex = DoubleArray(6) { (xl0[it] - xl0[0]) / (xl0[5] - xl0[0]) }
            val ey = DoubleArray(Layout.ROWS + 1) { it.toDouble() / Layout.ROWS }
            val xl = gridLines(ctx, ht, ex, true, 0.045)
            val yl = gridLines(ctx, ht, ey, false, 0.012)
            if (xl == null || yl == null) return SheetResult(false, "Garis tabel jawaban tidak terbaca jelas. Foto ulang lebih dekat, tegak lurus, dan tidak buram.")
            val raw = Array(Layout.ROWS) { r -> Array(5) { o -> rawCell(ctx, ht, xl[o], xl[o + 1], yl[r], yl[r + 1]) } }
            // tinta huruf cetak tiap kolom = kuartil bawah isi kotak kolom itu
            val base = DoubleArray(5) { o -> raw.map { it[o].fill }.sorted()[Layout.ROWS / 4] }
            val letter = Array(5) { o -> letterMask(raw.map { it[o] }) }
            for (r in 0 until Layout.ROWS) {
                if (t * Layout.ROWS + r >= n) break
                val cells = (0 until 5).map { o ->
                    val c = raw[r][o]
                    val extra = c.fill - base[o]
                    val strike = strikeLen(c.m, letter[o])
                    val st = when {
                        c.fill >= th.tFull || strike >= th.tStrike -> "coret"
                        extra >= th.tMark -> "x"
                        extra >= th.tEmpty -> "tanda"
                        else -> "kosong"
                    }
                    Cell(st, c.fill, extra, strike)
                }
                answers.add(decide(cells, LET))
            }
        }
        return SheetResult(true, null, loc.page, answers)
    }

    /** Render lembar lurus (untuk tinjauan): piksel ARGB berukuran (210*scale)x(297*scale). */
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
