package com.rewindly.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewindly.app.ui.theme.mono
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.rewindly.app.stories.StoryViewer
import com.rewindly.app.ui.AppViewModel
import com.rewindly.app.ui.history.HistoryScreen
import com.rewindly.app.ui.home.HomeScreen
import com.rewindly.app.ui.importdata.ImportScreen
import com.rewindly.app.ui.settings.SettingsScreen
import com.rewindly.app.ui.statistics.StatisticsScreen
import com.rewindly.app.ui.theme.RewindlyTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleShareIntent(intent)

        setContent {
            val themeMode by vm.themeMode.collectAsState()
            val sysDark = androidx.compose.foundation.isSystemInDarkTheme()
            LaunchedEffect(themeMode, sysDark) {
                val dark = when (themeMode) { "light" -> false; "system" -> sysDark; else -> true }
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            RewindlyTheme(themeMode) {
                var tab by rememberSaveable { mutableIntStateOf(0) }
                val story by vm.story.collectAsState()
                val message by vm.message.collectAsState()
                val snackbar = remember { SnackbarHostState() }

                LaunchedEffect(message) {
                    message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
                }

                Box(Modifier.fillMaxSize()) {
                    Scaffold(
                        containerColor = MaterialTheme.colorScheme.background,
                        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it, containerColor = mono.fg, contentColor = mono.bg, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp)) } },
                        bottomBar = {
                            BottomTabs(tab) { tab = it }
                        }
                    ) { padding ->
                        Box(Modifier.padding(padding)) {
                            when (tab) {
                                0 -> HomeScreen(vm, onGoImport = { tab = 1 }, onGoHistory = { tab = 3 })
                                1 -> ImportScreen(vm)
                                2 -> StatisticsScreen(vm)
                                3 -> HistoryScreen(vm)
                                else -> SettingsScreen(vm)
                            }
                        }
                    }
                    AnimatedVisibility(story != null, enter = fadeIn() + scaleIn(initialScale = 0.96f), exit = fadeOut()) {
                        story?.let { st ->
                            BackHandler { vm.closeStory() }
                            StoryViewer(st.scenes, onClose = vm::closeStory, onExportPng = vm::exportPng, onExportPdf = vm::exportPdf)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShareIntent(intent)
    }

    /** Permite abrir el ZIP directamente desde el gestor de archivos o "Compartir con Rewindly". */
    private fun handleShareIntent(intent: Intent?) {
        intent ?: return
        val uri: Uri? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            else -> null
        }
        uri?.let { vm.importFile(it) }
    }

    override fun onResume() {
        super.onResume()
        vm.onAppForeground()
    }
}

private val tabs = listOf("Inicio", "Datos", "Stats", "Informes", "Ajustes")

/** Barra inferior tipográfica: número + nombre, filete superior en la pestaña activa. */
@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    val m = mono
    Column(Modifier.fillMaxWidth().background(m.bg).navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(m.line))
        Row(Modifier.fillMaxWidth().height(58.dp)) {
            tabs.forEachIndexed { i, t ->
                val sel = i == selected
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null
                    ) { onSelect(i) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(Modifier.width(if (sel) 28.dp else 0.dp).height(2.dp).background(m.fg))
                    Spacer(Modifier.height(10.dp))
                    Text("0${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (sel) m.fg else m.faint)
                    Spacer(Modifier.height(2.dp))
                    Text(t, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = if (sel) m.fg else m.dim)
                }
            }
        }
    }
}
