package net.pakeng.koreksiljk.data

import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sel tebal (header). */
data class B(val v: Any?)

class XSheet(val name: String, val rows: List<List<Any?>>, val widths: List<Int> = emptyList())

/** Penulis XLSX minimal (tanpa library). */
object Xlsx {
    private fun x(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        .filter { it == '\t' || it == '\n' || it == '\r' || it >= ' ' }

    private fun col(i0: Int): String {
        var i = i0 + 1
        val sb = StringBuilder()
        while (i > 0) { val m = (i - 1) % 26; sb.insert(0, ('A' + m)); i = (i - m - 1) / 26 }
        return sb.toString()
    }

    private fun sheetXml(s: XSheet): String {
        val sb = StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        if (s.widths.isNotEmpty()) {
            sb.append("<cols>")
            s.widths.forEachIndexed { i, w -> sb.append("<col min=\"${i + 1}\" max=\"${i + 1}\" width=\"$w\" customWidth=\"1\"/>") }
            sb.append("</cols>")
        }
        sb.append("<sheetData>")
        s.rows.forEachIndexed { ri, r ->
            sb.append("<row r=\"${ri + 1}\">")
            r.forEachIndexed { ci, v0 ->
                var st = 0
                var v = v0
                if (v is B) { st = 1; v = v.v }
                if (v == null || (v is String && v.isEmpty())) return@forEachIndexed
                val ref = col(ci) + (ri + 1)
                when (v) {
                    is Number -> sb.append("<c r=\"$ref\" s=\"$st\"><v>$v</v></c>")
                    else -> sb.append("<c r=\"$ref\" s=\"$st\" t=\"inlineStr\"><is><t xml:space=\"preserve\">${x(v.toString())}</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        return sb.append("</sheetData></worksheet>").toString()
    }

    fun write(out: OutputStream, sheets: List<XSheet>) {
        val f = ArrayList<Pair<String, String>>()
        f.add("[Content_Types].xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/><Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
            sheets.indices.joinToString("") { "<Override PartName=\"/xl/worksheets/sheet${it + 1}.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" } + "</Types>")
        f.add("_rels/.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/></Relationships>")
        f.add("xl/workbook.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>" +
            sheets.mapIndexed { i, s -> "<sheet name=\"${x(s.name.take(31))}\" sheetId=\"${i + 1}\" r:id=\"rId${i + 1}\"/>" }.joinToString("") + "</sheets></workbook>")
        f.add("xl/_rels/workbook.xml.rels" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            sheets.indices.joinToString("") { "<Relationship Id=\"rId${it + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet${it + 1}.xml\"/>" } +
            "<Relationship Id=\"rId${sheets.size + 1}\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/></Relationships>")
        f.add("xl/styles.xml" to "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font><font><b/><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts><fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills><borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders><cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs><cellXfs count=\"2\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/><xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/></cellXfs><cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles></styleSheet>")
        sheets.forEachIndexed { i, s -> f.add("xl/worksheets/sheet${i + 1}.xml" to sheetXml(s)) }
        ZipOutputStream(out).use { z ->
            for ((name, body) in f) { z.putNextEntry(ZipEntry(name)); z.write(body.toByteArray(Charsets.UTF_8)); z.closeEntry() }
        }
    }

    /** Buku kerja rekap untuk satu ujian; [kelas] = label kelas yang direkap ("Semua kelas" bila gabungan). */
    fun rekapBook(e: Exam, rs: List<Res>, kelas: String): List<XSheet> {
        val rows = rekapRows(e, rs)
        val s1 = ArrayList<List<Any?>>()
        s1.add(listOf(B("REKAP NILAI " + e.nama.uppercase())))
        s1.add(listOf("Mapel", e.mapel)); s1.add(listOf("Kelas", kelas))
        s1.add(listOf("Jumlah soal", e.n)); s1.add(listOf("KKTP", e.kktp)); s1.add(emptyList())
        s1.add(listOf(B("No"), B("Nama"), B("Kelas"), B("Paket"), B("Benar"), B("Salah"), B("Kosong"), B("Ganda"), B("Nilai"), B("Keterangan")))
        rows.forEachIndexed { i, x ->
            val s = x.s
            s1.add(listOf(i + 1, x.r.nama, x.r.kelas, x.r.paket, s.benar, s.salah, s.kosong, s.ganda, s.nilai,
                if (s.nilai == null) "Kunci belum lengkap" else if (s.nilai >= e.kktp) "Tuntas" else "Remedial"))
        }
        val s2 = ArrayList<List<Any?>>()
        s2.add(listOf(B("No"), B("Nama"), B("Kelas"), B("Paket")) + (1..e.n).map { B(it) })
        rows.forEachIndexed { i, x ->
            s2.add(listOf<Any?>(i + 1, x.r.nama, x.r.kelas, x.r.paket) + (0 until e.n).map { q -> val f = finalAns(x.r, q); if (f.st == "ganda") "GANDA" else f.ch ?: "" })
        }
        e.paketLetters.forEach { p -> s2.add(listOf<Any?>("", "KUNCI $p", "", p) + e.key(p).padEnd(e.n, '-').take(e.n).map { it.toString() }) }
        val out = arrayListOf(XSheet("Rekap", s1, listOf(6, 32, 12, 7, 8, 8, 8, 8, 9, 16)), XSheet("Jawaban", s2, listOf(6, 28, 12, 7)))
        for (p in e.paketLetters) {
            val a = analyze(e, rs, p)
            if (a.N < 2) continue
            val s = ArrayList<List<Any?>>()
            s.add(listOf(B("ANALISIS BUTIR SOAL" + if (e.paket > 1) " PAKET $p" else "")))
            s.add(listOf("Peserta", a.N)); s.add(listOf("Reliabilitas KR-20", a.kr20?.let { Math.round(it * 1000) / 1000.0 } ?: "")); s.add(emptyList())
            s.add(listOf("No", "Kunci", "A", "B", "C", "D", "E", "Kosong", "Ganda", "P", "Kesukaran", "D", "Daya beda", "Pengecoh tdk berfungsi", "Keputusan").map { B(it) })
            a.items.forEach { it ->
                s.add(listOf(it.q + 1, it.kc, it.cnt["A"], it.cnt["B"], it.cnt["C"], it.cnt["D"], it.cnt["E"], it.cnt["kosong"], it.cnt["ganda"],
                    it.P?.let { v -> Math.round(v * 100) / 100.0 } ?: "", it.katP, it.D?.let { v -> Math.round(v * 100) / 100.0 } ?: "", it.katD,
                    it.pengecoh.joinToString(", "), it.rek.ifEmpty { "Tidak dinilai" }))
            }
            out.add(XSheet("Analisis $p", s, listOf(6, 7, 6, 6, 6, 6, 6, 8, 7, 7, 11, 7, 12, 22, 18)))
        }
        return out
    }
}
