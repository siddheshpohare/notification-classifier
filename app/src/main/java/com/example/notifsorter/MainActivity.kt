package com.example.notifsorter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var listenerOn by mutableStateOf(false)
    private val askNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Alerts.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val store = Store.get(this)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    Screen(
                        store = store,
                        listenerOn = listenerOn,
                        onGrant = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                        onExport = { exportCsv(store) },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        listenerOn = NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)
    }

    /** CSV in the same columns the Colab notebook expects: app,title,text,label */
    private fun exportCsv(store: Store) {
        val rows = store.labeled()
        fun q(s: String) = "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\""
        val sb = StringBuilder("app,title,text,label\n")
        rows.forEach { sb.append("${q(it.app)},${q(it.title)},${q(it.body)},${it.userLabel}\n") }

        val dir = File(cacheDir, "exports").apply { mkdirs() }
        val f = File(dir, "notifications_labeled.csv").apply { writeText(sb.toString()) }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Export ${rows.size} labeled notifications"))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Screen(store: Store, listenerOn: Boolean, onGrant: () -> Unit, onExport: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun io(block: () -> Unit) { scope.launch(Dispatchers.IO) { block() } }

    val version by store.version.collectAsState()
    var all by remember { mutableStateOf(emptyList<Rec>()) }
    var tab by remember { mutableIntStateOf(0) }
    var silence by remember { mutableStateOf(Prefs.silenceEnabled(ctx)) }
    var popup by remember { mutableStateOf(Prefs.popupEnabled(ctx)) }
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(version) { all = withContext(Dispatchers.IO) { store.recent() } }

    val important = all.filter { it.isImportant }
    val silenced = all.filter { !it.isImportant }
    val shown = if (tab == 0) important else silenced

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear everything?") },
            text = { Text("Deletes all logged notifications and the rules learned from your corrections.") },
            confirmButton = {
                TextButton(onClick = { io { store.clearAll() }; confirmClear = false }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(36.dp))
        Text("Notification Sorter", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        if (!listenerOn) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Notification access is off. The app can't see notifications until you allow it.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onGrant) { Text("Open notification access settings") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        SwitchRow(
            "Pop up important notifications",
            if (popup) "ON: important ones also appear as a pop-up at the top of the screen"
            else "OFF: no extra pop-ups",
            popup,
        ) { popup = it; Prefs.setPopupEnabled(ctx, it) }

        SwitchRow(
            "Hide junk notifications",
            if (silence) "ON: notifications judged junk are removed from your shade"
            else "OFF (learning mode): nothing is hidden, everything is just labeled",
            silence,
        ) { silence = it; Prefs.setSilenceEnabled(ctx, it) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            OutlinedButton(onClick = {
                Alerts.post(ctx, "Test", "Important alert test", "This is how an important notification will pop up.", null)
            }) { Text("Test pop-up") }
            OutlinedButton(onClick = onExport) { Text("Export labels") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
            OutlinedButton(onClick = { io { Demo.add(store) } }) { Text("Add demo") }
            OutlinedButton(onClick = { io { store.clearDemo() } }) { Text("Clear demo") }
            OutlinedButton(onClick = { confirmClear = true }) { Text("Clear all") }
        }

        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Important (${important.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Silenced (${silenced.size})") })
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing here yet.")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(shown, key = { it.id }) { r ->
                    NotifCard(
                        r,
                        onCorrect = { label -> io { store.setUserLabel(r.id, label) } },
                        onDelete = { io { store.delete(r.id) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotifCard(r: Rec, onCorrect: (Int) -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()) }
    val tag = when {
        r.userLabel != null -> "you marked this"
        r.decision == Decision.UNSURE -> "unsure"
        r.hidden -> "hidden from shade"
        else -> null
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "${r.app} • ${fmt.format(Date(r.ts))}" + (tag?.let { " • $it" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(r.title, fontWeight = FontWeight.SemiBold)
            Text(r.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (r.isImportant) {
                    OutlinedButton(onClick = { onCorrect(0) }) { Text("Not important") }
                } else {
                    OutlinedButton(onClick = { onCorrect(1) }) { Text("This was important") }
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}