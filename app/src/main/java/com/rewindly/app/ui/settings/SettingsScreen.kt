package com.rewindly.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.components.RCard
import com.rewindly.app.ui.components.SectionTitle
import java.time.ZoneId

private val commonZones = listOf(
    "America/Bogota", "America/Mexico_City", "America/Lima", "America/Santiago", "America/Argentina/Buenos_Aires",
    "America/Caracas", "America/New_York", "America/Los_Angeles", "Europe/Madrid", "Europe/London", "UTC"
)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    var zone by remember { mutableStateOf(s.zoneId) }
    var zoneMenu by remember { mutableStateOf(false) }
    var gap by remember { mutableFloatStateOf(s.sessionGapMinutes.toFloat()) }
    var weekly by remember { mutableStateOf(s.weeklyAutoReport) }
    var reminder by remember { mutableStateOf(s.exportReminder) }
    var live by remember { mutableStateOf(s.liveTrackingEnabled) }
    var confirmAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Ajustes", style = MaterialTheme.typography.headlineMedium)

        SectionTitle("Análisis")
        RCard {
            Text("Zona horaria", style = MaterialTheme.typography.labelLarge)
            Text("Las marcas de Spotify vienen en UTC; se convierten a esta zona para calcular horas y días.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(zone, Modifier.weight(1f))
                TextButton(onClick = { zoneMenu = true }) { Text("Cambiar") }
                DropdownMenu(zoneMenu, { zoneMenu = false }) {
                    (listOf(ZoneId.systemDefault().id) + commonZones).distinct().forEach { z ->
                        DropdownMenuItem(text = { Text(z) }, onClick = { zone = z; s.zoneId = z; zoneMenu = false; vm.refreshStats() })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Separar sesiones tras ${gap.toInt()} minutos sin actividad", style = MaterialTheme.typography.labelLarge)
            Slider(gap, { gap = it }, valueRange = 10f..120f, steps = 21, onValueChangeFinished = { s.sessionGapMinutes = gap.toInt(); vm.refreshStats() })
        }

        SectionTitle("Calendario y avisos")
        RCard {
            Toggle("Informe semanal automático", "Cada semana se genera y se guarda tu resumen.", weekly) { weekly = it; s.weeklyAutoReport = it; vm.updateSchedules() }
            Toggle("Recordatorio mensual de exportación", "Te avisa para pedir un ZIP nuevo a Spotify.", reminder) { reminder = it; s.exportReminder = it; vm.updateSchedules() }
            Toggle("Registro en vivo", "Guardar lo que suena en Spotify en este teléfono.", live) { live = it; s.liveTrackingEnabled = it }
        }

        SectionTitle("Tus datos")
        RCard {
            listOf(Sources.LIVE, Sources.LASTFM, Sources.ZIP_ACCOUNT, Sources.ZIP_EXTENDED).forEach { src ->
                OutlinedButton(onClick = { vm.deleteSource(src) }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text("Borrar datos de ${Sources.label(src)}")
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { confirmAll = true }, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Eliminar todo el historial e informes") }
        }

        SectionTitle("Privacidad y seguridad")
        RCard {
            listOf(
                "No pide contraseña ni correo de Spotify y no usa OAuth.",
                "No accede a cookies, sesiones ni endpoints privados.",
                "No modifica listas ni reproducciones.",
                "No envía tu historial a servidores ni a servicios de IA.",
                "Descarta IP, usuario, país de conexión y agente del navegador al importar.",
                "Los datos viven en el almacenamiento privado de la app y se excluyen de las copias automáticas.",
                "El único acceso a internet es opcional: leer tu perfil público de Last.fm."
            ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp)) }
        }

        SectionTitle("Aviso")
        RCard {
            Text(
                "Rewindly es un proyecto independiente. No está afiliado, patrocinado ni aprobado por Spotify AB. “Spotify” es una marca de su titular y se menciona solo para describir la compatibilidad con los archivos que tú mismo exportas.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Versión 1.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
        Spacer(Modifier.height(30.dp))
    }

    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("¿Eliminar todo?") },
        text = { Text("Se borrarán todas las reproducciones importadas, el registro en vivo y los informes guardados. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmAll = false }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancelar") } }
    )
}

@Composable
private fun Toggle(title: String, sub: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value, onChange)
    }
}
