package com.rewindly.app.ui.importdata

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.live.MediaListenerService
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.ImportState
import com.rewindly.app.ui.components.RCard
import com.rewindly.app.ui.components.SectionTitle
import com.rewindly.app.ui.theme.RColors
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))
private val timeFmt = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale("es"))

@Composable
fun ImportScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val state by vm.importState.collectAsState()
    val batches by vm.batches.collectAsState()
    val recentLive by vm.recentLive.collectAsState()
    val zone = vm.settings.zone()

    // Revisar el permiso cada vez que la pantalla vuelve a primer plano
    var liveGranted by remember { mutableStateOf(MediaListenerService.hasPermission(context)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) liveGranted = MediaListenerService.hasPermission(context) }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFile(it) } }

    var user by remember { mutableStateOf(vm.settings.lastFmUser) }
    var key by remember { mutableStateOf(vm.settings.lastFmApiKey) }
    var auto by remember { mutableStateOf(vm.settings.lastFmAutoSync) }

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Tus datos", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Tres formas seguras de alimentar Rewindly. Ninguna pide tu contraseña de Spotify y puedes combinarlas: los duplicados se fusionan solos.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        StatusCard(state, onDismiss = vm::resetImportState)

        // ---------- 1. Registro en vivo ----------
        SectionTitle("1 · Registro en vivo", "Funciona desde hoy, sin esperar nada")
        RCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (liveGranted) Icons.Default.CheckCircle else Icons.Default.GraphicEq, null, tint = if (liveGranted) RColors.Teal else RColors.Coral)
                Spacer(Modifier.width(10.dp))
                Text(if (liveGranted) "Activo: registrando lo que suena en Spotify" else "Desactivado", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Rewindly lee la información pública de “reproduciendo ahora” que Spotify ya muestra en tu pantalla de bloqueo (título, artista, álbum y duración) y mide cuánto escuchas. Todo queda en el teléfono: no usa internet ni toca tu cuenta.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Extra opcional: en Spotify › Ajustes › Dispositivos, activa “Estado de transmisión del dispositivo” (Device Broadcast Status) como respaldo.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) {
                Text(if (liveGranted) "Administrar permiso" else "Activar registro en vivo")
            }
            if (recentLive.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Últimas capturas", style = MaterialTheme.typography.labelLarge)
                recentLive.take(5).forEach {
                    Text(
                        "${Instant.ofEpochMilli(it.timestampUtc).atZone(zone).format(timeFmt)} · ${it.trackName} — ${it.artistName}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                    )
                }
            }
        }

        // ---------- 2. Last.fm ----------
        SectionTitle("2 · Last.fm", "Historial en la nube, integración oficial de Spotify")
        RCard {
            Text(
                "Conecta Spotify a Last.fm desde la web de Last.fm (Ajustes › Aplicaciones › Spotify Scrobbling). Desde ese momento, todo lo que escuches en cualquier dispositivo queda registrado. Rewindly solo lee tu historial público con tu usuario de Last.fm y una API key gratuita.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = { open("https://www.last.fm/settings/applications") }) { Text("Conectar Spotify") }
                TextButton(onClick = { open("https://www.last.fm/api/account/create") }) { Text("Crear API key") }
            }
            OutlinedTextField(user, { user = it }, label = { Text("Usuario de Last.fm") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sincronizar cada 12 horas", Modifier.weight(1f))
                Switch(auto, { auto = it; vm.settings.lastFmAutoSync = it; vm.updateSchedules() })
            }
            Spacer(Modifier.height(8.dp))
            Button(
                enabled = user.isNotBlank() && key.isNotBlank() && state !is ImportState.Running,
                onClick = { vm.settings.lastFmUser = user; vm.settings.lastFmApiKey = key; vm.updateSchedules(); vm.syncLastFm() }
            ) {
                Icon(Icons.Default.Sync, null); Spacer(Modifier.width(8.dp)); Text("Sincronizar ahora")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Last.fm no informa cuánto escuchaste cada canción: Rewindly estima 3:30 por reproducción y lo marca como “Estimado”.",
                style = MaterialTheme.typography.bodySmall, color = RColors.Warning
            )
        }

        // ---------- 3. ZIP ----------
        SectionTitle("3 · Exportación oficial de Spotify", "La fuente más completa")
        RCard {
            Text(
                "En spotify.com › Cuenta › Privacidad, pide dos paquetes a la vez:",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("• Datos de la cuenta: suele llegar en unos 5 días y trae el último año.", style = MaterialTheme.typography.bodyMedium)
            Text("• Historial de reproducción extendido: puede tardar hasta 30 días y trae toda la vida de la cuenta, con saltos, aleatorio y álbum.", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Importa el primero apenas llegue y el segundo después: Rewindly los fusiona sin duplicar y reemplaza las capturas en vivo por los datos oficiales.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = { open("https://www.spotify.com/account/privacy/") }) { Text("Solicitar mis datos") }
            }
            Button(
                enabled = state !is ImportState.Running,
                onClick = { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream")) }
            ) {
                Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Importar ZIP o JSON")
            }
        }

        if (batches.isNotEmpty()) {
            SectionTitle("Historial de importaciones")
            batches.take(15).forEach { b ->
                RCard(Modifier.padding(bottom = 8.dp)) {
                    Text(Sources.label(b.source), fontWeight = FontWeight.SemiBold)
                    Text(
                        "${Instant.ofEpochMilli(b.createdAt).atZone(zone).format(fmt)} · ${b.inserted} nuevas · ${b.duplicates} duplicadas" + (b.fileName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusCard(state: ImportState, onDismiss: () -> Unit) {
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Running -> {
            Spacer(Modifier.height(12.dp))
            RCard {
                Text(state.message, fontWeight = FontWeight.SemiBold)
                if (state.processed > 0) Text("${state.processed} procesadas", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        is ImportState.Done -> {
            Spacer(Modifier.height(12.dp))
            RCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = RColors.Teal); Spacer(Modifier.width(8.dp))
                    Text("Importación completa", fontWeight = FontWeight.Bold)
                }
                val r = state.report
                Text("${r.inserted} reproducciones nuevas de ${r.read} leídas en ${r.filesFound} archivos.")
                Text("${r.duplicates} duplicadas ignoradas · ${r.replaced} capturas reemplazadas por datos oficiales · ${r.ignoredPodcasts} podcasts/vídeos omitidos.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                r.warnings.forEach { w ->
                    Row(Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Default.Warning, null, tint = RColors.Warning); Spacer(Modifier.width(6.dp))
                        Text(w, style = MaterialTheme.typography.bodySmall)
                    }
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { Text("Entendido") }
            }
        }
        is ImportState.LastFmDone -> {
            Spacer(Modifier.height(12.dp))
            RCard {
                Text("Last.fm sincronizado", fontWeight = FontWeight.Bold)
                Text("${state.inserted} scrobbles nuevos · ${state.duplicates} ya estaban (por ZIP o registro en vivo).")
                OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { Text("Entendido") }
            }
        }
        is ImportState.Error -> {
            Spacer(Modifier.height(12.dp))
            RCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp))
                    Text("No se pudo completar", fontWeight = FontWeight.Bold)
                }
                Text(state.message)
                OutlinedButton(onClick = onDismiss, modifier = Modifier.padding(top = 8.dp)) { Text("Cerrar") }
            }
        }
    }
}
