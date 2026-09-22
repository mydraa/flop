package com.example.worker

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.IcsPreferences
import com.example.data.IcsRepository
import com.example.notification.NotificationHelper
import com.example.widget.ScheduleWidget
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Worker WorkManager exécuté périodiquement ou à la demande pour :
 * 1. Lire l'URL .ics configurée dans DataStore Preferences.
 * 2. Synchroniser le fichier schedule.json avec IcsRepository.syncFromRemote(url).
 * 3. Mettre à jour la notification permanente dynamique.
 * 4. Déclencher la recomposition du widget Glance (ScheduleWidget().updateAll(context)).
 */
class ScheduleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "ScheduleWorker"
        const val UNIQUE_WORK_NAME = "flop_edt_schedule_sync"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting background schedule sync & notification update")
        val repository = IcsRepository(applicationContext)
        val preferences = IcsPreferences(applicationContext)

        val today = LocalDate.now(IcsRepository.PARIS_ZONE)
        val now = LocalDateTime.now(IcsRepository.PARIS_ZONE)

        return try {
            // 1. Lit l'URL .ics depuis DataStore
            val icsUrl = preferences.getIcsUrl()

            // 2. Synchronisation distante si une URL est définie
            val todayCourses = if (!icsUrl.isNullOrBlank()) {
                val syncResult = repository.syncFromRemote(icsUrl, today)
                if (syncResult.isSuccess) {
                    syncResult.getOrNull() ?: repository.getTodayEvents(today)
                } else {
                    Log.w(TAG, "Remote sync failed, fallback to local cache: ${syncResult.exceptionOrNull()?.message}")
                    repository.getTodayEvents(today)
                }
            } else {
                Log.d(TAG, "No ICS URL configured yet, reading cached events")
                repository.getTodayEvents(today)
            }

            // 3. Met à jour la notification permanente silencieuse
            NotificationHelper.updateCurrentCourseNotification(applicationContext, todayCourses, now)

            // 4. Déclenche la recomposition du widget Glance
            try {
                ScheduleWidget().updateAll(applicationContext)
            } catch (t: Throwable) {
                Log.w(TAG, "Glance widget update skipped: ${t.message}")
            }

            Log.d(TAG, "ScheduleWorker completed successfully (${todayCourses.size} courses today)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error in ScheduleWorker", e)
            try {
                val cached = repository.getTodayEvents(today)
                NotificationHelper.updateCurrentCourseNotification(applicationContext, cached, now)
                ScheduleWidget().updateAll(applicationContext)
            } catch (_: Exception) {}

            Result.retry()
        }
    }
}

// Alias pour compatibilité ascendante
typealias ScheduleSyncWorker = ScheduleWorker
