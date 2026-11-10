package net.pakeng.koreksiljk.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import net.pakeng.koreksiljk.data.JURUSAN
import net.pakeng.koreksiljk.data.TINGKAT
import net.pakeng.koreksiljk.omr.Layout
import java.io.File

val Pri = Color(0xFF0F5C4D)
val Pri2 = Color(0xFF13806B)
val Ok = Color(0xFF1A8F4E)
val No = Color(0xFFC8372D)
val Warn = Color(0xFFD98A00)
val Info = Color(0xFF2A6FD6)
val Mut = Color(0xFF66726E)
val Bd = Color(0xFFDFE5E3)
val Bg = Color(0xFFF3F5F4)

@Composable
fun Pill(text: String, kind: String = "") {
    val (bg, fg) = when (kind) {
        "ok" -> Color(0xFFE3F5EA) to Ok
        "no" -> Color(0xFFFBE6E4) to No
        "warn" -> Color(0xFFFFF2D9) to Color(0xFF9A6200)
        "info" -> Color(0xFFE5EEFC) to Info
        else -> Color(0xFFEEF1F0) to Mut
    }
    Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.background(bg, RoundedCornerShape(99.dp)).padding(horizontal = 8.dp, vertical = 1.dp))
}

@Composable
fun CardBox(modifier: Modifier = Modifier, borderColor: Color = Bd, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().padding(bottom = 10.dp)
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        content = content,
    )
}

@Composable
fun Gap(content: @Composable RowScope.() -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

fun share(ctx: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", file)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, title))
}

fun safeName(s: String) = s.replace(Regex("[^\\w\\- ]+"), "").trim().replace(Regex("\\s+"), "_").ifEmpty { "ujian" }

/** Potongan area tulisan tangan Nama/Kelas/Mapel dari gambar LJK yang sudah diluruskan. */
fun loadNameCrop(file: File): Bitmap? {
    val bmp = BitmapFactory.decodeFile(file.path) ?: return null
    val k = bmp.width / Layout.W
    val b = Layout.nameBox
    val x = (b.x * k).toInt().coerceIn(0, bmp.width - 1); val y = (b.y * k).toInt().coerceIn(0, bmp.height - 1)
    return Bitmap.createBitmap(bmp, x, y, (b.w * k).toInt().coerceAtMost(bmp.width - x), (b.h * k).toInt().coerceAtMost(bmp.height - y))
}

@Composable
fun NeedExam(st: AppState) {
    Column(Modifier.padding(12.dp)) {
        CardBox {
            Text("Belum ada ujian.", fontWeight = FontWeight.Bold)
            Text("Buat ujian dulu di menu Ujian: isi mapel, jumlah soal, dan kunci jawaban.", color = Mut, fontSize = 13.sp)
            Button(onClick = { st.page = "ujian"; st.editExamId = null; st.editing = true }, modifier = Modifier.padding(top = 8.dp)) { Text("＋ Buat ujian") }
        }
    }
}

/** Tombol pilihan (terisi bila dipilih). */
@Composable
fun Choice(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val pad = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
    if (selected) Button(onClick = onClick, contentPadding = pad, modifier = modifier) { Text(label, fontSize = 13.sp, maxLines = 1) }
    else OutlinedButton(onClick = onClick, contentPadding = pad, modifier = modifier) { Text(label, fontSize = 13.sp, maxLines = 1) }
}

/** Pemilih kelas: tingkat, kompetensi keahlian, dan rombel. Nilai berbentuk "XI AK 2". */
@Composable
fun KelasPicker(value: String, onChange: (String) -> Unit) {
    val p = value.split(" ")
    val t = p.getOrNull(0)?.takeIf { it in TINGKAT } ?: TINGKAT[0]
    val j = p.getOrNull(1)?.takeIf { it in JURUSAN } ?: JURUSAN[0]
    val r = p.getOrNull(2)?.toIntOrNull() ?: 1
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { TINGKAT.forEach { x -> Choice(x, x == t, Modifier.weight(1f)) { onChange("$x $j $r") } } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { JURUSAN.forEach { x -> Choice(x, x == j, Modifier.weight(1f)) { onChange("$t $x $r") } } }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { (1..6).forEach { x -> Choice("$x", x == r, Modifier.weight(1f)) { onChange("$t $j $x") } } }
    }
}
