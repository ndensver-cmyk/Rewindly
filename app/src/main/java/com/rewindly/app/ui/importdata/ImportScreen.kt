package com.rewindly.app.ui.importdata

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.importer.AutoImport
import com.rewindly.app.live.MediaListenerService
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.ImportState
import com.rewindly.app.ui.components.Hairline
import com.rewindly.app.ui.components.InverseBlock
import com.rewindly.app.ui.components.LinkText
import com.rewindly.app.ui.components.PrimaryButton
import com.rewindly.app.ui.components.RCard
import com.rewindly.app.ui.components.ScreenHeader
import com.rewindly.app.ui.components.SecondaryButton
import com.rewindly.app.ui.components.SectionTitle
import com.rewindly.app.ui.components.StatusDot
import com.rewindly.app.ui.components.ToggleRow
import com.rewindly.app.ui.theme.MonoFont
import com.rewindly.app.ui.theme.Serif
import com.rewindly.app.ui.theme.mono
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
    val tick by vm.autoTick.collectAsState()
    val zone = vm.settings.zone()
    val s = vm.settings
    val m = mono

    // Se revisan los permisos cada vez que la pantalla vuelve a primer plano
    var resumeCount by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeCount++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val liveGranted = remember(resumeCount) { MediaListenerService.hasPermission(context) }
    val filesGranted = remember(resumeCount) { AutoImport.hasDownloadsAccess(context) }
    var scanDownloads by remember { mutableStateOf(s.scanDownloads) }
    var tree by remember { mutableStateOf(s.watchedTreeUri) }
    val requestedAt = remember(tick) { s.spotifyRequestedAt }
    val lastScan = remember(tick, resumeCount) { s.lastAutoScanAt }

    // Si el usuario volvió de conceder "acceso a archivos", activar la búsqueda
    LaunchedEffect(filesGranted) {
        if (filesGranted && scanDownloads) { vm.autoChanged(); vm.scanNow() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { vm.importFile(it) } }
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        s.watchedTreeUri = uri.toString(); tree = uri.toString()
        vm.autoChanged(); vm.scanNow()
    }
    val legacyPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { vm.autoChanged(); vm.scanNow() }
    }

    var user by remember { mutableStateOf(s.lastFmUser) }
    var key by remember { mutableStateOf(s.lastFmApiKey) }
    var auto by remember { mutableStateOf(s.lastFmAutoSync) }
    val lastFmReady = s.lastFmUser.isNotBlank() && s.lastFmApiKey.isNotBlank()

    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    fun requestFilesAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else legacyPerm.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenHeader(
            "02", "Fuentes", "Tus", "datos",
            "Tres formas seguras de alimentar Rewindly. Ninguna pide tu contraseña de Spotify y se combinan solas: los duplicados se fusionan."
        )

        StatusCard(state, onDismiss = vm::resetImportState)

        // ---------- Panel de automatización ----------
        SectionTitle("Piloto automático", "Configúralo una vez y Rewindly se alimenta solo.", "A")
        Column(Modifier.fillMaxWidth().border(1.dp, m.line, RoundedCornerShape(2.dp))) {
            AutoRow("Registro en vivo", if (liveGranted) "Activo · cada canción que suena" else "Requiere acceso a notificaciones", liveGranted)
            Hairline()
            AutoRow("Last.fm", when {
                !lastFmReady -> "Sin configurar"
                !auto -> "Configurado · sincronización manual"
                else -> "Cada 3 h y al abrir la app" + (s.lastFmSyncedAt.takeIf { it > 0 }?.let { " · última ${ago(it)}" } ?: "")
            }, lastFmReady && auto)
            Hairline()
            val autoZip = (scanDownloads && filesGranted) || tree != null
            AutoRow("Detección del ZIP", if (autoZip) "Revisa cada hora" + (lastScan.takeIf { it > 0 }?.let { " · última ${ago(it)}" } ?: "") else "Desactivada", autoZip)
            Hairline()
            val days = if (requestedAt > 0) ((System.currentTimeMillis() - requestedAt) / 86_400_000L).toInt() else -1
            AutoRow("Solicitud a Spotify", if (days >= 0) "Día $days · te avisamos a los 5 y 30 días" else "No registrada", days >= 0)
            Hairline()
            AutoRow("Informes", "Semanal cada lunes · mensual cada día 1", s.weeklyAutoReport || s.monthlyAutoReport)
        }

        // ---------- 1. Registro en vivo ----------
        SectionTitle("Registro en vivo", "Funciona desde hoy, sin esperar nada.", "01")
        Text(
            "Rewindly lee la información de “reproduciendo ahora” que Spotify ya muestra en tu pantalla de bloqueo (título, artista, álbum y duración) y mide cuánto escuchas. No usa internet ni toca tu cuenta. Tras reiniciar el teléfono, Android lo vuelve a activar solo.",
            style = MaterialTheme.typography.bodyMedium, color = m.dim
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Respaldo opcional: en Spotify › Ajustes › Dispositivos, activa “Device Broadcast Status”.",
            style = MaterialTheme.typography.bodySmall, color = m.faint
        )
        Spacer(Modifier.height(14.dp))
        if (liveGranted) SecondaryButton("Administrar permiso", { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        else PrimaryButton("Activar registro en vivo", { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, Modifier.fillMaxWidth())
        if (recentLive.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("ÚLTIMAS CAPTURAS", style = MaterialTheme.typography.labelSmall, color = m.dim)
            Spacer(Modifier.height(6.dp))
            recentLive.take(5).forEach {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(Instant.ofEpochMilli(it.timestampUtc).atZone(zone).format(timeFmt), style = MaterialTheme.typography.labelSmall, color = m.faint, modifier = Modifier.width(92.dp))
                    Text("${it.trackName} — ${it.artistName}", style = MaterialTheme.typography.bodySmall, color = m.fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        // ---------- 2. Last.fm ----------
        SectionTitle("Last.fm", "Historial en la nube con la integración oficial de Spotify.", "02")
        Text(
            "Conecta Spotify a Last.fm (Ajustes › Aplicaciones › Spotify Scrobbling). Desde ese momento, todo lo que escuches en cualquier dispositivo queda registrado. Rewindly solo lee tu historial público con tu usuario y una API key gratuita.",
            style = MaterialTheme.typography.bodyMedium, color = m.dim
        )
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            LinkText("Conectar Spotify", { open("https://www.last.fm/settings/applications") })
            LinkText("Crear API key", { open("https://www.last.fm/api/account/create") })
        }
        MonoField(user, { user = it }, "Usuario de Last.fm")
        Spacer(Modifier.height(8.dp))
        MonoField(key, { key = it }, "API key", secret = true)
        ToggleRow("Sincronizar automáticamente", "Cada 3 horas con conexión y cada vez que abres la app.", auto) {
            auto = it; s.lastFmAutoSync = it; vm.autoChanged()
        }
        PrimaryButton(
            "Guardar y sincronizar", {
                s.lastFmUser = user; s.lastFmApiKey = key; vm.autoChanged(); vm.syncLastFm()
            }, Modifier.fillMaxWidth(), enabled = user.isNotBlank() && key.isNotBlank() && state !is ImportState.Running, icon = Icons.Default.Sync
        )
        Spacer(Modifier.height(8.dp))
        Text("Last.fm no informa cuánto escuchaste cada canción: se estiman 3:30 por reproducción y se marca como ESTIMADO.",
            style = MaterialTheme.typography.bodySmall, color = m.faint)

        // ---------- 3. Exportación oficial ----------
        SectionTitle("Exportación de Spotify", "La fuente más completa. Pídela una vez y deja que llegue sola.", "03")
        Step("1", "Pide los dos paquetes a la vez en spotify.com › Cuenta › Privacidad: “Datos de la cuenta” (≈5 días, último año) e “Historial extendido” (hasta 30 días, toda la vida de la cuenta).")
        LinkText("Abrir privacidad de Spotify", { open("https://www.spotify.com/account/privacy/") })
        ToggleRow(
            "Ya lo pedí",
            if (requestedAt > 0) "Registrado el ${Instant.ofEpochMilli(requestedAt).atZone(zone).format(fmt)}. Te avisaremos cuando debería llegar."
            else "Marca esto para recibir avisos a los 5 y a los 30 días.",
            requestedAt > 0
        ) { vm.markRequested(it) }
        if (requestedAt > 0) {
            val days = ((System.currentTimeMillis() - requestedAt) / 86_400_000f).coerceIn(0f, 30f)
            Timeline(days)
        }
        Spacer(Modifier.height(12.dp))
        Step("2", "Cuando llegue el correo, descarga el ZIP. Rewindly lo detecta y lo importa solo — o tócalo en el correo y elige “Abrir con Rewindly”.")
        ToggleRow(
            "Buscar en Descargas",
            if (filesGranted) "Revisa la carpeta Descargas cada hora y al abrir la app. Solo abre archivos con nombre de exportación de Spotify."
            else "Necesita “acceso a archivos”. Solo se leen archivos con nombre de exportación de Spotify.",
            scanDownloads && filesGranted
        ) { on ->
            scanDownloads = on; s.scanDownloads = on
            if (on && !filesGranted) requestFilesAccess() else { vm.autoChanged(); if (on) vm.scanNow() }
        }
        ToggleRow(
            "Vigilar otra carpeta",
            tree?.let { "Carpeta: ${AutoImport.treeLabel(it)}" } ?: "Alternativa sin acceso global: elige una carpeta (p. ej. Download/Spotify).",
            tree != null
        ) { on ->
            if (on) treePicker.launch(null) else {
                tree?.let { runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                s.watchedTreeUri = null; tree = null; vm.autoChanged()
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Buscar ahora", { vm.scanNow() }, Modifier.weight(1f), enabled = ((scanDownloads && filesGranted) || tree != null) && state !is ImportState.Running)
            PrimaryButton(
                "Elegir archivo", { picker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream")) },
                Modifier.weight(1f), enabled = state !is ImportState.Running, icon = Icons.Default.FileOpen
            )
        }

        if (batches.isNotEmpty()) {
            SectionTitle("Historial de importaciones", null, "04")
            batches.take(15).forEachIndexed { i, b ->
                if (i > 0) Hairline()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row {
                        Text(Sources.label(b.source), style = MaterialTheme.typography.titleSmall, color = m.fg, modifier = Modifier.weight(1f))
                        Text(Instant.ofEpochMilli(b.createdAt).atZone(zone).format(fmt).uppercase(), style = MaterialTheme.typography.labelSmall, color = m.faint)
                    }
                    Text("${b.inserted} nuevas · ${b.duplicates} duplicadas" + (b.fileName?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = m.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun ago(ts: Long): String {
    val min = (System.currentTimeMillis() - ts) / 60_000
    return when {
        min < 1 -> "ahora"
        min < 60 -> "hace $min min"
        min < 1440 -> "hace ${min / 60} h"
        else -> "hace ${min / 1440} d"
    }
}

@Composable
private fun AutoRow(title: String, sub: String, active: Boolean) {
    val m = mono
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(active)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = m.fg)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = m.dim)
        }
        Text(if (active) "ON" else "OFF", style = MaterialTheme.typography.labelSmall, color = if (active) m.fg else m.faint)
    }
}

@Composable
private fun Step(n: String, text: String) {
    val m = mono
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(n, fontFamily = Serif, fontSize = 28.sp, lineHeight = 28.sp, color = m.fg, modifier = Modifier.width(30.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = m.dim)
    }
}

/** Línea de tiempo de 30 días con hitos en el día 5 y el 30. */
@Composable
private fun Timeline(days: Float) {
    val m = mono
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(18.dp)) {
            val y = size.height / 2
            drawRect(m.line, androidx.compose.ui.geometry.Offset(0f, y - 1f), androidx.compose.ui.geometry.Size(size.width, 2f))
            drawRect(m.fg, androidx.compose.ui.geometry.Offset(0f, y - 1.5f), androidx.compose.ui.geometry.Size(size.width * days / 30f, 3f))
            listOf(0f, 5f, 30f).forEach { d ->
                val x = (size.width * d / 30f).coerceIn(4f, size.width - 4f)
                val reached = days >= d
                drawRect(if (reached) m.fg else m.bg, androidx.compose.ui.geometry.Offset(x - 6f, y - 6f), androidx.compose.ui.geometry.Size(12f, 12f))
                if (!reached) drawRect(m.faint, androidx.compose.ui.geometry.Offset(x - 6f, y - 6f), androidx.compose.ui.geometry.Size(12f, 12f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("HOY · DÍA ${days.toInt()} DE 30", style = MaterialTheme.typography.labelSmall, color = m.fg)
        Text("Hitos: día 5, datos de la cuenta · día 30, historial extendido.", style = MaterialTheme.typography.bodySmall, color = m.dim)
    }
}

@Composable
private fun MonoField(value: String, onChange: (String) -> Unit, label: String, secret: Boolean = false) {
    val m = mono
    OutlinedTextField(
        value, onChange, label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(2.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MonoFont, fontSize = 15.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (secret) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = m.fg, unfocusedBorderColor = m.line, focusedLabelColor = m.fg, unfocusedLabelColor = m.dim, cursorColor = m.fg
        )
    )
}

@Composable
private fun StatusCard(state: ImportState, onDismiss: () -> Unit) {
    val m = mono
    when (state) {
        ImportState.Idle -> Unit
        is ImportState.Running -> {
            Spacer(Modifier.height(16.dp))
            RCard {
                Text(state.message.uppercase(), style = MaterialTheme.typography.labelMedium, color = m.fg)
                if (state.processed > 0) Text("%,d procesadas".format(state.processed).replace(',', '.'), style = MaterialTheme.typography.bodySmall, color = m.dim)
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = m.fg, trackColor = m.line)
            }
        }
        is ImportState.Done -> {
            Spacer(Modifier.height(16.dp))
            InverseBlock {
                val r = state.report
                Text("IMPORTACIÓN COMPLETA", style = MaterialTheme.typography.labelSmall, color = m.bg.copy(0.7f))
                Text("+${"%,d".format(r.inserted).replace(',', '.')}", fontFamily = Serif, fontSize = 48.sp, lineHeight = 50.sp, color = m.bg)
                Text("reproducciones nuevas de ${r.read} leídas en ${r.filesFound} archivo(s).", style = MaterialTheme.typography.bodyMedium, color = m.bg)
                Spacer(Modifier.height(6.dp))
                Text("${r.duplicates} duplicadas ignoradas · ${r.replaced} capturas reemplazadas por datos oficiales · ${r.ignoredPodcasts} podcasts/vídeos omitidos.",
                    style = MaterialTheme.typography.bodySmall, color = m.bg.copy(0.75f))
                r.warnings.forEach { w -> Text("— $w", style = MaterialTheme.typography.bodySmall, color = m.bg, modifier = Modifier.padding(top = 6.dp)) }
                Text("ENTENDIDO  ×", style = MaterialTheme.typography.labelMedium, color = m.bg,
                    modifier = Modifier.padding(top = 12.dp).clickable(onClick = onDismiss))
            }
        }
        is ImportState.LastFmDone -> {
            Spacer(Modifier.height(16.dp))
            InverseBlock {
                Text("LAST.FM SINCRONIZADO", style = MaterialTheme.typography.labelSmall, color = m.bg.copy(0.7f))
                Text("+${state.inserted}", fontFamily = Serif, fontSize = 48.sp, lineHeight = 50.sp, color = m.bg)
                Text("scrobbles nuevos · ${state.duplicates} ya estaban por ZIP o registro en vivo.", style = MaterialTheme.typography.bodyMedium, color = m.bg)
                Text("ENTENDIDO  ×", style = MaterialTheme.typography.labelMedium, color = m.bg,
                    modifier = Modifier.padding(top = 12.dp).clickable(onClick = onDismiss))
            }
        }
        is ImportState.Error -> {
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().border(1.5.dp, m.fg, RoundedCornerShape(2.dp)).padding(16.dp)) {
                Text("NO SE PUDO COMPLETAR", style = MaterialTheme.typography.labelMedium, color = m.fg)
                Spacer(Modifier.height(4.dp))
                Text(state.message, style = MaterialTheme.typography.bodyMedium, color = m.fg)
                Text("CERRAR  ×", style = MaterialTheme.typography.labelMedium, color = m.fg,
                    modifier = Modifier.padding(top = 12.dp).clickable(onClick = onDismiss))
            }
        }
    }
}
