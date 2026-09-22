package com.example.worker

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    private const val TAG = "WorkManagerScheduler"
    private const val PERIODIC_WORK_TAG = "flop_edt_periodic_sync"
    private const val IMMEDIATE_WORK_TAG = "flop_edt_immediate_sync"

    /**
     * Planifie la synchronisation périodique (toutes les 15 minutes, minimum autorisé par Android).
     */
    fun schedulePeriodicSync(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<ScheduleWorker>(
                15, TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .addTag(PERIODIC_WORK_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                ScheduleWorker.UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
            Log.d(TAG, "Enqueued periodic WorkManager job every 15 minutes")
        } catch (e: Throwable) {
            Log.w(TAG, "WorkManager periodic scheduling skipped: ${e.message}")
        }
    }

    /**
     * Déclenche une synchronisation ponctuelle immédiate en arrière-plan.
     */
    fun triggerImmediateSync(context: Context) {
        try {
            val oneTimeRequest = OneTimeWorkRequestBuilder<ScheduleWorker>()
                .addTag(IMMEDIATE_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "flop_edt_immediate_run",
                ExistingWorkPolicy.REPLACE,
                oneTimeRequest
            )
            Log.d(TAG, "Triggered immediate WorkManager sync")
        } catch (e: Throwable) {
            Log.w(TAG, "WorkManager immediate sync skipped: ${e.message}")
        }
    }
}
