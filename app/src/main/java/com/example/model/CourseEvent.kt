package com.example.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

/**
 * Modèle de données unique pour les cours de l'emploi du temps Flop!EDT.
 * Sérialisable directement en JSON pour un cache local instantané.
 */
@Serializable
data class CourseEvent(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val room: String,
    val teacher: String,
    val startIso: String,
    val endIso: String
) {
    constructor(
        title: String,
        room: String,
        teacher: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        id: String = UUID.randomUUID().toString()
    ) : this(
        id = id,
        title = title,
        room = room,
        teacher = teacher,
        startIso = startTime.toString(),
        endIso = endTime.toString()
    )
    companion object {
        val PARIS_ZONE: ZoneId = ZoneId.of("Europe/Paris")
        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH)

        /**
         * Constructeur d'aide à partir de LocalDateTime
         */
        fun fromLocalDateTime(
            id: String = UUID.randomUUID().toString(),
            title: String,
            room: String,
            teacher: String,
            startTime: LocalDateTime,
            endTime: LocalDateTime
        ): CourseEvent = CourseEvent(
            id = id,
            title = title,
            room = room,
            teacher = teacher,
            startIso = startTime.toString(),
            endIso = endTime.toString()
        )
    }

    @Transient
    val startTime: LocalDateTime = try {
        LocalDateTime.parse(startIso)
    } catch (_: Exception) {
        LocalDateTime.now(PARIS_ZONE)
    }

    @Transient
    val endTime: LocalDateTime = try {
        LocalDateTime.parse(endIso)
    } catch (_: Exception) {
        LocalDateTime.now(PARIS_ZONE)
    }

    /**
     * Formatage de la plage horaire (ex : "10h15 - 12h15").
     */
    @Transient
    val timeSlot: String = "${startTime.format(TIME_FORMATTER)} - ${endTime.format(TIME_FORMATTER)}"

    @Transient
    val formattedTimeRange: String = timeSlot

    /**
     * Vérifie si le cours est actuellement en cours.
     */
    fun isCurrentlyOngoing(now: LocalDateTime = LocalDateTime.now(PARIS_ZONE)): Boolean =
        !now.isBefore(startTime) && now.isBefore(endTime)

    fun isOngoing(now: LocalDateTime = LocalDateTime.now(PARIS_ZONE)): Boolean =
        isCurrentlyOngoing(now)

    fun isUpcoming(now: LocalDateTime = LocalDateTime.now(PARIS_ZONE)): Boolean =
        now.isBefore(startTime)

    fun isPast(now: LocalDateTime = LocalDateTime.now(PARIS_ZONE)): Boolean =
        !now.isBefore(endTime)

    @Transient
    val durationFormatted: String = run {
        val minutes = Duration.between(startTime, endTime).toMinutes()
        val hours = minutes / 60
        val rem = minutes % 60
        if (rem == 0L) "${hours}h" else "${hours}h${rem.toString().padStart(2, '0')}"
    }

    fun progress(now: LocalDateTime = LocalDateTime.now(PARIS_ZONE)): Float {
        val total = Duration.between(startTime, endTime).toMillis().toFloat()
        if (total <= 0f) return 0f
        val elapsed = Duration.between(startTime, now).toMillis().toFloat()
        return (elapsed / total).coerceIn(0f, 1f)
    }

    fun remainingMinutes(now: LocalDateTime = LocalDateTime.now(PARIS_ZONE)): Long {
        return Duration.between(now, endTime).toMinutes().coerceAtLeast(0)
    }
}
