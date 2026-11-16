package com.rewindly.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Button
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.Periods
import com.rewindly.app.ui.ReportType
import com.rewindly.app.ui.components.Hairline
import com.rewindly.app.ui.components.LinkText
import com.rewindly.app.ui.components.PrimaryButton
import com.rewindly.app.ui.components.SectionTitle
import com.rewindly.app.ui.theme.Serif
import com.rewindly.app.ui.theme.mono
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import java.time.LocalDate
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
private val todayFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale("es"))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, onGoImport: () -> Unit, onGoHistory: () -> Unit) {
    val count by vm.playCount.collectAsState()
    val min by vm.minTs.collectAsState()
    val max by vm.maxTs.collectAsState()
    val sources by vm.sourceCounts.collectAsState()
    val reports by vm.reports.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    val zone = vm.settings.zone()
    val m = mono

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("REWINDLY", style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp), color = m.fg, modifier = Modifier.weight(1f))
            Text(LocalDate.now(zone).format(todayFmt).uppercase(), style = MaterialTheme.typography.labelSmall, color = m.dim)
        }
        Spacer(Modifier.height(36.dp))

        if (count == 0) {
            Text(
                buildAnnotatedString { append("Tu música,\n"); withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append("rebobinada.") } },
                style = MaterialTheme.typography.displayLarge, color = m.fg
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Aún no hay reproducciones. Activa el registro en vivo o conecta Last.fm y empieza hoy; cuando llegue tu ZIP de Spotify se fusiona solo, sin duplicados.",
                style = MaterialTheme.typography.bodyLarge, color = m.dim
            )
            Spacer(Modifier.height(22.dp))
            PrimaryButton("Configurar mis datos", onGoImport, Modifier.fillMaxWidth())
        } else {
            Text("REPRODUCCIONES GUARDADAS", style = MaterialTheme.typography.labelSmall, color = m.dim)
            Spacer(Modifier.height(6.dp))
            Text("%,d".format(count).replace(',', '.'), style = MaterialTheme.typography.displayLarge.copy(fontSize = 84.sp, lineHeight = 80.sp), color = m.fg)
            if (min != null && max != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Instant.ofEpochMilli(min!!).atZone(zone).format(dateFmt)} — ${Instant.ofEpochMilli(max!!).atZone(zone).format(dateFmt)}".uppercase(),
                    style = MaterialTheme.typography.labelMedium, color = m.dim
                )
            }
            Spacer(Modifier.height(20.dp))
            Hairline()
            sources.sortedByDescending { it.count }.forEach { sc ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Sources.label(sc.source), style = MaterialTheme.typography.bodyMedium, color = m.fg, modifier = Modifier.weight(1f))
                    Text("%,d".format(sc.count).replace(',', '.'), style = MaterialTheme.typography.labelMedium, color = m.dim)
                }
                Hairline()
            }
        }

        SectionTitle("Informes", "Se generan al instante con lo que ya tienes importado.", "02")
        val rows = listOf(
            Triple<String, String, () -> Unit>("Semanal", "Últimos 7 días", { vm.generateReport(ReportType.WEEKLY) }),
            Triple<String, String, () -> Unit>("Quincenal", "Últimos 15 días", { vm.generateReport(ReportType.BIWEEKLY) }),
            Triple<String, String, () -> Unit>("Mensual", "Últimos 30 días", { vm.generateReport(ReportType.MONTHLY) }),
            Triple<String, String, () -> Unit>("Anual", "Tu año, sin esperar a diciembre", { vm.generateReport(ReportType.YEARLY) }),
            Triple<String, String, () -> Unit>("Toda la historia", "Desde tu primera reproducción", { vm.generateReport(ReportType.ALL) }),
            Triple<String, String, () -> Unit>("Personalizado", "Elige las fechas", { showPicker = true })
        )
        rows.forEachIndexed { i, (title, sub, action) ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth().clickable(onClick = action).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(i + 1), style = MaterialTheme.typography.labelSmall, color = m.faint, modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontFamily = Serif, fontSize = 24.sp, lineHeight = 26.sp, color = m.fg)
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = m.dim)
                }
                Text("→", style = MaterialTheme.typography.titleLarge, color = m.fg)
            }
        }

        if (reports.isNotEmpty()) {
            SectionTitle("Recientes", null, "03")
            reports.take(3).forEachIndexed { i, r ->
                if (i > 0) Hairline()
                Column(Modifier.fillMaxWidth().clickable { vm.openReport(r.id) }.padding(vertical = 12.dp)) {
                    Text(r.title, style = MaterialTheme.typography.titleSmall, color = m.fg)
                    Text(r.personality, style = MaterialTheme.typography.bodySmall, color = m.dim)
                }
            }
            LinkText("Ver biblioteca", onGoHistory)
        }

        Spacer(Modifier.height(32.dp))
        Hairline()
        Text(
            "SIN INICIO DE SESIÓN EN SPOTIFY · SIN SERVIDORES · TODO SE CALCULA EN ESTE TELÉFONO",
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 15.sp), color = m.faint, modifier = Modifier.padding(vertical = 14.dp)
        )
        Spacer(Modifier.height(12.dp))
    }

    if (showPicker) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null, onClick = {
                    showPicker = false
                    val range = Periods.customRange(state.selectedStartDateMillis!!, state.selectedEndDateMillis!!, zone)
                    vm.generateReport(ReportType.CUSTOM, range)
                }) { Text("Generar") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancelar") } }
        ) {
            DateRangePicker(state = state, modifier = Modifier.height(480.dp), title = { Text("Elige el período", Modifier.padding(16.dp)) })
        }
    }
}

