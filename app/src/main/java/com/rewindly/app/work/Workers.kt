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
import com.rewindly.app.data.repository.RewindlyRepository
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

/** Cada semana genera automáticamente el informe semanal si hay datos de esos días. */
class WeeklyReportWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = RewindlyRepository.get(applicationContext)
        if (!repo.settings.weeklyAutoReport) return Result.success()
        val (start, end) = Periods.lastCompletedWeek(repo.settings.zone())
        if (repo.reportExists(ReportType.WEEKLY.name, start, end)) return Result.success()
        val stats = repo.computeStats(start, end)
        if (stats.isEmpty) return Result.success()
        repo.saveReport(ReportType.WEEKLY.name, "Semanal · ${StoryGenerator.dateRange(stats)}", stats)
        Notifications.show(
            applicationContext, 1001, "Tu semana en música está lista",
            "${stats.totalMs / 60_000} minutos · Tu artista principal: ${stats.topArtists.firstOrNull()?.name ?: "—"}. Abre Rewindly para ver tus historias."
        )
        return Result.success()
    }
}

/** Recordatorio mensual para pedir una exportación nueva a Spotify (el ZIP puede tardar). */
class ExportReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = RewindlyRepository.get(applicationContext)
        if (!repo.settings.exportReminder) return Result.success()
        Notifications.show(
            applicationContext, 1002, "¿Pedimos datos nuevos a Spotify?",
            "Solicita tu historial en Privacidad de la cuenta. Mientras llega, el registro en vivo y Last.fm siguen sumando reproducciones."
        )
        return Result.success()
    }
}

/** Sincronización periódica de Last.fm (opcional, solo con red). */
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
        if (s.weeklyAutoReport) {
            wm.enqueueUniquePeriodicWork(
                "weekly_report", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeeklyReportWorker>(7, TimeUnit.DAYS).setInitialDelay(1, TimeUnit.DAYS).build()
            )
        } else wm.cancelUniqueWork("weekly_report")

        if (s.exportReminder) {
            wm.enqueueUniquePeriodicWork(
                "export_reminder", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<ExportReminderWorker>(30, TimeUnit.DAYS).setInitialDelay(30, TimeUnit.DAYS).build()
            )
        } else wm.cancelUniqueWork("export_reminder")

        if (s.lastFmAutoSync) {
            wm.enqueueUniquePeriodicWork(
                "lastfm_sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<LastFmSyncWorker>(12, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
            )
        } else wm.cancelUniqueWork("lastfm_sync")
    }
}
