package net.pakeng.koreksiljk.data

import android.content.Context
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import net.pakeng.koreksiljk.omr.Thresholds
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Penyimpanan lokal (JSON + folder gambar). Format cadangan sama dengan versi web. */
class Store(private val ctx: Context) {
    val exams = mutableStateListOf<Exam>()
    val results = mutableStateListOf<Res>()
    var activeId by mutableStateOf<String?>(null)
    var sekolah by mutableStateOf("SMKS PGRI 2 Jambi")
    var th by mutableStateOf(Thresholds())

    private val dataFile get() = File(ctx.filesDir, "data.json")
    val imgDir: File get() = File(ctx.filesDir, "img").apply { mkdirs() }

    val active: Exam? get() = exams.firstOrNull { it.id == activeId }
    fun resultsOf(examId: String) = results.filter { it.examId == examId }

    fun load() {
        exams.clear(); results.clear()
        val f = dataFile
        if (f.exists()) {
            try {
                val o = JSONObject(f.readText())
                o.optJSONArray("exams")?.let { a -> for (i in 0 until a.length()) exams.add(examFrom(a.getJSONObject(i))) }
                o.optJSONArray("results")?.let { a -> for (i in 0 until a.length()) results.add(resFrom(a.getJSONObject(i))) }
                activeId = o.optString("active").ifEmpty { null }
                sekolah = o.optString("sekolah", sekolah)
                o.optJSONObject("th")?.let { t -> th = Thresholds(t.optDouble("tEmpty", 0.07), t.optDouble("tFull", 0.62), t.optDouble("tStrike", 0.78), t.optDouble("tDiag", 0.42)) }
            } catch (_: Exception) { }
        }
        exams.sortByDescending { it.created }
        if (active == null) activeId = exams.firstOrNull()?.id
    }

    @Synchronized
    fun save() {
        val o = JSONObject()
        o.put("exams", JSONArray().apply { exams.forEach { put(examTo(it)) } })
        o.put("results", JSONArray().apply { results.forEach { put(resTo(it, false)) } })
        o.put("active", activeId ?: "")
        o.put("sekolah", sekolah)
        o.put("th", JSONObject().put("tEmpty", th.tEmpty).put("tFull", th.tFull).put("tStrike", th.tStrike).put("tDiag", th.tDiag))
        val tmp = File(ctx.filesDir, "data.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(dataFile)
    }

    fun putExam(e: Exam) {
        val i = exams.indexOfFirst { it.id == e.id }
        if (i >= 0) exams[i] = e else exams.add(0, e)
        save()
    }

    fun deleteExam(id: String) {
        results.filter { it.examId == id }.forEach { File(imgDir, it.img).delete() }
        results.removeAll { it.examId == id }
        exams.removeAll { it.id == id }
        if (activeId == id) activeId = exams.firstOrNull()?.id
        save()
    }

    fun putResult(r: Res) {
        val i = results.indexOfFirst { it.id == r.id }
        if (i >= 0) results[i] = r else results.add(r)
        save()
    }

    fun deleteResult(id: String) {
        results.firstOrNull { it.id == id }?.let { File(imgDir, it.img).delete() }
        results.removeAll { it.id == id }
        save()
    }

    // ---------- Cadangan (kompatibel dengan web) ----------
    fun backupJson(): String {
        val o = JSONObject().put("app", "koreksi-ljk").put("ver", "android").put("at", System.currentTimeMillis())
        o.put("exams", JSONArray().apply { exams.forEach { put(examTo(it)) } })
        o.put("results", JSONArray().apply { results.forEach { put(resTo(it, true)) } })
        return o.toString()
    }

    fun restore(json: String): Pair<Int, Int> {
        val o = JSONObject(json)
        require(o.optString("app") == "koreksi-ljk") { "Bukan file cadangan aplikasi Koreksi LJK" }
        val ea = o.getJSONArray("exams"); val ra = o.getJSONArray("results")
        for (i in 0 until ea.length()) putExamNoSave(examFrom(ea.getJSONObject(i)))
        for (i in 0 until ra.length()) {
            val jo = ra.getJSONObject(i)
            var r = resFrom(jo)
            val img = jo.optString("img")
            if (img.startsWith("data:")) {
                val bytes = Base64.decode(img.substringAfter(","), Base64.DEFAULT)
                val name = r.id + ".jpg"
                File(imgDir, name).writeBytes(bytes)
                r = r.copy(img = name)
            }
            val k = results.indexOfFirst { it.id == r.id }
            if (k >= 0) results[k] = r else results.add(r)
        }
        exams.sortByDescending { it.created }
        if (active == null) activeId = exams.firstOrNull()?.id
        save()
        return Pair(ea.length(), ra.length())
    }

    private fun putExamNoSave(e: Exam) {
        val i = exams.indexOfFirst { it.id == e.id }
        if (i >= 0) exams[i] = e else exams.add(e)
    }

    // ---------- JSON ----------
    private fun examTo(e: Exam) = JSONObject().apply {
        put("id", e.id); put("nama", e.nama); put("mapel", e.mapel); put("kelas", e.kelas); put("sekolah", e.sekolah)
        put("n", e.n); put("paket", e.paket); put("kktp", e.kktp); put("created", e.created)
        put("kunci", JSONObject().apply { e.kunci.forEach { (k, v) -> put(k, v) } })
        put("siswa", JSONArray(e.siswa))
    }

    private fun examFrom(o: JSONObject): Exam {
        val k = HashMap<String, String>()
        o.optJSONObject("kunci")?.let { j -> j.keys().forEach { key -> k[key] = j.optString(key) } }
        val s = ArrayList<String>()
        o.optJSONArray("siswa")?.let { a -> for (i in 0 until a.length()) s.add(a.optString(i)) }
        return Exam(o.getString("id"), o.optString("nama"), o.optString("mapel"), o.optString("kelas"), o.optString("sekolah"),
            o.optInt("n", 40), o.optInt("paket", 1), o.optDouble("kktp", 75.0), k, s, o.optLong("created", System.currentTimeMillis()))
    }

    private fun resTo(r: Res, embedImg: Boolean) = JSONObject().apply {
        put("id", r.id); put("examId", r.examId); put("absen", r.absen ?: JSONObject.NULL); put("paket", r.paket ?: JSONObject.NULL)
        put("ts", r.ts)
        put("ans", JSONArray().apply { r.ans.forEach { a -> put(JSONObject().put("c", a.c ?: JSONObject.NULL).put("s", a.s).put("m", a.m).put("n", a.n)) } })
        put("ovr", JSONObject().apply { r.ovr.forEach { (q, v) -> put(q.toString(), v) } })
        put("notes", JSONArray(r.notes))
        if (embedImg) {
            val f = File(imgDir, r.img)
            put("img", if (f.exists()) "data:image/jpeg;base64," + Base64.encodeToString(f.readBytes(), Base64.NO_WRAP) else "")
        } else put("img", r.img)
    }

    private fun resFrom(o: JSONObject): Res {
        val ans = ArrayList<Ans>()
        o.optJSONArray("ans")?.let { a ->
            for (i in 0 until a.length()) {
                val j = a.getJSONObject(i)
                ans.add(Ans(if (j.isNull("c")) null else j.optString("c"), j.optString("s", "kosong"), j.optString("m", "....."), j.optString("n", "")))
            }
        }
        val ovr = HashMap<Int, String>()
        o.optJSONObject("ovr")?.let { j -> j.keys().forEach { key -> key.toIntOrNull()?.let { q -> ovr[q] = j.optString(key) } } }
        val notes = ArrayList<String>()
        o.optJSONArray("notes")?.let { a -> for (i in 0 until a.length()) notes.add(a.optString(i)) }
        return Res(o.getString("id"), o.getString("examId"), if (o.isNull("absen")) null else o.optInt("absen"),
            if (o.isNull("paket")) null else o.optString("paket"), ans, ovr, o.optString("img"), o.optLong("ts"), notes)
    }
}

fun newId(): String = java.lang.Long.toString(System.currentTimeMillis(), 36) + java.lang.Long.toString((Math.random() * 1e8).toLong(), 36)
