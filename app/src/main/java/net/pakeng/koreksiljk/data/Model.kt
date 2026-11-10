package net.pakeng.koreksiljk.data

import kotlin.math.roundToInt

val LETTERS = listOf("A", "B", "C", "D", "E")

val TINGKAT = listOf("X", "XI", "XII")
val JURUSAN = listOf("TSM", "TKR", "DKV", "MP", "AK", "BR")

data class Exam(
    val id: String,
    val nama: String,
    val mapel: String,
    val n: Int,
    val paket: Int,
    val kktp: Double,
    val kunci: Map<String, String>,
    val created: Long,
) {
    fun key(p: String?): String = kunci[p ?: "A"] ?: ""
    val paketLetters: List<String> get() = LETTERS.take(paket)
}

/** Jawaban terbaca mesin. c = pilihan, s = ok|ragu|ganda|kosong, m = tanda per opsi (. x t c), n = catatan */
data class Ans(val c: String?, val s: String, val m: String, val n: String)

/** Hasil satu LJK. kelas mis. "XI AK 2"; nama diisi guru (di LJK hanya tulisan tangan). */
data class Res(
    val id: String,
    val examId: String,
    val kelas: String,
    val nama: String,
    val paket: String,
    val ans: List<Ans>,
    val ovr: Map<Int, String>,
    val img: String,
    val ts: Long,
)

data class Final(val ch: String?, val st: String, val ovr: Boolean)

fun finalAns(r: Res, q: Int): Final {
    if (r.ovr.containsKey(q)) {
        val o = r.ovr[q] ?: ""
        return Final(o.ifEmpty { null }, if (o.isEmpty()) "kosong" else "ok", true)
    }
    val a = r.ans.getOrNull(q) ?: Ans(null, "kosong", ".....", "")
    return Final(a.c, a.s, false)
}

data class Score(val benar: Int, val salah: Int, val kosong: Int, val ganda: Int, val ragu: Int, val tot: Int, val nilai: Double?)

fun score(e: Exam, r: Res): Score {
    val key = e.key(r.paket)
    var benar = 0; var salah = 0; var kosong = 0; var ganda = 0; var ragu = 0; var tot = 0
    for (q in 0 until e.n) {
        val f = finalAns(r, q)
        val k = key.getOrNull(q)?.toString()
        if (f.st == "ganda") ganda++
        if (f.st == "ragu") ragu++
        if (k == null || k == "-") continue
        tot++
        when {
            f.st == "ganda" -> salah++
            f.ch == null -> kosong++
            f.ch == k -> benar++
            else -> salah++
        }
    }
    val nilai = if (tot > 0) (benar.toDouble() / tot * 10000).roundToInt() / 100.0 else null
    return Score(benar, salah, kosong, ganda, ragu, tot, nilai)
}

fun fmt(v: Double?): String {
    if (v == null) return "–"
    val r = (v * 100).roundToInt() / 100.0
    return (if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()).replace('.', ',')
}

fun normKey(s: String): String = s.uppercase().replace('?', '-').filter { it in "ABCDE-" }

// ---------- Rekap ----------
data class RekapRow(val r: Res, val s: Score)

/** Baris rekap: yang sudah bernama urut abjad, yang belum bernama di akhir menurut urutan scan. */
fun rekapRows(e: Exam, rs: List<Res>): List<RekapRow> =
    rs.sortedWith(compareBy<Res>({ it.kelas }, { it.nama.isBlank() }, { it.nama.lowercase() }, { it.ts })).map { RekapRow(it, score(e, it)) }

// ---------- Analisis butir ----------
data class Item(
    val q: Int, val kc: String, val cnt: Map<String, Int>,
    val P: Double?, val D: Double?, val katP: String, val katD: String,
    val pengecoh: List<String>, val rek: String,
)

data class Analysis(val N: Int, val items: List<Item>, val kr20: Double?, val mean: Double, val k: Int)

fun analyze(e: Exam, all: List<Res>, p: String): Analysis {
    val rs = all.filter { it.paket == p }
    val key = e.key(p)
    val n = rs.size
    val tot = rs.map { score(e, it).benar }
    val order = rs.indices.sortedByDescending { tot[it] }
    val g = if (n >= 20) (n * 0.27).roundToInt() else n / 2
    val up = order.take(g).toSet()
    val lo = order.takeLast(g).toSet()
    val items = ArrayList<Item>()
    var spq = 0.0; var k = 0
    for (q in 0 until e.n) {
        val kc = key.getOrNull(q)?.toString() ?: "-"
        val cnt = linkedMapOf("A" to 0, "B" to 0, "C" to 0, "D" to 0, "E" to 0, "kosong" to 0, "ganda" to 0)
        var bu = 0; var bl = 0; var b = 0
        rs.forEachIndexed { i, r ->
            val f = finalAns(r, q)
            val slot = when { f.st == "ganda" -> "ganda"; f.ch == null -> "kosong"; else -> f.ch }
            cnt[slot] = (cnt[slot] ?: 0) + 1
            if (f.st != "ganda" && f.ch != null && f.ch == kc) { b++; if (i in up) bu++; if (i in lo) bl++ }
        }
        if (kc == "-") { items.add(Item(q, "-", cnt, null, null, "", "", emptyList(), "")); continue }
        val P = if (n > 0) b.toDouble() / n else null
        val D = if (g > 0) (bu - bl).toDouble() / g else null
        if (P != null) { spq += P * (1 - P); k++ }
        val pengecoh = LETTERS.filter { it != kc && n > 0 && (cnt[it] ?: 0).toDouble() / n < 0.05 }
        val katP = when { P == null -> ""; P < 0.3 -> "Sukar"; P <= 0.7 -> "Sedang"; else -> "Mudah" }
        val katD = when { D == null -> ""; D >= 0.4 -> "Sangat baik"; D >= 0.3 -> "Baik"; D >= 0.2 -> "Cukup"; D >= 0 -> "Jelek"; else -> "Negatif" }
        val rek = when {
            D == null || P == null -> ""
            D < 0 -> "Buang/periksa kunci"
            D < 0.2 || P < 0.15 || P > 0.95 -> "Revisi"
            else -> "Diterima"
        }
        items.add(Item(q, kc, cnt, P, D, katP, katD, pengecoh, rek))
    }
    val mean = if (n > 0) tot.sum().toDouble() / n else 0.0
    val vr = if (n > 0) tot.sumOf { (it - mean) * (it - mean) } / n else 0.0
    val kr20 = if (k > 1 && vr > 0) (k.toDouble() / (k - 1)) * (1 - spq / vr) else null
    return Analysis(n, items, kr20, mean, k)
}
