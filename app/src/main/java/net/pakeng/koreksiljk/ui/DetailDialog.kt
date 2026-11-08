package net.pakeng.koreksiljk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import net.pakeng.koreksiljk.data.LETTERS
import net.pakeng.koreksiljk.data.finalAns
import net.pakeng.koreksiljk.data.fmt
import net.pakeng.koreksiljk.data.score
import net.pakeng.koreksiljk.omr.Layout
import java.io.File

private val MARKNAME = mapOf('x' to "silang", 't' to "tanda lain", 'c' to "dicoret")

@Composable
fun DetailDialog(st: AppState, id: String) {
    val store = st.store
    val orig = store.results.firstOrNull { it.id == id }
    val e = orig?.let { o -> store.exams.firstOrNull { it.id == o.examId } }
    if (orig == null || e == null) { st.detailId = null; return }
    val scope = rememberCoroutineScope()
    var r by remember(id) { mutableStateOf(orig) }
    var absenTxt by remember(id) { mutableStateOf(orig.absen?.toString() ?: "") }
    var sel by remember(id) { mutableStateOf<Int?>(null) }
    var confirmDup by remember { mutableStateOf(false) }
    var confirmDel by remember { mutableStateOf(false) }
    val img = remember(r.img) { File(store.imgDir, r.img).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
    val s = score(e, r)
    val key = e.key(r.paket)
    val close = { st.detailId = null }

    fun doSave() {
        val dup = r.absen?.let { ab -> store.resultsOf(e.id).firstOrNull { it.absen == ab && it.id != r.id } }
        if (dup != null) store.deleteResult(dup.id)
        val notes = r.notes.filter { n -> !(r.absen != null && n.contains("absen", true)) && !(r.paket != null && n.contains("paket", true)) }
        store.putResult(r.copy(notes = notes))
        close()
        scope.launch { st.snack.showSnackbar("Tersimpan") }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.White).verticalScroll(rememberScrollState()).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("LJK " + (r.absen?.let { "absen $it" } ?: "(absen belum terbaca)"), fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = close) { Text("✕ Tutup") }
            }
            // Gambar LJK + penanda
            Box(Modifier.fillMaxWidth().aspectRatio(210f / 297f).border(1.dp, Bd)) {
                if (img != null) Image(img, null, Modifier.matchParentSize())
                Canvas(Modifier.matchParentSize().pointerInput(id) {
                    detectTapGestures { off ->
                        val x = off.x / size.width * 210; val y = off.y / size.height * 297
                        for (q in 0 until e.n) {
                            val b0 = Layout.ansBox(q, 0); val b4 = Layout.ansBox(q, 4)
                            if (x >= b0.x - 10 && x <= b4.x + b4.w + 1 && y >= b0.y - 1 && y <= b0.y + b0.h + 1) { sel = q; break }
                        }
                    }
                }) {
                    val k = size.width / 210f
                    fun box(q: Int, o: Int, fill: Color?, stroke: Color?, w: Float = 2f) {
                        val b = Layout.ansBox(q, o)
                        val tl = Offset((b.x * k).toFloat(), (b.y * k).toFloat()); val sz = Size((b.w * k).toFloat(), (b.h * k).toFloat())
                        if (fill != null) drawRect(fill, tl, sz)
                        if (stroke != null) drawRect(stroke, Offset(tl.x - 1.5f, tl.y - 1.5f), Size(sz.width + 3f, sz.height + 3f), style = Stroke(w))
                    }
                    for (q in 0 until e.n) {
                        val f = finalAns(r, q); val kc = key.getOrNull(q)?.toString(); val a = r.ans.getOrNull(q)
                        if (f.st == "ganda") {
                            a?.m?.forEachIndexed { o, m -> if (m == 'x' || m == 't') box(q, o, Color(0x73F08C00), Warn) }
                        } else if (f.ch != null) {
                            val o = LETTERS.indexOf(f.ch)
                            val col = if (kc == null || kc == "-") Color(0x59787878) else if (f.ch == kc) Color(0x661A8F4E) else Color(0x6BC8372D)
                            box(q, o, col, if (f.st == "ragu") Warn else null)
                        }
                        if (kc != null && kc != "-" && f.ch != kc) box(q, LETTERS.indexOf(kc), null, Ok, 2.5f)
                    }
                    sel?.let { q ->
                        val b0 = Layout.ansBox(q, 0); val b4 = Layout.ansBox(q, 4)
                        drawRect(Info, Offset(((b0.x - 10) * k).toFloat(), ((b0.y - 1) * k).toFloat()),
                            Size(((b4.x + b4.w - b0.x + 11) * k).toFloat(), ((b0.h + 2) * k).toFloat()), style = Stroke(3f))
                    }
                }
            }
            Text("■ hijau benar · ■ merah salah · □ hijau kunci · ■ oranye ganda/ragu — ketuk baris soal untuk mengubah", color = Mut, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(absenTxt, { v -> absenTxt = v.filter { it.isDigit() }.take(2); r = r.copy(absen = absenTxt.toIntOrNull()) },
                    label = { Text("No. absen") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                if (e.paket > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    e.paketLetters.forEach { p ->
                        if (r.paket == p) Button(onClick = { }, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Text(p) }
                        else OutlinedButton(onClick = { r = r.copy(paket = p) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Text(p) }
                    }
                }
            }
            Text(e.namaSiswa(r.absen).ifEmpty { if (r.absen != null) "(nama tidak ada di daftar siswa)" else "Isi no. absen secara manual" }, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(fmt(s.nilai), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = if (s.nilai != null && s.nilai >= e.kktp) Ok else No)
                Spacer(Modifier.padding(4.dp))
                Text("Benar ${s.benar} · Salah ${s.salah} · Kosong ${s.kosong}" + (if (s.ganda > 0) " · Ganda ${s.ganda}" else "") + (if (s.ragu > 0) " · Ragu ${s.ragu}" else ""), color = Mut, fontSize = 13.sp)
            }
            if (r.notes.isNotEmpty()) Text(r.notes.joinToString("; "), color = No, fontSize = 13.sp)
            if (key.length < e.n) Text("Kunci paket ini belum lengkap.", color = No, fontSize = 13.sp)

            // Panel ubah jawaban
            sel?.let { q ->
                val f = finalAns(r, q); val a = r.ans.getOrNull(q); val kc = key.getOrNull(q)?.toString()
                CardBox(Modifier.padding(top = 8.dp), borderColor = Info) {
                    Text("Soal ${q + 1} · kunci ${if (kc != null && kc != "-") kc else "(tidak dinilai)"} · jawaban ${if (f.st == "ganda") "GANDA" else f.ch ?: "kosong"}${if (f.ovr) " (diubah manual)" else ""}", fontWeight = FontWeight.Bold)
                    val det = a?.m?.mapIndexedNotNull { o, m -> MARKNAME[m]?.let { "${LETTERS[o]}=$it" } }?.joinToString(", ").orEmpty()
                    Text("Terbaca mesin: ${det.ifEmpty { "tidak ada tanda" }}${if (!a?.n.isNullOrEmpty()) " — ${a?.n}" else ""}", color = Mut, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                        LETTERS.forEach { L ->
                            val on = f.ch == L && f.st != "ganda"
                            val act = { r = r.copy(ovr = r.ovr + (q to L)) }
                            if (on) Button(onClick = act, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp), modifier = Modifier.weight(1f)) { Text(L) }
                            else OutlinedButton(onClick = act, contentPadding = androidx.compose.foundation.layout.PaddingValues(6.dp), modifier = Modifier.weight(1f)) { Text(L) }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedButton(onClick = { r = r.copy(ovr = r.ovr + (q to "")) }) { Text("Kosong/batal") }
                        if (f.ovr) OutlinedButton(onClick = { r = r.copy(ovr = r.ovr - q) }) { Text("↺ Hasil mesin") }
                    }
                }
            }

            // Kisi soal
            Spacer(Modifier.height(8.dp))
            (0 until e.n).chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    row.forEach { q ->
                        val f = finalAns(r, q); val kc = key.getOrNull(q)?.toString()
                        val (bg, bd) = when {
                            f.st == "ganda" -> Color(0xFFFFE3C2) to Color(0xFFF2A650)
                            f.ch == null -> Color(0xFFF1F1F1) to Bd
                            kc == null || kc == "-" -> Color.White to Bd
                            f.ch == kc -> Color(0xFFE3F5EA) to Color(0xFF9FD8B6)
                            else -> Color(0xFFFBE6E4) to Color(0xFFEFB3AD)
                        }
                        val border = when { q == sel -> Info; f.ovr -> Info; f.st == "ragu" -> Warn; else -> bd }
                        Column(
                            Modifier.weight(1f).background(bg, RoundedCornerShape(7.dp)).border(if (q == sel || f.ovr || f.st == "ragu") 2.dp else 1.dp, border, RoundedCornerShape(7.dp))
                                .clickable { sel = q }.padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${q + 1}", fontSize = 11.sp, color = Mut)
                            Text(if (f.st == "ganda") "⚠" else f.ch ?: "–", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                    repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp, bottom = 30.dp)) {
                Button(onClick = {
                    val dup = r.absen?.let { ab -> store.resultsOf(e.id).firstOrNull { it.absen == ab && it.id != r.id } }
                    if (dup != null) confirmDup = true else doSave()
                }) { Text("Simpan") }
                TextButton(onClick = { confirmDel = true }) { Text("Hapus hasil", color = No) }
            }
        }
    }
    if (confirmDup) AlertDialog(onDismissRequest = { confirmDup = false },
        title = { Text("No. absen sudah dipakai") },
        text = { Text("Absen ${r.absen} sudah punya hasil lain. Timpa hasil lama?") },
        confirmButton = { TextButton(onClick = { confirmDup = false; doSave() }) { Text("Timpa") } },
        dismissButton = { TextButton(onClick = { confirmDup = false }) { Text("Batal") } })
    if (confirmDel) AlertDialog(onDismissRequest = { confirmDel = false },
        title = { Text("Hapus hasil LJK ini?") },
        confirmButton = { TextButton(onClick = { confirmDel = false; store.deleteResult(r.id); sessionIds.remove(r.id); close() }) { Text("Hapus", color = No) } },
        dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Batal") } })
}
