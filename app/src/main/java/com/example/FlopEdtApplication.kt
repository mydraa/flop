package com.example

import android.app.Application
import android.util.Log
import com.example.notification.NotificationHelper
import com.example.worker.WorkManagerScheduler

class FlopEdtApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("FlopEdtApp", "Initializing application")

        // 1. Initialise le canal de notification silencieux
        NotificationHelper.createNotificationChannel(this)

        // 2. Planifie le WorkManager pour la synchronisation et mise à jour périodique
        try {
            WorkManagerScheduler.schedulePeriodicSync(this)
        } catch (t: Throwable) {
            Log.w("FlopEdtApp", "WorkManager init postponed: ${t.message}")
        }
    }
}
