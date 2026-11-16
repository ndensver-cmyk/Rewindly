package com.rewindly.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rewindly.app.MainActivity
import com.rewindly.app.R
import com.rewindly.app.data.entities.Sources
import com.rewindly.app.data.repository.RewindlyRepository
import com.rewindly.app.importer.AutoImport
import com.rewindly.app.lastfm.LastFmClient
import com.rewindly.app.stories.StoryGenerator
import com.rewindly.app.ui.Periods
import com.rewindly.app.ui.ReportType
import java.util.concurrent.TimeUnit

object Notifications {
    const val CHANNEL = "rewindly_reports"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Informes y recordatorios", NotificationManager.IMPORTANCE_DEFAULT)
        ch.description = "Avisos de informes nuevos y recordatorios para actualizar tus datos"
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val pi = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}

/** Diario: genera el informe semanal (lunes a domingo) y el mensual cuando se completa el período. */
class AutoReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = RewindlyRepository.get(applicationContext)
        val zone = repo.settings.zone()
        suspend fun make(type: ReportType, range: Pair<Long, Long>, id: Int, title: String) {
            if (repo.reportExists(type.name, range.first, range.second)) return
            val stats = repo.computeStats(range.first, range.second)
            if (stats.isEmpty) return
            repo.saveReport(type.name, "${type.label} · ${StoryGenerator.dateRange(stats)}", stats)
            Notifications.show(
                applicationContext, id, title,
                "${stats.totalMs / 60_000} minutos · Artista principal: ${stats.topArtists.firstOrNull()?.name ?: "—"}. Abre Rewindly para verlo."
            )
        }
        if (repo.settings.weeklyAutoReport) make(ReportType.WEEKLY, Periods.lastCompletedWeek(zone), 1001, "Tu semana en música está lista")
        if (repo.settings.monthlyAutoReport) make(ReportType.MONTHLY, Periods.lastCompletedMonth(zone), 1003, "Tu mes en música está listo")
        return Result.success()
    }
}

/**
 * Diario: acompaña la solicitud a Spotify. A los 5 días avisa que debería haber llegado
 * "Datos de la cuenta" y a los 30 el historial extendido. Se detiene cuando ya se importaron.
 */
class RequestTrackerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = RewindlyRepository.get(applicationContext)
        val s = repo.settings
        val req = s.spotifyRequestedAt
        if (req == 0L) {
            if (s.exportReminder) monthlyReminder(s)
            return Result.success()
        }
        val days = (System.currentTimeMillis() - req) / 86_400_000L
        val gotAccount = (repo.lastBatch(Sources.ZIP_ACCOUNT)?.createdAt ?: 0) > req
        val gotExtended = (repo.lastBatch(Sources.ZIP_EXTENDED)?.createdAt ?: 0) > req
        val auto = AutoImport.isEnabled(applicationContext)
        val tail = if (auto) "Descárgalo desde el correo de Spotify: Rewindly lo detectará y lo importará solo."
            else "Descárgalo desde el correo de Spotify y ábrelo con Rewindly."
        if (gotExtended) { s.spotifyRequestedAt = 0; s.requestNotifiedStage = 0; return Result.success() }
        if (days >= 5 && !gotAccount && s.requestNotifiedStage < 1) {
            Notifications.show(applicationContext, 1004, "Tus datos de cuenta deberían estar listos", tail)
            s.requestNotifiedStage = 1
        }
        if (days >= 30 && s.requestNotifiedStage < 2) {
            Notifications.show(applicationContext, 1005, "Revisa tu correo: historial extendido", tail)
            s.requestNotifiedStage = 2
        }
        return Result.success()
    }

    private fun monthlyReminder(s: com.rewindly.app.data.repository.Settings) {
        val day = java.time.LocalDate.now(s.zone()).dayOfMonth
        if (day != 1) return
        Notifications.show(
            applicationContext, 1002, "¿Pedimos datos nuevos a Spotify?",
            "Solicita tu historial en Privacidad de la cuenta y márcalo en Rewindly. Mientras llega, el registro en vivo y Last.fm siguen sumando."
        )
    }
}

/** Busca exportaciones nuevas en Descargas o en la carpeta vigilada y las importa. */
class AutoImportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!AutoImport.isEnabled(applicationContext)) return Result.success()
        val r = AutoImport.run(applicationContext)
        val inserted = r.imported.sumOf { it.inserted }
        if (r.imported.isNotEmpty()) {
            Notifications.show(
                applicationContext, 1006, "Datos de Spotify importados automáticamente",
                "Se encontraron ${r.imported.size} archivo(s) y se añadieron $inserted reproducciones nuevas."
            )
        }
        return Result.success()
    }
}

/** Sincronización periódica de Last.fm (solo con red). */
class LastFmSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = RewindlyRepository.get(applicationContext)
        val s = repo.settings
        if (!s.lastFmAutoSync || s.lastFmUser.isBlank() || s.lastFmApiKey.isBlank()) return Result.success()
        return try { LastFmClient(repo).sync(); Result.success() } catch (e: Exception) { Result.retry() }
    }
}

object Scheduler {
    fun apply(context: Context) {
        val wm = WorkManager.getInstance(context)
        val s = RewindlyRepository.get(context).settings
        listOf("weekly_report", "export_reminder").forEach { wm.cancelUniqueWork(it) } // tareas de la v1.0

        wm.enqueueUniquePeriodicWork(
            "auto_reports", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AutoReportWorker>(1, TimeUnit.DAYS).build()
        )
        wm.enqueueUniquePeriodicWork(
            "request_tracker", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RequestTrackerWorker>(1, TimeUnit.DAYS).build()
        )
        if (AutoImport.isEnabled(context)) {
            wm.enqueueUniquePeriodicWork(
                "auto_import", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutoImportWorker>(1, TimeUnit.HOURS).build()
            )
        } else wm.cancelUniqueWork("auto_import")

        if (s.lastFmAutoSync && s.lastFmUser.isNotBlank() && s.lastFmApiKey.isNotBlank()) {
            wm.enqueueUniquePeriodicWork(
                "lastfm_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LastFmSyncWorker>(3, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        } else wm.cancelUniqueWork("lastfm_sync")
    }
}
