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
import com.rewindly.app.ui.components.GradientCard
import com.rewindly.app.ui.components.RCard
import com.rewindly.app.ui.components.SectionTitle
import com.rewindly.app.ui.theme.RColors
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("REWINDLY", style = MaterialTheme.typography.labelSmall, color = RColors.Coral)
        Text("Tu música.\nTu historia.", style = MaterialTheme.typography.displaySmall, lineHeight = 40.sp)
        Spacer(Modifier.height(16.dp))

        GradientCard(listOf(Color(0xFF3B1D6E), Color(0xFF8B2F6B), RColors.Coral)) {
            if (count == 0) {
                Text("Aún no hay música", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Empieza hoy mismo: activa el registro en vivo o conecta Last.fm. Cuando llegue tu ZIP de Spotify, lo fusionamos sin duplicar.",
                    color = Color.White.copy(0.9f)
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onGoImport) { Text("Añadir mis datos") }
            } else {
                Text("%,d".format(count).replace(',', '.'), color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Black)
                Text("reproducciones guardadas en este teléfono", color = Color.White.copy(0.9f))
                Spacer(Modifier.height(8.dp))
                if (min != null && max != null) {
                    Text(
                        "Del ${Instant.ofEpochMilli(min!!).atZone(zone).format(dateFmt)} al ${Instant.ofEpochMilli(max!!).atZone(zone).format(dateFmt)}",
                        color = Color.White.copy(0.85f), style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(sources.joinToString(" · ") { "${Sources.label(it.source)}: ${it.count}" }, color = Color.White.copy(0.75f), style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionTitle("Tus informes musicales", "Se generan con los datos que ya tienes importados.")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportTile("Semanal", "7 días", "Resumen de la semana", Icons.Default.ViewWeek, RColors.Violet, Modifier.weight(1f)) { vm.generateReport(ReportType.WEEKLY) }
            ReportTile("Quincenal", "15 días", "Dos semanas de música", Icons.Default.Today, RColors.Teal, Modifier.weight(1f)) { vm.generateReport(ReportType.BIWEEKLY) }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportTile("Mensual", "30 días", "Tu informe mensual", Icons.Default.CalendarMonth, RColors.Coral, Modifier.weight(1f)) { vm.generateReport(ReportType.MONTHLY) }
            ReportTile("Personalizado", "Libre", "Elige las fechas", Icons.Default.DateRange, RColors.Amber, Modifier.weight(1f)) { showPicker = true }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReportTile("Anual", "365 días", "Tu año, sin esperar a diciembre", Icons.Default.AutoAwesome, RColors.Rose, Modifier.weight(1f)) { vm.generateReport(ReportType.YEARLY) }
            ReportTile("Todo", "Historia", "Toda tu vida musical", Icons.Default.AutoAwesome, RColors.Sky, Modifier.weight(1f)) { vm.generateReport(ReportType.ALL) }
        }

        if (reports.isNotEmpty()) {
            SectionTitle("Últimos informes")
            reports.take(3).forEach { r ->
                RCard(Modifier.padding(bottom = 8.dp).clickable { vm.openReport(r.id) }) {
                    Text(r.title, fontWeight = FontWeight.SemiBold)
                    Text(r.personality, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            TextButton(onClick = onGoHistory) { Text("Ver biblioteca de informes") }
        }

        SectionTitle("Privado por diseño")
        RCard {
            Text("Rewindly no inicia sesión en Spotify, no pide contraseñas y no envía tu historial a ningún servidor. Todo se calcula en este teléfono.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
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

@Composable
private fun ReportTile(title: String, big: String, sub: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = accent)
            Spacer(Modifier.height(8.dp))
            Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = accent)
            Text(big, style = MaterialTheme.typography.headlineSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
