package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.IcsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * BroadcastReceiver gérant les actions sur la notification permanente :
 * - Retrait/suppression par balayage (deleteIntent) ou clic sur "Masquer"
 * - Restauration / réactivation de la notification
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationActionRx"
        const val ACTION_DISMISS = "com.example.notification.ACTION_DISMISS"
        const val ACTION_RESTORE = "com.example.notification.ACTION_RESTORE"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received notification action: $action")

        when (action) {
            ACTION_DISMISS -> {
                // Marque la notification comme retirée/masquée par l'utilisateur
                NotificationHelper.setNotificationDismissed(context, true)
                NotificationHelper.cancelNotification(context)
                Log.d(TAG, "Notification marked as dismissed by user")
            }

            ACTION_RESTORE -> {
                // Rétablit la notification et rafraîchit immédiatement le cours en cours
                NotificationHelper.setNotificationDismissed(context, false)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repository = IcsRepository(context)
                        val now = LocalDateTime.now(IcsRepository.PARIS_ZONE)
                        val todayCourses = repository.getTodayEvents(LocalDate.now(IcsRepository.PARIS_ZONE))
                        NotificationHelper.updateCurrentCourseNotification(
                            context = context,
                            todayCourses = todayCourses,
                            now = now,
                            force = true
                        )
                        Log.d(TAG, "Notification restored successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore notification from receiver: ${e.message}")
                    }
                }
            }
        }
    }
}
