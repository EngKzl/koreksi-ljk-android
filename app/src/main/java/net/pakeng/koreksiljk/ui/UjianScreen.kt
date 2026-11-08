package net.pakeng.koreksiljk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.pakeng.koreksiljk.data.Exam
import net.pakeng.koreksiljk.data.LETTERS
import net.pakeng.koreksiljk.data.newId
import net.pakeng.koreksiljk.data.normKey

@Composable
fun UjianScreen(st: AppState) {
    if (st.editing) { ExamForm(st); return }
    val store = st.store
    var confirmDel by remember { mutableStateOf<Exam?>(null) }
    var dup by remember { mutableStateOf<Exam?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Daftar Ujian", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = { st.editExamId = null; st.editing = true }) { Text("＋ Ujian baru") }
        }
        if (store.exams.isEmpty()) CardBox {
            Text("Selamat datang!", fontWeight = FontWeight.Bold)
            Text("Langkah: 1) Buat ujian & isi kunci → 2) Cetak LJK → 3) Foto/scan LJK siswa → 4) Lihat rekap & analisis, simpan Excel.", color = Mut, fontSize = 13.sp)
        }
        store.exams.forEach { e ->
            val act = e.id == store.activeId
            CardBox(borderColor = if (act) Pri2 else Bd) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.nama, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                    if (act) { Spacer(Modifier.padding(4.dp)); Pill("aktif", "info") }
                }
                Text("${e.mapel} · ${e.kelas} · ${e.n} soal · ${e.paket} paket · KKTP ${net.pakeng.koreksiljk.data.fmt(e.kktp)}", color = Mut, fontSize = 13.sp)
                Text("${store.resultsOf(e.id).size} LJK terkoreksi · ${e.siswa.size} siswa", color = Mut, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    e.paketLetters.forEach { p ->
                        val k = e.key(p)
                        val filled = k.take(e.n).count { it != '-' }
                        Pill("Kunci ${if (e.paket > 1) "$p " else ""}$filled/${e.n}", if (k.length >= e.n) "ok" else "no")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 8.dp)) {
                    if (!act) Button(onClick = { store.activeId = e.id; store.save() }) { Text("Aktifkan", fontSize = 13.sp) }
                    OutlinedButton(onClick = { st.editExamId = e.id; st.editing = true }) { Text("✎ Edit", fontSize = 13.sp) }
                    OutlinedButton(onClick = { dup = e }) { Text("⧉ Duplikat", fontSize = 13.sp) }
                    TextButton(onClick = { confirmDel = e }) { Text("Hapus", color = No, fontSize = 13.sp) }
                }
            }
        }
    }
    confirmDel?.let { e ->
        AlertDialog(onDismissRequest = { confirmDel = null },
            title = { Text("Hapus ujian?") },
            text = { Text("Ujian \"${e.nama} · ${e.kelas}\" beserta semua hasil koreksinya akan dihapus.") },
            confirmButton = { TextButton(onClick = { store.deleteExam(e.id); confirmDel = null }) { Text("Hapus", color = No) } },
            dismissButton = { TextButton(onClick = { confirmDel = null }) { Text("Batal") } })
    }
    dup?.let { e ->
        var kelas by remember { mutableStateOf(e.kelas) }
        AlertDialog(onDismissRequest = { dup = null },
            title = { Text("Duplikat untuk kelas lain") },
            text = { OutlinedTextField(kelas, { kelas = it }, label = { Text("Kelas") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val c = e.copy(id = newId(), kelas = kelas, siswa = emptyList(), created = System.currentTimeMillis())
                    store.putExam(c); store.activeId = c.id; store.save(); dup = null
                }) { Text("Duplikat") }
            },
            dismissButton = { TextButton(onClick = { dup = null }) { Text("Batal") } })
    }
}

@Composable
private fun ExamForm(st: AppState) {
    val store = st.store
    val scope = rememberCoroutineScope()
    val old = st.editExamId?.let { id -> store.exams.firstOrNull { it.id == id } }
    var nama by remember { mutableStateOf(old?.nama ?: "") }
    var mapel by remember { mutableStateOf(old?.mapel ?: "") }
    var kelas by remember { mutableStateOf(old?.kelas ?: "") }
    var sekolah by remember { mutableStateOf(old?.sekolah ?: store.sekolah) }
    var n by remember { mutableStateOf((old?.n ?: 40).toString()) }
    var paket by remember { mutableStateOf(old?.paket ?: 1) }
    var kktp by remember { mutableStateOf(net.pakeng.koreksiljk.data.fmt(old?.kktp ?: 75.0).replace(',', '.')) }
    val kunci = remember { mutableStateMapOf<String, String>().apply { old?.kunci?.let { putAll(it) } } }
    var siswa by remember { mutableStateOf(old?.siswa?.joinToString("\n") ?: "") }
    val nInt = (n.toIntOrNull() ?: 0).coerceIn(0, 60)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (old == null) "Ujian baru" else "Edit ujian", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(nama, { nama = it }, label = { Text("Nama ujian") }, placeholder = { Text("Sumatif Akhir Semester Ganjil") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(mapel, { mapel = it }, label = { Text("Mata pelajaran") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(kelas, { kelas = it }, label = { Text("Kelas") }, placeholder = { Text("XI TKR 1") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(kktp, { kktp = it }, label = { Text("KKTP") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(0.6f))
        }
        OutlinedTextField(sekolah, { sekolah = it }, label = { Text("Sekolah") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(n, { n = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Jumlah soal (1–60)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
        Text("Jumlah paket soal", color = Mut, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { p ->
                if (p == paket) Button(onClick = { paket = p }) { Text("$p") } else OutlinedButton(onClick = { paket = p }) { Text("$p") }
            }
        }
        LETTERS.take(paket).forEach { p ->
            val k = normKey(kunci[p] ?: "")
            val okLen = k.length == nInt
            Text("Kunci jawaban${if (paket > 1) " paket $p" else ""}", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
            OutlinedTextField(kunci[p] ?: "", { kunci[p] = it }, placeholder = { Text("ABCDEABCDE… atau 1A 2C 3E") },
                modifier = Modifier.fillMaxWidth(), minLines = 2, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill("${k.length}/$nInt" + if (k.length > nInt) " (kelebihan)" else if (okLen) " ✓" else "", if (okLen) "ok" else if (k.length > nInt) "warn" else "no")
            }
            if (nInt > 0) Text(
                (0 until nInt).chunked(10).joinToString("\n") { ch -> "${ch.first() + 1}–${ch.last() + 1}: " + ch.joinToString(" ") { k.getOrNull(it)?.toString() ?: "?" } },
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Mut,
            )
        }
        Text("Ketik kunci berurutan. Pakai \"-\" untuk soal yang tidak dinilai/dianulir. Kunci juga bisa dipindai dari LJK guru (menu Koreksi → Pindai kunci).", color = Mut, fontSize = 12.sp)
        OutlinedTextField(siswa, { siswa = it }, label = { Text("Daftar siswa (satu nama per baris, urut no. absen)") }, minLines = 5, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (nama.isBlank()) { scope.launch { st.snack.showSnackbar("Nama ujian wajib diisi") }; return@Button }
                if (nInt < 1) { scope.launch { st.snack.showSnackbar("Jumlah soal 1–60") }; return@Button }
                val kmap = LETTERS.take(paket).associateWith { normKey(kunci[it] ?: "").take(nInt) }
                val e = Exam(
                    id = old?.id ?: newId(), nama = nama.trim(), mapel = mapel.trim(), kelas = kelas.trim(), sekolah = sekolah.trim(),
                    n = nInt, paket = paket, kktp = kktp.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    kunci = (old?.kunci ?: emptyMap()) + kmap,
                    siswa = siswa.lines().map { it.trim() }.filter { it.isNotEmpty() },
                    created = old?.created ?: System.currentTimeMillis(),
                )
                store.putExam(e)
                if (old == null) { store.activeId = e.id; store.save() }
                st.editing = false; st.editExamId = null
                scope.launch { st.snack.showSnackbar("Ujian tersimpan") }
            }) { Text("Simpan ujian") }
            if (store.exams.isNotEmpty()) OutlinedButton(onClick = { st.editing = false; st.editExamId = null }) { Text("Batal") }
        }
        Spacer(Modifier.height(40.dp))
    }
}
