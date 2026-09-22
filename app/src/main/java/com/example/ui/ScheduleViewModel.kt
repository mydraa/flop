package com.example.ui

import android.app.Application
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.IcsPreferences
import com.example.data.IcsRepository
import com.example.model.CourseEvent
import com.example.model.ResourceItem
import com.example.model.ResourceType
import com.example.notification.NotificationHelper
import com.example.widget.ScheduleWidget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

enum class CalendarViewMode {
    DAY,
    WEEK,
    MONTH,
    RESOURCES
}

enum class ResourceFilter {
    ALL,
    ROOMS,
    TEACHERS,
    SUBJECTS
}

data class ScheduleUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val calendarViewMode: CalendarViewMode = CalendarViewMode.DAY,
    val selectedDate: LocalDate = LocalDate.now(IcsRepository.PARIS_ZONE),
    val selectedYearMonth: YearMonth = YearMonth.now(IcsRepository.PARIS_ZONE),
    val allCourses: List<CourseEvent> = emptyList(),
    val displayedCourses: List<CourseEvent> = emptyList(),
    val weekCourses: Map<LocalDate, List<CourseEvent>> = emptyMap(),
    val monthCourses: Map<LocalDate, List<CourseEvent>> = emptyMap(),
    val resourceItems: List<ResourceItem> = emptyList(),
    val searchQuery: String = "",
    val resourceFilter: ResourceFilter = ResourceFilter.ALL,
    val calendarSearchQuery: String = "",
    val selectedSubject: String? = null,
    val isCalendarSearchVisible: Boolean = false,
    val todayCourses: List<CourseEvent> = emptyList(),
    val currentTime: LocalDateTime = LocalDateTime.now(IcsRepository.PARIS_ZONE),
    val isFromCache: Boolean = true,
    val lastSyncTime: LocalDateTime? = null,
    val errorMessage: String? = null,
    val icsUrl: String? = null,
    val isUrlLoaded: Boolean = false,
    val hasNotificationPermission: Boolean = false,
    val isNotificationDismissed: Boolean = false,
    val isCacheStale: Boolean = false,
    val isOffline: Boolean = false
) {
    val hasConfiguredUrl: Boolean
        get() = isUrlLoaded && !icsUrl.isNullOrBlank()

    val canRestoreNotification: Boolean
        get() = hasNotificationPermission && isNotificationDismissed

    val isCacheStaleAndOffline: Boolean
        get() = isCacheStale && isOffline

    val isSelectedDateToday: Boolean
        get() = selectedDate == LocalDate.now(IcsRepository.PARIS_ZONE)

    val currentCourse: CourseEvent?
        get() = displayedCourses.firstOrNull { it.isCurrentlyOngoing(currentTime) }

    val nextCourse: CourseEvent?
        get() = displayedCourses.firstOrNull { it.isUpcoming(currentTime) }

    val availableSubjects: List<String>
        get() = allCourses.map { it.title.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    fun matchesCalendarFilter(course: CourseEvent): Boolean {
        val subjectMatches = selectedSubject == null || course.title.equals(selectedSubject, ignoreCase = true)
        if (!subjectMatches) return false
        if (calendarSearchQuery.isBlank()) return true
        val q = calendarSearchQuery.trim().lowercase()
        return course.title.lowercase().contains(q) ||
                course.room.lowercase().contains(q) ||
                course.teacher.lowercase().contains(q)
    }

    val isCalendarFilterActive: Boolean
        get() = calendarSearchQuery.isNotBlank() || selectedSubject != null

    val filteredDisplayedCourses: List<CourseEvent>
        get() = if (isCalendarFilterActive) displayedCourses.filter { matchesCalendarFilter(it) } else displayedCourses

    val filteredWeekCourses: Map<LocalDate, List<CourseEvent>>
        get() = if (isCalendarFilterActive) {
            weekCourses.mapValues { (_, list) -> list.filter { matchesCalendarFilter(it) } }
        } else weekCourses

    val filteredMonthCourses: Map<LocalDate, List<CourseEvent>>
        get() = if (isCalendarFilterActive) {
            monthCourses.mapValues { (_, list) -> list.filter { matchesCalendarFilter(it) } }
        } else monthCourses

    val globalMatchingCourses: List<CourseEvent>
        get() = if (isCalendarFilterActive) {
            allCourses.filter { matchesCalendarFilter(it) }.sortedBy { it.startTime }
        } else emptyList()

    val filteredResources: List<ResourceItem>
        get() {
            var list = when (resourceFilter) {
                ResourceFilter.ALL -> resourceItems
                ResourceFilter.ROOMS -> resourceItems.filter { it.type == ResourceType.ROOM }
                ResourceFilter.TEACHERS -> resourceItems.filter { it.type == ResourceType.TEACHER }
                ResourceFilter.SUBJECTS -> resourceItems.filter { it.type == ResourceType.SUBJECT }
            }
            if (searchQuery.isNotBlank()) {
                val q = searchQuery.trim().lowercase()
                list = list.filter { item ->
                    item.name.lowercase().contains(q) ||
                    item.currentCourse?.title?.lowercase()?.contains(q) == true ||
                    item.nextCourse?.title?.lowercase()?.contains(q) == true ||
                    item.nextCourse?.room?.lowercase()?.contains(q) == true ||
                    item.nextCourse?.teacher?.lowercase()?.contains(q) == true
                }
            }
            return list
        }

    val searchMatchedCourses: List<CourseEvent>
        get() {
            if (searchQuery.isBlank()) return emptyList()
            val q = searchQuery.trim().lowercase()
            return allCourses.filter { course ->
                course.title.lowercase().contains(q) ||
                course.room.lowercase().contains(q) ||
                course.teacher.lowercase().contains(q)
            }.sortedBy { it.startTime }
        }
}

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IcsRepository(application)
    private val preferences = IcsPreferences(application)

    private val initialToday: LocalDate = LocalDate.now(IcsRepository.PARIS_ZONE)
    private val initialNow: LocalDateTime = LocalDateTime.now(IcsRepository.PARIS_ZONE)
    private val initialAllCourses: List<CourseEvent> = repository.getAllEvents()
    private val initialTodayCourses: List<CourseEvent> = repository.getEventsForDate(initialToday)
    private val initialYearMonth: YearMonth = YearMonth.now(IcsRepository.PARIS_ZONE)

    private val _uiState = MutableStateFlow(
        ScheduleUiState(
            isLoading = false,
            allCourses = initialAllCourses,
            todayCourses = initialTodayCourses,
            displayedCourses = initialTodayCourses,
            weekCourses = repository.getEventsForWeek(initialToday),
            monthCourses = repository.getEventsForMonth(initialYearMonth),
            resourceItems = computeResources(initialAllCourses, initialNow),
            currentTime = initialNow,
            isFromCache = true,
            isCacheStale = repository.isCacheOlderThan(24),
            isOffline = !repository.isInternetConnected(),
            hasNotificationPermission = NotificationHelper.hasNotificationPermission(application),
            isNotificationDismissed = NotificationHelper.isNotificationDismissed(application)
        )
    )
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        // 1. Notification alimentée immédiatement (si non retirée)
        if (initialTodayCourses.isNotEmpty() && !NotificationHelper.isNotificationDismissed(application)) {
            NotificationHelper.updateCurrentCourseNotification(
                application,
                initialTodayCourses,
                initialNow
            )
        }

        // 2. Observer les retraits / remises de la notification en direct
        viewModelScope.launch {
            NotificationHelper.isNotificationDismissedFlow.collect { dismissed ->
                _uiState.update { it.copy(isNotificationDismissed = dismissed) }
            }
        }

        // 3. Horloge temps réel (30s)
        startClockLoop()

        // 4. Observer l'URL et synchroniser
        viewModelScope.launch {
            preferences.icsUrlFlow.collect { savedUrl ->
                _uiState.update { it.copy(icsUrl = savedUrl, isUrlLoaded = true) }
                if (!savedUrl.isNullOrBlank()) {
                    refreshFromNetwork(savedUrl)
                }
            }
        }
    }

    /**
     * Changement de mode de vue calendrier (Jour, Semaine, Mois, Ressources)
     */
    fun setCalendarViewMode(mode: CalendarViewMode) {
        _uiState.update { it.copy(calendarViewMode = mode) }
    }

    /**
     * Sélection d'une date spécifique (ex: clic sur un jour de la semaine ou du mois)
     */
    fun setSelectedDate(date: LocalDate) {
        val ym = YearMonth.from(date)
        _uiState.update { current ->
            current.copy(
                selectedDate = date,
                selectedYearMonth = ym,
                displayedCourses = repository.getEventsForDate(date),
                weekCourses = repository.getEventsForWeek(date),
                monthCourses = if (ym != current.selectedYearMonth) repository.getEventsForMonth(ym) else current.monthCourses
            )
        }
    }

    /**
     * Période suivante selon la vue active
     */
    fun nextPeriod() {
        val current = _uiState.value
        when (current.calendarViewMode) {
            CalendarViewMode.DAY -> {
                setSelectedDate(current.selectedDate.plusDays(1))
            }
            CalendarViewMode.WEEK -> {
                setSelectedDate(current.selectedDate.plusWeeks(1))
            }
            CalendarViewMode.MONTH -> {
                val nextMonth = current.selectedYearMonth.plusMonths(1)
                _uiState.update {
                    it.copy(
                        selectedYearMonth = nextMonth,
                        monthCourses = repository.getEventsForMonth(nextMonth)
                    )
                }
            }
            CalendarViewMode.RESOURCES -> {}
        }
    }

    /**
     * Période précédente selon la vue active
     */
    fun previousPeriod() {
        val current = _uiState.value
        when (current.calendarViewMode) {
            CalendarViewMode.DAY -> {
                setSelectedDate(current.selectedDate.minusDays(1))
            }
            CalendarViewMode.WEEK -> {
                setSelectedDate(current.selectedDate.minusWeeks(1))
            }
            CalendarViewMode.MONTH -> {
                val prevMonth = current.selectedYearMonth.minusMonths(1)
                _uiState.update {
                    it.copy(
                        selectedYearMonth = prevMonth,
                        monthCourses = repository.getEventsForMonth(prevMonth)
                    )
                }
            }
            CalendarViewMode.RESOURCES -> {}
        }
    }

    /**
     * Réinitialise à aujourd'hui / ce mois
     */
    fun resetToToday() {
        val today = LocalDate.now(IcsRepository.PARIS_ZONE)
        setSelectedDate(today)
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setResourceFilter(filter: ResourceFilter) {
        _uiState.update { it.copy(resourceFilter = filter) }
    }

    fun setCalendarSearchQuery(query: String) {
        _uiState.update { it.copy(calendarSearchQuery = query) }
    }

    fun setSelectedSubject(subject: String?) {
        _uiState.update { current ->
            val newSubject = if (current.selectedSubject.equals(subject, ignoreCase = true)) null else subject
            current.copy(selectedSubject = newSubject)
        }
    }

    fun clearCalendarFilters() {
        _uiState.update { it.copy(calendarSearchQuery = "", selectedSubject = null) }
    }

    fun toggleCalendarSearch(visible: Boolean? = null) {
        _uiState.update { current ->
            val next = visible ?: !current.isCalendarSearchVisible
            current.copy(isCalendarSearchVisible = next)
        }
    }

    fun jumpToCourseDate(date: LocalDate) {
        setSelectedDate(date)
        setCalendarViewMode(CalendarViewMode.DAY)
    }

    /**
     * Rafraîchissement asynchrone non-bloquant depuis le flux iCal distant.
     */
    fun refreshFromNetwork(forcedUrl: String? = null) {
        val targetUrl = forcedUrl ?: _uiState.value.icsUrl
        if (targetUrl.isNullOrBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }

            val today = LocalDate.now(IcsRepository.PARIS_ZONE)
            val now = LocalDateTime.now(IcsRepository.PARIS_ZONE)
            val result = repository.syncFromRemote(targetUrl, today)

            result.fold(
                onSuccess = { freshTodayCourses ->
                    val all = repository.getAllEvents()
                    val selected = _uiState.value.selectedDate
                    val ym = _uiState.value.selectedYearMonth

                    _uiState.update { current ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            allCourses = all,
                            todayCourses = freshTodayCourses,
                            displayedCourses = repository.getEventsForDate(selected),
                            weekCourses = repository.getEventsForWeek(selected),
                            monthCourses = repository.getEventsForMonth(ym),
                            resourceItems = computeResources(all, now),
                            currentTime = now,
                            isFromCache = false,
                            lastSyncTime = now,
                            errorMessage = null,
                            isCacheStale = false,
                            isOffline = false
                        )
                    }
                    NotificationHelper.updateCurrentCourseNotification(getApplication(), freshTodayCourses, now)
                    try {
                        ScheduleWidget().updateAll(getApplication())
                    } catch (_: Throwable) {}
                },
                onFailure = { error ->
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            errorMessage = error.message,
                            isCacheStale = repository.isCacheOlderThan(24),
                            isOffline = !repository.isInternetConnected()
                        )
                    }
                }
            )
        }
    }

    /**
     * Sauvegarde et valide la nouvelle URL .ics dans DataStore Preferences.
     */
    fun saveIcsUrl(newUrl: String, onResult: (Boolean, String?) -> Unit) {
        val clean = newUrl.trim()
        if (clean.isBlank()) {
            onResult(false, "L'URL ne peut pas être vide")
            return
        }

        val isValid = clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true) ||
                clean.startsWith("webcal://", ignoreCase = true)

        if (!isValid) {
            onResult(false, "L'URL doit commencer par https://, http:// ou webcal://")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val today = LocalDate.now(IcsRepository.PARIS_ZONE)
            val now = LocalDateTime.now(IcsRepository.PARIS_ZONE)
            val result = repository.syncFromRemote(clean, today)

            result.fold(
                onSuccess = { freshCourses ->
                    preferences.setIcsUrl(clean)
                    val all = repository.getAllEvents()
                    val selected = _uiState.value.selectedDate
                    val ym = _uiState.value.selectedYearMonth

                    _uiState.update { current ->
                        current.copy(
                            icsUrl = clean,
                            isUrlLoaded = true,
                            isLoading = false,
                            isRefreshing = false,
                            allCourses = all,
                            todayCourses = freshCourses,
                            displayedCourses = repository.getEventsForDate(selected),
                            weekCourses = repository.getEventsForWeek(selected),
                            monthCourses = repository.getEventsForMonth(ym),
                            resourceItems = computeResources(all, now),
                            currentTime = now,
                            isFromCache = false,
                            lastSyncTime = now,
                            errorMessage = null,
                            isCacheStale = false,
                            isOffline = false
                        )
                    }
                    NotificationHelper.updateCurrentCourseNotification(getApplication(), freshCourses, now)
                    try {
                        ScheduleWidget().updateAll(getApplication())
                    } catch (_: Throwable) {}
                    onResult(true, null)
                },
                onFailure = { error ->
                    _uiState.update { current ->
                        current.copy(
                            isRefreshing = false,
                            errorMessage = error.message
                        )
                    }
                    onResult(false, error.message ?: "Délai d'attente dépassé ou échec de connexion")
                }
            )
        }
    }

    /**
     * Réinitialise l'URL et nettoie le cache.
     */
    fun resetSchedule() {
        viewModelScope.launch {
            preferences.clearIcsUrl()
            repository.clearCache()
            val today = LocalDate.now(IcsRepository.PARIS_ZONE)
            val now = LocalDateTime.now(IcsRepository.PARIS_ZONE)
            val initial = repository.getEventsForDate(today)
            val all = repository.getAllEvents()
            val ym = YearMonth.now(IcsRepository.PARIS_ZONE)

            _uiState.update {
                ScheduleUiState(
                    isLoading = false,
                    icsUrl = null,
                    isUrlLoaded = true,
                    allCourses = all,
                    todayCourses = initial,
                    displayedCourses = initial,
                    weekCourses = repository.getEventsForWeek(today),
                    monthCourses = repository.getEventsForMonth(ym),
                    resourceItems = computeResources(all, now),
                    currentTime = now,
                    isFromCache = true,
                    hasNotificationPermission = NotificationHelper.hasNotificationPermission(getApplication())
                )
            }
            NotificationHelper.cancelNotification(getApplication())
            try {
                ScheduleWidget().updateAll(getApplication())
            } catch (_: Throwable) {}
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun restoreNotification() {
        NotificationHelper.setNotificationDismissed(getApplication(), false)
        _uiState.update { it.copy(isNotificationDismissed = false) }
        val courses = _uiState.value.todayCourses
        val now = _uiState.value.currentTime
        if (courses.isNotEmpty()) {
            NotificationHelper.restoreNotification(getApplication(), courses, now)
        }
    }

    fun dismissNotification() {
        NotificationHelper.setNotificationDismissed(getApplication(), true)
        NotificationHelper.cancelNotification(getApplication())
        _uiState.update { it.copy(isNotificationDismissed = true) }
    }

    fun onNotificationPermissionChanged(granted: Boolean) {
        _uiState.update { it.copy(hasNotificationPermission = granted) }
        if (granted) {
            val courses = _uiState.value.todayCourses
            val now = _uiState.value.currentTime
            NotificationHelper.setNotificationDismissed(getApplication(), false)
            _uiState.update { it.copy(isNotificationDismissed = false) }
            if (courses.isNotEmpty()) {
                NotificationHelper.restoreNotification(getApplication(), courses, now)
            }
        }
    }

    private fun startClockLoop() {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000L)
                val now = LocalDateTime.now(IcsRepository.PARIS_ZONE)
                val state = _uiState.value
                val today = LocalDate.now(IcsRepository.PARIS_ZONE)

                val refreshedTodayCourses = if (state.selectedDate == today) {
                    repository.getTodayEvents(today)
                } else {
                    state.todayCourses
                }

                _uiState.update { current ->
                    current.copy(
                        currentTime = now,
                        todayCourses = refreshedTodayCourses,
                        resourceItems = computeResources(current.allCourses, now),
                        isCacheStale = repository.isCacheOlderThan(24),
                        isOffline = !repository.isInternetConnected()
                    )
                }

                if (state.hasNotificationPermission && !state.isNotificationDismissed && refreshedTodayCourses.isNotEmpty()) {
                    NotificationHelper.updateCurrentCourseNotification(getApplication(), refreshedTodayCourses, now)
                }
            }
        }
    }

    private fun computeResources(all: List<CourseEvent>, now: LocalDateTime): List<ResourceItem> {
        val items = mutableListOf<ResourceItem>()

        // 1. Salles (Rooms)
        val byRoom = all.filter { it.room.isNotBlank() }.groupBy { it.room.trim() }
        for ((roomName, courses) in byRoom) {
            val sorted = courses.sortedBy { it.startTime }
            val current = sorted.firstOrNull { it.isCurrentlyOngoing(now) }
            val next = sorted.firstOrNull { it.startTime.isAfter(now) }
            val upcoming = sorted.filter { it.startTime.isAfter(now) }
            items.add(
                ResourceItem(
                    id = "room_$roomName",
                    name = roomName,
                    type = ResourceType.ROOM,
                    currentCourse = current,
                    nextCourse = next,
                    nextCourseTimeDescription = ResourceItem.formatNextCourseTime(next, now),
                    totalSessionsCount = courses.size,
                    upcomingSessionsCount = upcoming.size,
                    allCourses = sorted
                )
            )
        }

        // 2. Enseignants (Teachers)
        val byTeacher = all.filter { it.teacher.isNotBlank() }.groupBy { it.teacher.trim() }
        for ((teacherName, courses) in byTeacher) {
            val sorted = courses.sortedBy { it.startTime }
            val current = sorted.firstOrNull { it.isCurrentlyOngoing(now) }
            val next = sorted.firstOrNull { it.startTime.isAfter(now) }
            val upcoming = sorted.filter { it.startTime.isAfter(now) }
            items.add(
                ResourceItem(
                    id = "teacher_$teacherName",
                    name = teacherName,
                    type = ResourceType.TEACHER,
                    currentCourse = current,
                    nextCourse = next,
                    nextCourseTimeDescription = ResourceItem.formatNextCourseTime(next, now),
                    totalSessionsCount = courses.size,
                    upcomingSessionsCount = upcoming.size,
                    allCourses = sorted
                )
            )
        }

        // 3. Matières (Subjects)
        val bySubject = all.filter { it.title.isNotBlank() }.groupBy { it.title.trim() }
        for ((subjectName, courses) in bySubject) {
            val sorted = courses.sortedBy { it.startTime }
            val current = sorted.firstOrNull { it.isCurrentlyOngoing(now) }
            val next = sorted.firstOrNull { it.startTime.isAfter(now) }
            val upcoming = sorted.filter { it.startTime.isAfter(now) }
            items.add(
                ResourceItem(
                    id = "subject_$subjectName",
                    name = subjectName,
                    type = ResourceType.SUBJECT,
                    currentCourse = current,
                    nextCourse = next,
                    nextCourseTimeDescription = ResourceItem.formatNextCourseTime(next, now),
                    totalSessionsCount = courses.size,
                    upcomingSessionsCount = upcoming.size,
                    allCourses = sorted
                )
            )
        }

        return items.sortedWith(
            compareBy<ResourceItem> { it.type.ordinal }
                .thenByDescending { it.isCurrentlyOccupied }
                .thenBy { it.name }
        )
    }
}
