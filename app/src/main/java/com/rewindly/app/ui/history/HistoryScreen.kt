package com.rewindly.app.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.ReportType
import com.rewindly.app.ui.components.RCard
import com.rewindly.app.ui.theme.RColors
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale("es"))

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val reports by vm.reports.collectAsState()
    var toDelete by remember { mutableStateOf<Long?>(null) }
    val zone = vm.settings.zone()

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Biblioteca de informes", style = MaterialTheme.typography.headlineMedium)
        Text("Tus informes anteriores se conservan aunque importes datos nuevos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Todavía no has generado informes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else LazyColumn {
            items(reports, key = { it.id }) { r ->
                RCard(Modifier.padding(bottom = 10.dp).clickable { vm.openReport(r.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(runCatching { ReportType.valueOf(r.type).label }.getOrDefault(r.type).uppercase(), style = MaterialTheme.typography.labelSmall, color = RColors.Coral)
                            Text(r.title.substringAfter("· "), fontWeight = FontWeight.SemiBold)
                            Text("${r.personality} · creado ${Instant.ofEpochMilli(r.createdAt).atZone(zone).format(fmt)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { toDelete = r.id }) { Icon(Icons.Default.Delete, "Eliminar") }
                    }
                }
            }
        }
    }
    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar informe?") },
            text = { Text("Se borrará solo este informe; tus reproducciones se conservan.") },
            confirmButton = { TextButton(onClick = { vm.deleteReport(id); toDelete = null }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }
}
