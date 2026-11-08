package net.pakeng.koreksiljk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pakeng.koreksiljk.data.Store
import net.pakeng.koreksiljk.data.Updater

/** Status bersama antar layar. */
class AppState(val store: Store, val snack: SnackbarHostState) {
    var detailId by mutableStateOf<String?>(null)
    var page by mutableStateOf("ujian")
    var editExamId by mutableStateOf<String?>(null)
    var editing by mutableStateOf(false)
    var settings by mutableStateOf(false)
    var update by mutableStateOf<Updater.Info?>(null)
}

@Composable
fun App(store: Store) {
    val snack = remember { SnackbarHostState() }
    val st = remember { AppState(store, snack) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (store.exams.isEmpty()) st.editing = true
        val info = withContext(Dispatchers.IO) { runCatching { Updater.check() }.getOrNull() }
        if (info != null) st.update = info
    }

    Scaffold(
        containerColor = Bg,
        topBar = { Header(st) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                listOf("ujian" to "📋\nUjian", "ljk" to "🖨\nLJK", "koreksi" to "📷\nKoreksi", "rekap" to "📊\nRekap", "analisis" to "🔎\nAnalisis").forEach { (k, l) ->
                    val (ic, lb) = l.split("\n")
                    NavigationBarItem(
                        selected = st.page == k,
                        onClick = { st.page = k },
                        icon = { Text(ic, fontSize = 20.sp) },
                        label = { Text(lb, fontSize = 11.sp) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (st.page) {
                "ujian" -> UjianScreen(st)
                "ljk" -> LjkScreen(st)
                "koreksi" -> KoreksiScreen(st)
                "rekap" -> RekapScreen(st)
                else -> AnalisisScreen(st)
            }
        }
    }

    st.detailId?.let { DetailDialog(st, it) }
    if (st.settings) SettingsDialog(st)

    // ---------- Dialog pembaruan ----------
    st.update?.let { info ->
        var prog by remember { mutableStateOf<Float?>(null) }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (prog == null) st.update = null },
            title = { Text("Versi baru ${info.versionName}") },
            text = {
                Column {
                    Text("Pembaruan aplikasi tersedia dari ljk.pak-eng.net.")
                    if (info.notes.isNotBlank()) Text(info.notes, color = Mut, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                    prog?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) }
                    err?.let { Text(it, color = No, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                TextButton(enabled = prog == null, onClick = {
                    if (!Updater.canInstall(ctx)) {
                        err = "Izinkan \"instal aplikasi tidak dikenal\" untuk Koreksi LJK, lalu tekan Perbarui lagi."
                        Updater.openInstallPermission(ctx)
                        return@TextButton
                    }
                    prog = 0f; err = null
                    scope.launch {
                        try {
                            val f = withContext(Dispatchers.IO) { Updater.download(ctx, info) { p -> prog = p } }
                            prog = null
                            Updater.install(ctx, f)
                            st.update = null
                        } catch (e: Exception) {
                            prog = null; err = "Gagal mengunduh: ${e.message}"
                        }
                    }
                }) { Text("Perbarui") }
            },
            dismissButton = { TextButton(enabled = prog == null, onClick = { st.update = null }) { Text("Nanti") } },
        )
    }
}

@Composable
private fun Header(st: AppState) {
    val store = st.store
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(Pri).statusBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓ Koreksi LJK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().background(Color(0x1FFFFFFF), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))) {
                Text(store.active?.let { "${it.nama} · ${it.kelas} ▾" } ?: "(belum ada ujian)", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                store.exams.forEach { e ->
                    DropdownMenuItem(text = { Text("${e.nama} · ${e.kelas}") }, onClick = { store.activeId = e.id; store.save(); open = false })
                }
            }
        }
        TextButton(onClick = { st.settings = true }) { Text("⚙", color = Color.White, fontSize = 20.sp) }
    }
}
