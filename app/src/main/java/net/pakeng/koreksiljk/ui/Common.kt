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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
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

fun loadThumb(file: File, maxW: Int = 160): Bitmap? {
    if (!file.exists()) return null
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, o)
    var s = 1
    while (o.outWidth / (s * 2) >= maxW) s *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })
}
