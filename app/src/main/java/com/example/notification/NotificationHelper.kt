package com.example.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.CourseEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object NotificationHelper {

    private const val TAG = "NotificationHelper"
    const val CHANNEL_ID = "flop_ongoing_channel"
    const val NOTIFICATION_ID = 1001

    private const val PREFS_NAME = "flop_notification_prefs"
    private const val KEY_NOTIFICATION_DISMISSED = "notification_dismissed_by_user"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH)

    private val _isNotificationDismissedFlow = MutableStateFlow(false)
    val isNotificationDismissedFlow: StateFlow<Boolean> = _isNotificationDismissedFlow.asStateFlow()

    /**
     * Vérifie si la notification a été retirée/masquée par l'utilisateur.
     */
    fun isNotificationDismissed(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val dismissed = prefs.getBoolean(KEY_NOTIFICATION_DISMISSED, false)
        _isNotificationDismissedFlow.value = dismissed
        return dismissed
    }

    /**
     * Enregistre l'état du retrait volontaire de la notification par l'utilisateur.
     */
    fun setNotificationDismissed(context: Context, dismissed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_NOTIFICATION_DISMISSED, dismissed).apply()
        _isNotificationDismissedFlow.value = dismissed
    }

    /**
     * Crée le canal de notification silencieux (IMPORTANCE_LOW / IMPORTANCE_MIN).
     * Sans son, sans vibration, sans pop-up intempestif.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_desc)
            // IMPORTANCE_LOW garantit le silence complet tout en restant visible dans la barre d'état
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                setSound(null, null)
            }

            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Vérifie si l'application possède la permission d'afficher des notifications (Android 13+).
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Met à jour ou affiche la notification selon le cours en cours ou à venir.
     * Si l'utilisateur l'a retirée volontairement, ne l'affiche pas à moins que [force] soit true.
     */
    fun updateCurrentCourseNotification(
        context: Context,
        todayCourses: List<CourseEvent>,
        now: LocalDateTime = LocalDateTime.now(),
        force: Boolean = false
    ) {
        if (!hasNotificationPermission(context)) {
            Log.w(TAG, "Notification permission not granted, cannot post ongoing notification")
            return
        }

        if (!force && isNotificationDismissed(context)) {
            Log.d(TAG, "Notification was dismissed by user, skipping periodic update")
            return
        }

        val notification = buildOngoingNotification(context, todayCourses, now)

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Ongoing notification updated successfully with progress bar")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException while notifying: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update notification: ${e.message}")
        }
    }

    /**
     * Rétablit la notification de cours lorsque l'utilisateur appuie sur "Remettre la notif".
     */
    fun restoreNotification(
        context: Context,
        todayCourses: List<CourseEvent>,
        now: LocalDateTime = LocalDateTime.now()
    ) {
        setNotificationDismissed(context, false)
        updateCurrentCourseNotification(context, todayCourses, now, force = true)
    }

    /**
     * Construit la notification avec barre d'avancement pour le cours en cours.
     */
    fun buildOngoingNotification(
        context: Context,
        todayCourses: List<CourseEvent>,
        now: LocalDateTime = LocalDateTime.now()
    ): Notification {
        val currentCourse = todayCourses.firstOrNull { it.isOngoing(now) }
        val nextCourse = todayCourses.firstOrNull { it.isUpcoming(now) }

        val title: String
        val contentText: String
        val subText: String
        val detailedText: String
        var hasProgressBar = false
        var progressPercent = 0

        if (currentCourse != null) {
            // Un cours est actuellement en cours : calculer la barre d'avancement
            title = currentCourse.title
            val startFormatted = currentCourse.startTime.format(timeFormatter)
            val endFormatted = currentCourse.endTime.format(timeFormatter)

            val totalSeconds = Duration.between(currentCourse.startTime, currentCourse.endTime).seconds.coerceAtLeast(1)
            val elapsedSeconds = Duration.between(currentCourse.startTime, now).seconds.coerceIn(0, totalSeconds)
            progressPercent = ((elapsedSeconds.toDouble() / totalSeconds.toDouble()) * 100).toInt().coerceIn(0, 100)
            val remainingMinutes = Duration.between(now, currentCourse.endTime).toMinutes().coerceAtLeast(0)

            contentText = if (currentCourse.room.isNotBlank()) {
                "Salle ${currentCourse.room} • reste ${remainingMinutes} min ($progressPercent%)"
            } else {
                "Reste ${remainingMinutes} min ($progressPercent%)"
            }
            subText = "En cours • $progressPercent%"
            hasProgressBar = true

            val teacherPart = if (currentCourse.teacher.isNotBlank()) " • ${currentCourse.teacher}" else ""
            val roomPart = if (currentCourse.room.isNotBlank()) "Salle ${currentCourse.room}$teacherPart\n" else ""
            detailedText = "$roomPart$startFormatted - $endFormatted • $progressPercent% effectué ($remainingMinutes min restantes)"
        } else if (nextCourse != null) {
            // Aucun cours en ce moment, mais un prochain cours arrive plus tard aujourd'hui
            title = context.getString(R.string.break_or_no_class)
            val startFormatted = nextCourse.startTime.format(timeFormatter)
            val roomPart = if (nextCourse.room.isNotBlank()) " (${nextCourse.room})" else ""
            contentText = "Prochain cours à $startFormatted : ${nextCourse.title}$roomPart"
            subText = "Pause"
            detailedText = contentText
        } else {
            // Aucun cours aujourd'hui ou tous les cours sont terminés
            title = context.getString(R.string.break_or_no_class)
            contentText = if (todayCourses.isEmpty()) {
                context.getString(R.string.no_courses_today)
            } else {
                "Journée de cours terminée"
            }
            subText = "Flop!EDT"
            detailedText = contentText
        }

        // Clic principal : ouvre l'application
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action / Balayage : Enregistre le retrait volontaire par l'utilisateur
        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSubText(subText)
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent) // Balayage de la notification
            .setOngoing(false) // Permet à l'utilisateur de retirer la notification
            .setAutoCancel(false)
            .setSilent(true) // Silencieux : pas de son
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Priorité basse, discret
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailedText))
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notification_dismiss_action),
                dismissPendingIntent
            )

        // Barre d'avancement dynamique si un cours est en cours
        if (hasProgressBar) {
            builder.setProgress(100, progressPercent, false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
    }

    /**
     * Supprime la notification persistante si besoin.
     */
    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
