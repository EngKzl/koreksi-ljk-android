package net.pakeng.koreksiljk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.pakeng.koreksiljk.data.LETTERS
import net.pakeng.koreksiljk.data.analyze
import net.pakeng.koreksiljk.data.fmt

@Composable
fun AnalisisScreen(st: AppState) {
    val store = st.store
    val e = store.active ?: return NeedExam(st)
    var p by remember(e.id) { mutableStateOf("A") }
    if (p !in e.paketLetters) p = "A"
    val a = analyze(e, store.resultsOf(e.id), p)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("Analisis Butir Soal", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (e.paket > 1) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                e.paketLetters.forEach { x ->
                    if (x == p) Button(onClick = {}, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Text(x) }
                    else OutlinedButton(onClick = { p = x }, contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)) { Text(x) }
                }
            }
        }
        val kat = when { a.kr20 == null -> ""; a.kr20 >= 0.7 -> " (reliabel)"; a.kr20 >= 0.5 -> " (cukup)"; else -> " (rendah)" }
        StatGrid(listOf(
            "Peserta" to "${a.N}",
            "Rata-rata benar" to fmt(a.mean),
            "KR-20$kat" to fmt(a.kr20),
            "Soal diterima" to "${a.items.count { it.rek == "Diterima" }}",
            "Perlu revisi" to "${a.items.count { it.rek.isNotEmpty() && it.rek != "Diterima" }}",
        ))
        Text("P = tingkat kesukaran (proporsi benar) · D = daya beda (kelompok atas − bawah 27%) · pengecoh berfungsi bila dipilih ≥5% siswa", color = Mut, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
        if (a.N < 2) { CardBox { Text("Butuh minimal 2 LJK terkoreksi untuk analisis.", color = Mut) }; return@Column }
        val pc = { n: Int -> "$n·${Math.round(n * 100.0 / a.N)}%" }
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(10.dp)).border(1.dp, Bd, RoundedCornerShape(10.dp)).horizontalScroll(rememberScrollState())) {
            Row(Modifier.background(Color(0xFFF7F9F8))) {
                Cell("No", 40.dp, true, Mut); Cell("Kunci", 50.dp, true, Mut)
                LETTERS.forEach { Cell(it, 64.dp, true, Mut) }
                Cell("Ksg", 40.dp, true, Mut); Cell("Gnd", 40.dp, true, Mut); Cell("P", 50.dp, true, Mut); Cell("Kesukaran", 80.dp, true, Mut)
                Cell("D", 50.dp, true, Mut); Cell("Daya beda", 90.dp, true, Mut); Cell("Pengecoh lemah", 110.dp, true, Mut); Cell("Keputusan", 150.dp, true, Mut)
            }
            a.items.forEach { it ->
                Row {
                    Cell("${it.q + 1}", 40.dp); Cell(it.kc, 50.dp, true)
                    LETTERS.forEach { o -> Cell(pc(it.cnt[o] ?: 0), 64.dp, o == it.kc, if (o == it.kc) Ok else Color.Unspecified) }
                    Cell("${it.cnt["kosong"] ?: 0}", 40.dp); Cell("${it.cnt["ganda"] ?: 0}", 40.dp)
                    Cell(fmt(it.P), 50.dp); Cell(it.katP, 80.dp); Cell(fmt(it.D), 50.dp); Cell(it.katD, 90.dp)
                    Cell(it.pengecoh.joinToString(", "), 110.dp)
                    Cell(it.rek.ifEmpty { "tidak dinilai" }, 150.dp, true, when (it.rek) { "Diterima" -> Ok; "Revisi" -> Warn; "" -> Mut; else -> No })
                }
            }
        }
    }
}
