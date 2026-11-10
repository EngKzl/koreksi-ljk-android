package net.pakeng.koreksiljk.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pakeng.koreksiljk.data.Exam
import net.pakeng.koreksiljk.data.Res
import net.pakeng.koreksiljk.data.Scanner
import net.pakeng.koreksiljk.data.fmt
import net.pakeng.koreksiljk.data.newId
import net.pakeng.koreksiljk.data.score
import java.io.File

/** Hasil koreksi sesi ini (bertahan selama aplikasi terbuka). */
val sessionIds = mutableStateListOf<String>()

@Composable
fun KoreksiScreen(st: AppState) {
    val store = st.store
    val e = store.active ?: return NeedExam(st)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("siswa") }
    var prog by remember { mutableStateOf<Float?>(null) }
    var errors by remember { mutableStateOf<List<String>>(emptyList()) }
    var keyAsk by remember { mutableStateOf<Pair<String, String>?>(null) } // paket to key
    var paket by remember(e.id) { mutableStateOf("A") }
    val capFile = remember { File(File(ctx.cacheDir, "photos").apply { mkdirs() }, "capture.jpg") }
    val capUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", capFile) }

    fun process(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scope.launch {
            prog = 0f
            val errs = ArrayList<String>()
            var okc = 0
            var lastId: String? = null
            for ((i, uri) in uris.withIndex()) {
                prog = i.toFloat() / uris.size
                val id = newId()
                val imgName = "$id.jpg"
                try {
                    val sc = withContext(Dispatchers.Default) { Scanner.scan(ctx, uri, e.n, store.th, File(store.imgDir, imgName)) }
                    val r = sc.sheet
                    if (!r.ok) { errs.add("Foto ${i + 1}: ${r.error}"); continue }
                    if (mode == "kunci") {
                        File(store.imgDir, imgName).delete()
                        val key = sc.ans.joinToString("") { a -> if ((a.s == "ok" || a.s == "ragu") && a.c != null) a.c else "-" }
                        keyAsk = Pair(paket, key)
                        okc++; continue
                    }
                    store.putResult(Res(id, e.id, store.kelas, "", paket, sc.ans, emptyMap(), imgName, System.currentTimeMillis()))
                    sessionIds.add(0, id)
                    lastId = id
                    okc++
                } catch (ex: Throwable) {
                    errs.add("Foto ${i + 1}: ${ex.message ?: ex.javaClass.simpleName}")
                }
            }
            prog = null
            errors = errs
            if (mode == "siswa" && okc > 0 && errs.isEmpty()) launch { st.snack.showSnackbar("$okc LJK terkoreksi") }
            // satu foto: langsung buka detail untuk mengisi nama & memeriksa hasil
            if (mode == "siswa" && uris.size == 1 && lastId != null) st.detailId = lastId
        }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) process(listOf(capUri)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> process(uris) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Koreksi", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        CardBox {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("siswa" to "Koreksi siswa", "kunci" to "Pindai kunci").forEach { (k, l) ->
                    if (mode == k) Button(onClick = { mode = k }, modifier = Modifier.weight(1f)) { Text(l) }
                    else OutlinedButton(onClick = { mode = k }, modifier = Modifier.weight(1f)) { Text(l) }
                }
            }
            Text(
                if (mode == "siswa") "Pilih kelas, lalu foto LJK satu per satu atau pilih banyak file hasil scan sekaligus. Nama siswa diisi setelah scan (LJK hanya memuat tulisan tangan)."
                else "Mode kunci: isi satu LJK kosong dengan jawaban benar (silang), lalu foto. Kunci terisi otomatis.",
                color = Mut, fontSize = 13.sp, modifier = Modifier.padding(vertical = 10.dp),
            )
            if (mode == "siswa") {
                Text("Kelas: ${store.kelas}", fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
                KelasPicker(store.kelas) { store.kelas = it; store.save() }
            }
            if (e.paket > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text("Paket soal:", fontSize = 13.sp, color = Mut)
                e.paketLetters.forEach { x -> Choice(x, x == paket, Modifier.weight(1f)) { paket = x } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 10.dp)) {
                Button(enabled = prog == null, onClick = { camera.launch(capUri) }, modifier = Modifier.weight(1f).height(56.dp)) { Text("📷 Foto LJK", fontSize = 16.sp) }
                OutlinedButton(enabled = prog == null, onClick = { picker.launch("image/*") }, modifier = Modifier.weight(1f).height(56.dp)) { Text("🖼 Pilih file", fontSize = 16.sp) }
            }
            prog?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) }
            Text("Tips: letakkan LJK di permukaan datar dan terang, seluruh lembar (kop sampai tanda tangan) masuk bingkai, hindari bayangan HP.", color = Mut, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
        val rs = sessionIds.mapNotNull { id -> store.results.firstOrNull { it.id == id && it.examId == e.id } }
        if (rs.isNotEmpty()) Text("Hasil sesi ini", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        rs.forEach { r -> ResultCard(st, e, r) }
    }

    if (errors.isNotEmpty()) AlertDialog(onDismissRequest = { errors = emptyList() },
        title = { Text("Sebagian gagal dibaca") },
        text = { Text(errors.joinToString("\n\n")) },
        confirmButton = { TextButton(onClick = { errors = emptyList() }) { Text("OK") } })

    keyAsk?.let { (p, key) ->
        val bad = key.mapIndexedNotNull { i, c -> if (c == '-') i + 1 else null }
        AlertDialog(onDismissRequest = { keyAsk = null },
            title = { Text("Kunci paket $p terbaca") },
            text = {
                Column {
                    Text(key.chunked(10).joinToString(" "), fontFamily = FontFamily.Monospace)
                    Text(if (bad.isEmpty()) "Semua soal terbaca." else "Tidak terbaca/ganda: no. ${bad.joinToString(", ")} (ditandai \"-\", perbaiki lewat Edit).", color = Mut, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.putExam(e.copy(kunci = e.kunci + (p to key)))
                    keyAsk = null
                    scope.launch { st.snack.showSnackbar("Kunci paket $p tersimpan") }
                }) { Text("Simpan sebagai kunci") }
            },
            dismissButton = { TextButton(onClick = { keyAsk = null }) { Text("Batal") } })
    }
}

@Composable
fun ResultCard(st: AppState, e: Exam, r: Res) {
    val s = score(e, r)
    val crop = remember(r.img) { loadNameCrop(File(st.store.imgDir, r.img))?.asImageBitmap() }
    CardBox(Modifier.clickable { st.detailId = r.id }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (r.nama.isNotBlank()) Text(r.nama, fontWeight = FontWeight.Bold)
                else if (crop != null) Image(crop, "Tulisan nama di LJK", Modifier.size(width = 168.dp, height = 66.dp).border(1.dp, Bd))
                Text("${r.kelas}${if (e.paket > 1) " · Paket ${r.paket}" else ""} · B ${s.benar} · S ${s.salah} · K ${s.kosong}", color = Mut, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (r.nama.isBlank()) Pill("nama belum diisi", "no")
                    if (s.ganda > 0) Pill("${s.ganda} ganda", "warn")
                    if (s.ragu > 0) Pill("${s.ragu} ragu", "warn")
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(fmt(s.nilai), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = if (s.nilai == null) Mut else if (s.nilai >= e.kktp) Ok else No)
        }
    }
}
