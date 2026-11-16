package com.rewindly.app.live

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.data.repository.RewindlyRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Registro en vivo 100 % local. Android permite a una app con "acceso a notificaciones"
 * leer la sesión multimedia pública que Spotify ya muestra en la pantalla de bloqueo.
 * No toca la cuenta, no usa la red y no lee el contenido de otras notificaciones.
 * Como respaldo escucha el "Device Broadcast Status" oficial de Spotify si el usuario lo activa.
 */
class MediaListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val handler = Handler(Looper.getMainLooper())
    private var sessionManager: MediaSessionManager? = null
    private val controllers = mutableMapOf<MediaSession_Token, Pair<MediaController, MediaController.Callback>>()
    private var batchId: Long? = null
    private lateinit var repo: RewindlyRepository

    private val tracker by lazy {
        PlayTracker(
            clock = { SystemClock.elapsedRealtime() },
            wallClock = { System.currentTimeMillis() },
            onCommit = { play ->
                if (!repo.settings.liveTrackingEnabled) return@PlayTracker
                scope.launch {
                    mutex.withLock {
                        val id = batchId ?: repo.newBatch(Sources.LIVE, null).also { batchId = it }
                        repo.insertPlays(listOf(play), Sources.LIVE, id)
                    }
                }
            }
        )
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        handler.post { updateControllers(list.orEmpty()) }
    }

    private val spotifyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            when (intent.action) {
                ACTION_METADATA -> {
                    val track = intent.getStringExtra("track") ?: return
                    val artist = intent.getStringExtra("artist") ?: return
                    // Solo completa datos cuando la sesión multimedia no está disponible
                    if (controllers.isEmpty()) {
                        tracker.onMetadata(
                            PlayTracker.Track(
                                track, artist, intent.getStringExtra("album"),
                                intent.getIntExtra("length", 0) * 1000L, intent.getStringExtra("id")
                            )
                        )
                    }
                }
                ACTION_PLAYBACK -> if (controllers.isEmpty()) tracker.onPlaybackState(intent.getBooleanExtra("playing", false))
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = RewindlyRepository.get(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        val msm = getSystemService(MediaSessionManager::class.java)
        sessionManager = msm
        val component = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            updateControllers(msm.getActiveSessions(component))
        }
        val filter = IntentFilter().apply { addAction(ACTION_METADATA); addAction(ACTION_PLAYBACK) }
        runCatching { ContextCompat.registerReceiver(this, spotifyReceiver, filter, ContextCompat.RECEIVER_EXPORTED) }
        _connected = true
    }

    override fun onListenerDisconnected() {
        cleanup()
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        cleanup()
        scope.cancel()
        super.onDestroy()
    }

    private fun cleanup() {
        tracker.flush()
        runCatching { sessionManager?.removeOnActiveSessionsChangedListener(sessionsListener) }
        runCatching { unregisterReceiver(spotifyReceiver) }
        controllers.values.forEach { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
        controllers.clear()
        _connected = false
    }

    private fun updateControllers(list: List<MediaController>) {
        val spotify = list.filter { it.packageName in SPOTIFY_PACKAGES }
        val tokens = spotify.map { MediaSession_Token(it.sessionToken.hashCode()) }.toSet()
        // Quitar sesiones que ya no existen
        controllers.keys.filter { it !in tokens }.forEach { key ->
            controllers.remove(key)?.let { (c, cb) -> runCatching { c.unregisterCallback(cb) } }
            tracker.flush()
        }
        spotify.forEach { controller ->
            val key = MediaSession_Token(controller.sessionToken.hashCode())
            if (key in controllers) return@forEach
            val cb = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) = handleMetadata(metadata)
                override fun onPlaybackStateChanged(state: PlaybackState?) = handleState(state)
                override fun onSessionDestroyed() { tracker.flush() }
            }
            controller.registerCallback(cb, handler)
            controllers[key] = controller to cb
            handleMetadata(controller.metadata)
            handleState(controller.playbackState)
        }
    }

    private fun handleMetadata(m: MediaMetadata?) {
        if (m == null) return
        val title = m.getString(MediaMetadata.METADATA_KEY_TITLE) ?: return
        val artist = m.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: m.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: return
        tracker.onMetadata(
            PlayTracker.Track(
                title = title,
                artist = artist,
                album = m.getString(MediaMetadata.METADATA_KEY_ALBUM),
                durationMs = m.getLong(MediaMetadata.METADATA_KEY_DURATION)
            )
        )
    }

    private fun handleState(state: PlaybackState?) {
        state ?: return
        tracker.onPlaybackState(state.state == PlaybackState.STATE_PLAYING)
    }

    /** Envoltorio simple para usar el token como clave estable. */
    data class MediaSession_Token(val hash: Int)

    companion object {
        val SPOTIFY_PACKAGES = setOf("com.spotify.music", "com.spotify.lite")
        const val ACTION_METADATA = "com.spotify.music.metadatachanged"
        const val ACTION_PLAYBACK = "com.spotify.music.playbackstatechanged"

        @Volatile private var _connected = false
        val isConnected: Boolean get() = _connected

        fun hasPermission(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
