package net.pakeng.koreksiljk.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.pakeng.koreksiljk.data.Exam
import net.pakeng.koreksiljk.data.SheetPdf
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Composable
fun NeedExam(st: AppState) {
    Column(Modifier.padding(12.dp)) {
        CardBox {
            Text("Belum ada ujian.", fontWeight = FontWeight.Bold)
            Text("Buat ujian dulu di menu Ujian: isi jumlah soal, paket, dan kunci jawaban.", color = Mut, fontSize = 13.sp)
            Button(onClick = { st.page = "ujian"; st.editExamId = null; st.editing = true }, modifier = Modifier.padding(top = 8.dp)) { Text("＋ Buat ujian") }
        }
    }
}

private fun pdfFile(ctx: Context, e: Exam, copies: Int): File {
    val f = File(File(ctx.cacheDir, "export").apply { mkdirs() }, "LJK_${safeName(e.nama)}_${safeName(e.kelas)}.pdf")
    SheetPdf.writePdf(e, f, copies)
    return f
}

private fun printPdf(ctx: Context, file: File, pages: Int, name: String) {
    val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(name, object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cs: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
            if (cs?.isCanceled == true) { cb.onLayoutCancelled(); return }
            cb.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(), true)
        }

        override fun onWrite(p: Array<out PageRange>?, dest: ParcelFileDescriptor, cs: CancellationSignal?, cb: WriteResultCallback) {
            try {
                FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) { cb.onWriteFailed(e.message) }
        }
    }, PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
}

@Composable
fun LjkScreen(st: AppState) {
    val e = st.store.active ?: return NeedExam(st)
    val ctx = LocalContext.current
    var copies by remember { mutableStateOf("1") }
    val preview = remember(e) {
        val s = 4f
        val bmp = Bitmap.createBitmap((210 * s).toInt(), (297 * s).toInt(), Bitmap.Config.ARGB_8888)
        SheetPdf.draw(Canvas(bmp), e, s)
        bmp.asImageBitmap()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        Text("Cetak Lembar Jawaban", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
        CardBox {
            Text("LJK dibuat sesuai ujian aktif (${e.n} soal, ${e.paket} paket). Cetak di kertas A4 lalu perbanyak dengan fotokopi. Keempat kotak hitam di sudut harus ikut tercetak.", color = Mut, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(copies, { copies = it.filter { c -> c.isDigit() }.take(2) }, label = { Text("Jumlah lembar") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Button(onClick = {
                    val c = (copies.toIntOrNull() ?: 1).coerceIn(1, 60)
                    printPdf(ctx, pdfFile(ctx, e, c), c, "LJK ${e.nama}")
                }) { Text("🖨 Cetak") }
                OutlinedButton(onClick = {
                    val c = (copies.toIntOrNull() ?: 1).coerceIn(1, 60)
                    share(ctx, pdfFile(ctx, e, c), "application/pdf", "Bagikan LJK (PDF)")
                }) { Text("⇪ Bagikan PDF") }
            }
        }
        Image(preview, contentDescription = "Pratinjau LJK", modifier = Modifier.fillMaxWidth().aspectRatio(210f / 297f).border(1.dp, Bd))
    }
}
