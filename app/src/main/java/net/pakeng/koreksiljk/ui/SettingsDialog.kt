package net.pakeng.koreksiljk.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pakeng.koreksiljk.BuildConfig
import net.pakeng.koreksiljk.data.Updater
import net.pakeng.koreksiljk.omr.Thresholds
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsDialog(st: AppState) {
    val store = st.store
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            msg = try {
                val json = withContext(Dispatchers.IO) { store.backupJson() }
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
                "Cadangan tersimpan."
            } catch (e: Exception) { "Gagal: ${e.message}" }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            msg = try {
                val txt = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }
                val (ne, nr) = store.restore(txt)
                "Dipulihkan: $ne ujian, $nr hasil."
            } catch (e: Exception) { "Gagal memulihkan: ${e.message}" }
        }
    }

    Dialog(onDismissRequest = { st.settings = false }) {
        Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(14.dp)).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pengaturan", fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { st.settings = false }) { Text("✕") }
            }
            Text("Kepekaan pembacaan", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            val th = store.th
            Text("Batas tanda jawaban: ${"%.3f".format(th.tMark)} (naikkan bila kotak kosong/kotor terbaca bertanda; turunkan bila tanda tipis tidak terbaca)", fontSize = 13.sp, color = Mut)
            Slider(th.tMark.toFloat(), { store.th = th.copy(tMark = it.toDouble(), tEmpty = it * 0.55) }, valueRange = 0.03f..0.12f, onValueChangeFinished = { store.save() })
            Text("Batas garis coretan (pembatalan): ${"%.1f".format(th.tStrike)} (naikkan bila silang biasa terbaca dicoret; turunkan bila coretan terbaca sebagai jawaban)", fontSize = 13.sp, color = Mut)
            Slider(th.tStrike.toFloat(), { store.th = th.copy(tStrike = it.toDouble()) }, valueRange = 3f..9f, onValueChangeFinished = { store.save() })
            TextButton(onClick = { store.th = Thresholds(); store.save() }) { Text("Kembalikan default") }

            Text("Cadangan data", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            Text("Semua data tersimpan di HP ini. Buat cadangan berkala atau untuk pindah HP.", fontSize = 13.sp, color = Mut)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                OutlinedButton(onClick = { backup.launch("cadangan-koreksi-ljk-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".json") }) { Text("⬇ Cadangkan") }
                OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("⬆ Pulihkan") }
            }

            Text("Aplikasi", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
            Text("Versi ${BuildConfig.VERSION_NAME} · pembaruan otomatis dari ljk.pak-eng.net", fontSize = 13.sp, color = Mut)
            OutlinedButton(enabled = !checking, onClick = {
                checking = true
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Updater.check() } }
                    checking = false
                    r.onSuccess { info -> if (info != null) { st.update = info; st.settings = false } else msg = "Aplikasi sudah versi terbaru." }
                        .onFailure { msg = "Tidak bisa mengecek pembaruan (periksa internet)." }
                }
            }, modifier = Modifier.padding(top = 6.dp)) { Text(if (checking) "Mengecek…" else "Cek pembaruan") }
            msg?.let { Text(it, color = Info, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}
