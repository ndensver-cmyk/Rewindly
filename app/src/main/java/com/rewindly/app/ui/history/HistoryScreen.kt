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
import androidx.compose.material.icons.outlined.Delete
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
import com.rewindly.app.ui.components.Hairline
import com.rewindly.app.ui.components.ScreenHeader
import com.rewindly.app.ui.theme.Serif
import com.rewindly.app.ui.theme.mono
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val reports by vm.reports.collectAsState()
    var toDelete by remember { mutableStateOf<Long?>(null) }
    val zone = vm.settings.zone()

    val m = mono
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenHeader("04", "Biblioteca", "Tus", "informes", "Se conservan aunque importes datos nuevos. Tócalos para ver sus historias.")
        Spacer(Modifier.height(12.dp))
        Hairline()
        if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Todavía no hay informes.", style = MaterialTheme.typography.bodyLarge, color = m.dim)
            }
        } else LazyColumn {
            items(reports, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().clickable { vm.openReport(r.id) }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(runCatching { ReportType.valueOf(r.type).label }.getOrDefault(r.type).uppercase() + "  ·  " +
                            Instant.ofEpochMilli(r.createdAt).atZone(zone).format(fmt).uppercase(), style = MaterialTheme.typography.labelSmall, color = m.dim)
                        Spacer(Modifier.height(4.dp))
                        Text(r.title.substringAfter("· "), fontFamily = Serif, fontSize = 24.sp, lineHeight = 26.sp, color = m.fg)
                        Text(r.personality, style = MaterialTheme.typography.bodySmall, color = m.dim)
                    }
                    IconButton(onClick = { toDelete = r.id }) { Icon(Icons.Outlined.Delete, "Eliminar", tint = m.faint) }
                }
                Hairline()
            }
        }
    }
    toDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Eliminar informe?") },
            text = { Text("Se borrará solo este informe; tus reproducciones se conservan.") },
            confirmButton = { TextButton(onClick = { vm.deleteReport(id); toDelete = null }) { Text("Eliminar", color = m.fg) } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar", color = m.dim) } },
            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
        )
    }
}
