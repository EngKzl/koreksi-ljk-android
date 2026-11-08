package net.pakeng.koreksiljk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import net.pakeng.koreksiljk.data.Xlsx
import net.pakeng.koreksiljk.data.fmt
import net.pakeng.koreksiljk.data.rekapRows
import java.io.File

@Composable
fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, Bd, RoundedCornerShape(10.dp)).padding(horizontal = 11.dp, vertical = 8.dp)) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = Mut)
    }
}

@Composable
fun StatGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 10.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (l, v) -> Stat(l, v, Modifier.weight(1f)) }
                repeat(3 - row.size) { Column(Modifier.weight(1f)) {} }
            }
        }
    }
}

@Composable
fun Cell(t: String, w: Dp, bold: Boolean = false, color: Color = Color.Unspecified, bg: Color = Color.Transparent) {
    Text(t, modifier = Modifier.width(w).background(bg).padding(horizontal = 6.dp, vertical = 6.dp), fontSize = 13.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = color, maxLines = 1)
}

@Composable
fun RekapScreen(st: AppState) {
    val store = st.store
    val e = store.active ?: return NeedExam(st)
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var askClear by remember { mutableStateOf(false) }
    val rs = store.resultsOf(e.id)
    val rows = rekapRows(e, rs)
    val vals = rows.mapNotNull { it.s?.nilai }
    val tuntas = vals.count { it >= e.kktp }

    fun xlsxFile(): File {
        val f = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "Rekap_${safeName(e.nama)}_${safeName(e.kelas)}.xlsx")
        f.outputStream().use { Xlsx.write(it, Xlsx.rekapBook(e, rs)) }
        return f
    }

    val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)!!.use { Xlsx.write(it, Xlsx.rekapBook(e, rs)) }
                scope.launch { st.snack.showSnackbar("File Excel tersimpan") }
            } catch (ex: Exception) { scope.launch { st.snack.showSnackbar("Gagal menyimpan: ${ex.message}") } }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Rekap Nilai", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            Button(onClick = { saver.launch("Rekap_${safeName(e.nama)}_${safeName(e.kelas)}.xlsx") }) { Text("⬇ Simpan Excel") }
            OutlinedButton(onClick = { share(ctx, xlsxFile(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Bagikan rekap") }) { Text("⇪ Bagikan") }
            TextButton(onClick = { askClear = true }) { Text("Hapus semua", color = No) }
        }
        StatGrid(listOf(
            "Terkoreksi" to "${rows.count { it.r != null }}/${maxOf(rows.size, e.siswa.size)}",
            "Rata-rata" to fmt(if (vals.isEmpty()) null else vals.average()),
            "Tertinggi" to fmt(vals.maxOrNull()),
            "Terendah" to fmt(vals.minOrNull()),
            "Tuntas" to "$tuntas (${if (vals.isEmpty()) 0 else Math.round(tuntas * 100.0 / vals.size)}%)",
            "Remedial" to "${vals.size - tuntas}",
        ))
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, Bd, RoundedCornerShape(10.dp)).horizontalScroll(rememberScrollState())) {
            Row(Modifier.background(Color(0xFFF7F9F8))) {
                Cell("No", 40.dp, true, Mut); Cell("Nama", 170.dp, true, Mut); Cell("Pkt", 40.dp, true, Mut); Cell("B", 36.dp, true, Mut)
                Cell("S", 36.dp, true, Mut); Cell("K", 36.dp, true, Mut); Cell("G", 36.dp, true, Mut); Cell("Nilai", 60.dp, true, Mut); Cell("Ket.", 90.dp, true, Mut)
            }
            rows.forEach { x ->
                val s = x.s
                Row(Modifier.clickable(enabled = x.r != null) { x.r?.let { st.detailId = it.id } }, verticalAlignment = Alignment.CenterVertically) {
                    Cell("${x.absen ?: "?"}", 40.dp); Cell(x.nama.ifEmpty { "—" }, 170.dp)
                    if (x.r == null || s == null) { Cell("belum dikoreksi", 334.dp, color = Mut) } else {
                        Cell(x.r.paket ?: "?", 40.dp); Cell("${s.benar}", 36.dp); Cell("${s.salah}", 36.dp); Cell("${s.kosong}", 36.dp)
                        Cell(if (s.ganda > 0) "${s.ganda}" else "", 36.dp, color = Warn); Cell(fmt(s.nilai), 60.dp, true)
                        Cell(
                            when { s.nilai == null -> "kunci?"; s.nilai >= e.kktp -> "Tuntas"; else -> "Remedial" } + if (s.ragu > 0 || x.r.absen == null || x.r.paket == null) " ⚠" else "",
                            90.dp, true, when { s.nilai == null -> Mut; s.nilai >= e.kktp -> Ok; else -> No },
                        )
                    }
                }
            }
        }
        Text("Ketuk baris untuk melihat LJK & mengoreksi manual. Nilai dihitung ulang otomatis bila kunci diubah.", color = Mut, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
    if (askClear) AlertDialog(onDismissRequest = { askClear = false },
        title = { Text("Hapus semua hasil?") },
        text = { Text("Semua hasil koreksi untuk \"${e.nama} · ${e.kelas}\" akan dihapus.") },
        confirmButton = { TextButton(onClick = { rs.forEach { store.deleteResult(it.id) }; sessionIds.clear(); askClear = false }) { Text("Hapus", color = No) } },
        dismissButton = { TextButton(onClick = { askClear = false }) { Text("Batal") } })
}
