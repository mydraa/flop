package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.R
import com.example.data.IcsRepository
import com.example.model.CourseEvent
import com.example.worker.WorkManagerScheduler
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Widget Glance d'écran d'accueil inspiré du widget "Agenda" de Google Calendar.
 * Charge instantanément les données depuis le cache local (context.cacheDir/schedule.json).
 */
class ScheduleWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = IcsRepository(context)
        val today = LocalDate.now(IcsRepository.PARIS_ZONE)
        val now = LocalDateTime.now(IcsRepository.PARIS_ZONE)

        val prefs = context.getSharedPreferences("flop_widget_prefs", Context.MODE_PRIVATE)
        val dayOffset = prefs.getInt("day_offset", 0)
        val displayedDate = today.plusDays(dayOffset.toLong())

        // Lecture synchrone et directe du cache local pour la date affichée (0ms de latence)
        val courses = repository.getEventsForDate(displayedDate)

        provideContent {
            GlanceTheme {
                WidgetRoot(
                    context = context,
                    today = today,
                    displayedDate = displayedDate,
                    dayOffset = dayOffset,
                    now = now,
                    courses = courses
                )
            }
        }
    }
}

// Alias pour rétrocompatibilité
typealias ScheduleAppWidget = ScheduleWidget

@Composable
private fun WidgetRoot(
    context: Context,
    today: LocalDate,
    displayedDate: LocalDate,
    dayOffset: Int,
    now: LocalDateTime,
    courses: List<CourseEvent>
) {
    val containerBg = ColorProvider(
        day = Color(0xFFF8FAFC),
        night = Color(0xFF0F172A)
    )

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(containerBg)
            .cornerRadius(20.dp)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // En-tête : Contrôles de date (Jour précédent / Aujourd'hui / Jour suivant / Actualiser)
        WidgetHeader(
            displayedDate = displayedDate,
            dayOffset = dayOffset
        )

        Spacer(modifier = GlanceModifier.height(8.dp))

        // Corps : Liste chronologique ou état vide
        if (courses.isEmpty()) {
            EmptyWidgetState(
                dayOffset = dayOffset
            )
        } else {
            LazyColumn(
                modifier = GlanceModifier.fillMaxSize()
            ) {
                items(
                    items = courses,
                    itemId = { it.id.hashCode().toLong() }
                ) { course ->
                    CourseWidgetItem(
                        course = course,
                        now = now
                    )
                    Spacer(modifier = GlanceModifier.height(6.dp))
                }
            }
        }
    }
}

/**
 * En-tête avec titre du jour, date formatée et barre d'actions navigation (Précédent, Aujourd'hui, Suivant, Actualiser).
 */
@Composable
private fun WidgetHeader(
    displayedDate: LocalDate,
    dayOffset: Int
) {
    val formatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)
    val rawDate = displayedDate.format(formatter)
    val formattedDate = rawDate.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.FRENCH) else it.toString()
    }

    val dayLabel = when (dayOffset) {
        0 -> "Aujourd'hui"
        1 -> "Demain"
        -1 -> "Hier"
        else -> if (dayOffset > 0) "+${dayOffset}j" else "${dayOffset}j"
    }

    val headerTextColor = ColorProvider(
        day = Color(0xFF0F172A),
        night = Color(0xFFF8FAFC)
    )
    val subtitleColor = ColorProvider(
        day = Color(0xFF64748B),
        night = Color(0xFF94A3B8)
    )
    val iconColor = ColorProvider(
        day = Color(0xFF334155),
        night = Color(0xFFCBD5E1)
    )
    val navButtonBg = ColorProvider(
        day = Color(0xFFE2E8F0),
        night = Color(0xFF1E293B)
    )

    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Colonne de gauche : Jour (Aujourd'hui / Demain) + Date
        Column(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = dayLabel,
                    style = TextStyle(
                        color = if (dayOffset == 0) ColorProvider(day = Color(0xFF2563EB), night = Color(0xFF60A5FA)) else headerTextColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                if (dayOffset != 0) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = "•",
                        style = TextStyle(color = subtitleColor, fontSize = 12.sp)
                    )
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Text(
                        text = formattedDate,
                        style = TextStyle(
                            color = subtitleColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            if (dayOffset == 0) {
                Text(
                    text = formattedDate,
                    style = TextStyle(
                        color = subtitleColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal
                    )
                )
            }
        }

        // Barre d'actions à droite : [<] [Auj] [>] [↻]
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Bouton Jour Précédent (<)
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(navButtonBg)
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<ScheduleWidgetPrevDayCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_prev),
                    contentDescription = "Jour précédent",
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(iconColor)
                )
            }

            Spacer(modifier = GlanceModifier.width(4.dp))

            // Bouton Retour à Aujourd'hui (affiché seulement si décalage)
            if (dayOffset != 0) {
                Box(
                    modifier = GlanceModifier
                        .size(28.dp)
                        .background(ColorProvider(day = Color(0xFFDBEAFE), night = Color(0xFF1E3A8A)))
                        .cornerRadius(14.dp)
                        .clickable(actionRunCallback<ScheduleWidgetTodayCallback>()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_today),
                        contentDescription = "Revenir à aujourd'hui",
                        modifier = GlanceModifier.size(15.dp),
                        colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(day = Color(0xFF1D4ED8), night = Color(0xFF93C5FD)))
                    )
                }
                Spacer(modifier = GlanceModifier.width(4.dp))
            }

            // Bouton Jour Suivant (>)
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(navButtonBg)
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<ScheduleWidgetNextDayCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_next),
                    contentDescription = "Jour suivant",
                    modifier = GlanceModifier.size(16.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(iconColor)
                )
            }

            Spacer(modifier = GlanceModifier.width(4.dp))

            // Bouton de rafraîchissement manuel
            Box(
                modifier = GlanceModifier
                    .size(28.dp)
                    .background(navButtonBg)
                    .cornerRadius(14.dp)
                    .clickable(actionRunCallback<ScheduleWidgetRefreshCallback>()),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_refresh),
                    contentDescription = "Actualiser l'emploi du temps",
                    modifier = GlanceModifier.size(15.dp),
                    colorFilter = androidx.glance.ColorFilter.tint(iconColor)
                )
            }
        }
    }
}

/**
 * Élément de liste pour un cours individuel.
 */
@Composable
private fun CourseWidgetItem(
    course: CourseEvent,
    now: LocalDateTime
) {
    val isOngoing = course.isCurrentlyOngoing(now)
    val isPast = course.isPast(now)

    // Palette dynamique pour le cours actif vs inactif
    val cardBg = when {
        isOngoing -> ColorProvider(
            day = Color(0xFFEFF6FF),
            night = Color(0xFF1E293B)
        )
        isPast -> ColorProvider(
            day = Color(0xFFF1F5F9),
            night = Color(0xFF161E2E)
        )
        else -> ColorProvider(
            day = Color(0xFFFFFFFF),
            night = Color(0xFF1E293B)
        )
    }

    val accentColor = when {
        isOngoing -> ColorProvider(
            day = Color(0xFF2563EB),
            night = Color(0xFF60A5FA)
        )
        isPast -> ColorProvider(
            day = Color(0xFF94A3B8),
            night = Color(0xFF64748B)
        )
        else -> ColorProvider(
            day = Color(0xFF0284C7),
            night = Color(0xFF38BDF8)
        )
    }

    val titleColor = when {
        isPast -> ColorProvider(
            day = Color(0xFF94A3B8),
            night = Color(0xFF64748B)
        )
        else -> ColorProvider(
            day = Color(0xFF0F172A),
            night = Color(0xFFF8FAFC)
        )
    }

    val subtitleColor = when {
        isPast -> ColorProvider(
            day = Color(0xFFCBD5E1),
            night = Color(0xFF475569)
        )
        else -> ColorProvider(
            day = Color(0xFF64748B),
            night = Color(0xFF94A3B8)
        )
    }

    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(cardBg)
            .cornerRadius(12.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Barre verticale d'accentuation (épaisse pour cours actif)
        Box(
            modifier = GlanceModifier
                .width(if (isOngoing) 4.dp else 3.dp)
                .height(38.dp)
                .background(accentColor)
                .cornerRadius(2.dp)
        ) {}

        Spacer(modifier = GlanceModifier.width(10.dp))

        // Badge horaire
        Column(
            modifier = GlanceModifier.width(62.dp)
        ) {
            val startFmt = course.startTime.format(DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH))
            val endFmt = course.endTime.format(DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH))

            Text(
                text = startFmt,
                style = TextStyle(
                    color = if (isOngoing) accentColor else titleColor,
                    fontSize = 12.sp,
                    fontWeight = if (isOngoing) FontWeight.Bold else FontWeight.Medium
                )
            )
            Text(
                text = endFmt,
                style = TextStyle(
                    color = subtitleColor,
                    fontSize = 11.sp
                )
            )
        }

        Spacer(modifier = GlanceModifier.width(8.dp))

        // Détails du cours (Titre, Salle, Enseignant)
        Column(
            modifier = GlanceModifier.defaultWeight()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = course.title,
                    maxLines = 1,
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )

                if (isOngoing) {
                    Spacer(modifier = GlanceModifier.width(4.dp))
                    Box(
                        modifier = GlanceModifier
                            .background(accentColor)
                            .cornerRadius(4.dp)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "EN COURS",
                            style = TextStyle(
                                color = ColorProvider(day = Color.White, night = Color.White),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = GlanceModifier.height(2.dp))

            val details = buildString {
                if (course.room.isNotBlank()) append("Salle ${course.room}")
                if (course.teacher.isNotBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(course.teacher)
                }
            }

            if (details.isNotBlank()) {
                Text(
                    text = details,
                    maxLines = 1,
                    style = TextStyle(
                        color = subtitleColor,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

/**
 * Message propre affiché si aucun cours n'est prévu.
 */
@Composable
private fun EmptyWidgetState(dayOffset: Int) {
    val emptyTextColor = ColorProvider(
        day = Color(0xFF64748B),
        night = Color(0xFF94A3B8)
    )

    val label = when (dayOffset) {
        0 -> "🎉 Aucun cours aujourd'hui"
        1 -> "🎉 Aucun cours demain"
        -1 -> "🎉 Aucun cours hier"
        else -> "🎉 Aucun cours programmé"
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = emptyTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(modifier = GlanceModifier.height(4.dp))
            Text(
                text = "Touchez pour ouvrir Flop!EDT",
                style = TextStyle(
                    color = emptyTextColor,
                    fontSize = 11.sp
                )
            )
        }
    }
}

/**
 * ActionCallback liée au bouton de jour suivant (passer au lendemain sur le widget).
 */
class ScheduleWidgetNextDayCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences("flop_widget_prefs", Context.MODE_PRIVATE)
        val currentOffset = prefs.getInt("day_offset", 0)
        prefs.edit().putInt("day_offset", currentOffset + 1).apply()
        ScheduleWidget().update(context, glanceId)
    }
}

/**
 * ActionCallback liée au bouton de jour précédent.
 */
class ScheduleWidgetPrevDayCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences("flop_widget_prefs", Context.MODE_PRIVATE)
        val currentOffset = prefs.getInt("day_offset", 0)
        prefs.edit().putInt("day_offset", currentOffset - 1).apply()
        ScheduleWidget().update(context, glanceId)
    }
}

/**
 * ActionCallback liée au bouton de retour direct à aujourd'hui.
 */
class ScheduleWidgetTodayCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val prefs = context.getSharedPreferences("flop_widget_prefs", Context.MODE_PRIVATE)
        prefs.edit().putInt("day_offset", 0).apply()
        ScheduleWidget().update(context, glanceId)
    }
}

/**
 * ActionCallback liée au bouton de rafraîchissement manuel du widget.
 */
class ScheduleWidgetRefreshCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        WorkManagerScheduler.triggerImmediateSync(context)
        ScheduleWidget().update(context, glanceId)
    }
}

typealias RefreshWidgetActionCallback = ScheduleWidgetRefreshCallback
