package com.example.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ResourceType {
    ROOM,
    TEACHER,
    SUBJECT
}

/**
 * Modèle de ressource unifié (Salle, Enseignant, Matière)
 * Permet de connaître en un coup d'œil son statut actuel et son PROCHAIN cours programmé.
 */
data class ResourceItem(
    val id: String,
    val name: String,
    val type: ResourceType,
    val currentCourse: CourseEvent? = null,
    val nextCourse: CourseEvent? = null,
    val nextCourseTimeDescription: String = "",
    val totalSessionsCount: Int = 0,
    val upcomingSessionsCount: Int = 0,
    val allCourses: List<CourseEvent> = emptyList()
) {
    val isCurrentlyOccupied: Boolean
        get() = currentCourse != null

    companion object {
        private val TIME_FMT = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH)
        private val DATE_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

        fun formatNextCourseTime(
            nextCourse: CourseEvent?,
            now: LocalDateTime = LocalDateTime.now(CourseEvent.PARIS_ZONE)
        ): String {
            if (nextCourse == null) return "Aucun cours programmé"
            val courseStart = nextCourse.startTime
            val courseDate = courseStart.toLocalDate()
            val nowDate = now.toLocalDate()

            val timeStr = courseStart.format(TIME_FMT)

            return when {
                courseDate == nowDate -> "Aujourd'hui à $timeStr"
                courseDate == nowDate.plusDays(1) -> "Demain à $timeStr"
                courseDate.isBefore(nowDate.plusDays(7)) && courseDate.isAfter(nowDate) -> {
                    val dayName = courseStart.format(DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH))
                        .replaceFirstChar { it.titlecase(Locale.FRENCH) }
                    "$dayName à $timeStr"
                }
                else -> {
                    val dateFormatted = courseStart.format(DATE_FMT)
                    "$dateFormatted à $timeStr"
                }
            }
        }
    }
}
