package com.rewindly.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.vector.ImageVector
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
            RewindlyTheme {
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
                        snackbarHost = { SnackbarHost(snackbar) },
                        bottomBar = {
                            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                                tabs.forEachIndexed { i, t ->
                                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(t.icon, null) }, label = { Text(t.label) })
                                }
                            }
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

    private data class Tab(val label: String, val icon: ImageVector)
    private val tabs = listOf(
        Tab("Inicio", Icons.Default.Home),
        Tab("Datos", Icons.Default.CloudUpload),
        Tab("Stats", Icons.Default.BarChart),
        Tab("Informes", Icons.Default.LibraryMusic),
        Tab("Ajustes", Icons.Default.Settings)
    )
}
