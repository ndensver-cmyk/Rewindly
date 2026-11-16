package com.rewindly.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.rewindly.app.ui.components.Hairline
import com.rewindly.app.ui.components.LinkText
import com.rewindly.app.ui.components.ScreenHeader
import com.rewindly.app.ui.components.SecondaryButton
import com.rewindly.app.ui.components.SectionTitle
import com.rewindly.app.ui.components.SegmentRow
import com.rewindly.app.ui.components.ToggleRow
import com.rewindly.app.ui.theme.mono
import java.time.ZoneId

private val commonZones = listOf(
    "America/Bogota", "America/Mexico_City", "America/Lima", "America/Santiago", "America/Argentina/Buenos_Aires",
    "America/Caracas", "America/New_York", "America/Los_Angeles", "Europe/Madrid", "Europe/London", "UTC"
)

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = vm.settings
    val m = mono
    val theme by vm.themeMode.collectAsState()
    var zone by remember { mutableStateOf(s.zoneId) }
    var zoneMenu by remember { mutableStateOf(false) }
    var gap by remember { mutableFloatStateOf(s.sessionGapMinutes.toFloat()) }
    var weekly by remember { mutableStateOf(s.weeklyAutoReport) }
    var monthly by remember { mutableStateOf(s.monthlyAutoReport) }
    var reminder by remember { mutableStateOf(s.exportReminder) }
    var live by remember { mutableStateOf(s.liveTrackingEnabled) }
    var confirmAll by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader("05", "Ajustes", "Tu", "configuración")

        SectionTitle("Apariencia", null, "01")
        SegmentRow(listOf("dark", "light", "system"), theme, { when (it) { "dark" -> "Negro"; "light" -> "Blanco"; else -> "Sistema" } }, { vm.setTheme(it) })

        SectionTitle("Análisis", null, "02")
        Text("ZONA HORARIA", style = MaterialTheme.typography.labelSmall, color = m.dim)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(zone, style = MaterialTheme.typography.titleMedium, color = m.fg, modifier = Modifier.weight(1f))
            Box {
                LinkText("Cambiar", { zoneMenu = true })
                DropdownMenu(zoneMenu, { zoneMenu = false }) {
                    (listOf(ZoneId.systemDefault().id) + commonZones).distinct().forEach { z ->
                        DropdownMenuItem(text = { Text(z) }, onClick = { zone = z; s.zoneId = z; zoneMenu = false; vm.refreshStats() })
                    }
                }
            }
        }
        Text("Las marcas de Spotify vienen en UTC; se convierten a esta zona para calcular horas y días.",
            style = MaterialTheme.typography.bodySmall, color = m.dim)
        Spacer(Modifier.height(18.dp))
        Text("CORTE DE SESIÓN · ${gap.toInt()} MIN", style = MaterialTheme.typography.labelSmall, color = m.dim)
        Slider(
            gap, { gap = (it / 5).toInt() * 5f }, valueRange = 10f..120f, onValueChangeFinished = { s.sessionGapMinutes = gap.toInt(); vm.refreshStats() },
            colors = SliderDefaults.colors(thumbColor = m.fg, activeTrackColor = m.fg, inactiveTrackColor = m.line, activeTickColor = m.bg, inactiveTickColor = m.faint)
        )

        SectionTitle("Automático", null, "03")
        ToggleRow("Informe semanal", "Cada lunes se guarda el resumen de la semana anterior.", weekly) { weekly = it; s.weeklyAutoReport = it; vm.autoChanged() }
        Hairline()
        ToggleRow("Informe mensual", "Cada día 1 se guarda el resumen del mes anterior.", monthly) { monthly = it; s.monthlyAutoReport = it; vm.autoChanged() }
        Hairline()
        ToggleRow("Recordatorio de exportación", "El día 1 de cada mes, si no hay una solicitud en curso.", reminder) { reminder = it; s.exportReminder = it; vm.autoChanged() }
        Hairline()
        ToggleRow("Registro en vivo", "Guardar lo que suena en Spotify en este teléfono.", live) { live = it; s.liveTrackingEnabled = it }

        SectionTitle("Tus datos", null, "04")
        listOf(Sources.LIVE, Sources.LASTFM, Sources.ZIP_ACCOUNT, Sources.ZIP_EXTENDED).forEachIndexed { i, src ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Sources.label(src), style = MaterialTheme.typography.bodyMedium, color = m.fg, modifier = Modifier.weight(1f))
                LinkText("Borrar", { vm.deleteSource(src) })
            }
        }
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Eliminar todo el historial e informes", { confirmAll = true }, Modifier.fillMaxWidth())

        SectionTitle("Privacidad", null, "05")
        listOf(
            "No pide contraseña ni correo de Spotify y no usa OAuth.",
            "No accede a cookies, sesiones ni endpoints privados.",
            "No modifica listas ni reproducciones.",
            "No envía tu historial a servidores ni a servicios de IA.",
            "Descarta IP, usuario, país de conexión y agente del navegador al importar.",
            "La búsqueda automática solo abre archivos con nombre de exportación de Spotify.",
            "Los datos viven en el almacenamiento privado de la app y se excluyen de las copias automáticas.",
            "El único acceso a internet es opcional: leer tu perfil público de Last.fm."
        ).forEachIndexed { i, t ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Text("%02d".format(i + 1), style = MaterialTheme.typography.labelSmall, color = m.faint, modifier = Modifier.width(30.dp).padding(top = 3.dp))
                Text(t, style = MaterialTheme.typography.bodyMedium, color = m.fg)
            }
        }

        SectionTitle("Aviso", null, "06")
        Text(
            "Rewindly es un proyecto independiente. No está afiliado, patrocinado ni aprobado por Spotify AB. “Spotify” es una marca de su titular y se menciona solo para describir la compatibilidad con los archivos que tú mismo exportas.",
            style = MaterialTheme.typography.bodySmall, color = m.dim
        )
        Text("REWINDLY 1.1", style = MaterialTheme.typography.labelSmall, color = m.faint, modifier = Modifier.padding(top = 14.dp))
        Spacer(Modifier.height(30.dp))
    }

    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("¿Eliminar todo?") },
        text = { Text("Se borrarán todas las reproducciones importadas, el registro en vivo y los informes guardados. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirmAll = false }) { Text("Eliminar", color = m.fg) } },
        dismissButton = { TextButton(onClick = { confirmAll = false }) { Text("Cancelar", color = m.dim) } },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
    )
}
