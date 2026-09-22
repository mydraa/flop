package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.CalendarViewMode
import com.example.ui.ScheduleUiState
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarTopAppBar(
    uiState: ScheduleUiState,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onRestoreNotification: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    CenterAlignedTopAppBar(
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Badge d'état réseau et cache
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val isOffline = uiState.isOffline
                    val icon = if (isOffline) Icons.Default.CloudOff else Icons.Default.CloudDone
                    val tint = if (isOffline) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    val text = when {
                        isOffline -> stringResource(R.string.offline_mode)
                        uiState.isFromCache -> stringResource(R.string.cache_active)
                        else -> stringResource(R.string.sync_success)
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
            }
        },
        actions = {
            // Bouton de gestion / remise de la notification
            if (uiState.hasNotificationPermission && onRestoreNotification != null) {
                IconButton(
                    onClick = onRestoreNotification,
                    modifier = Modifier.testTag("top_bar_notif_button")
                ) {
                    if (uiState.isNotificationDismissed) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(8.dp)
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsOff,
                                contentDescription = stringResource(R.string.restore_notification),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = stringResource(R.string.notification_status_active),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val infiniteTransition = rememberInfiniteTransition(label = "rotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "refresh_spin"
            )

            IconButton(
                onClick = onRefresh,
                enabled = !uiState.isRefreshing && uiState.hasConfiguredUrl,
                modifier = Modifier.testTag("refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.refresh),
                    modifier = if (uiState.isRefreshing) Modifier.rotate(rotation) else Modifier
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}

@Composable
fun CalendarViewModeSelector(
    activeMode: CalendarViewMode,
    onSelectMode: (CalendarViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val modes = listOf(
                Triple(CalendarViewMode.DAY, "Jour", Icons.Default.Today),
                Triple(CalendarViewMode.WEEK, "Semaine", Icons.Default.ViewWeek),
                Triple(CalendarViewMode.MONTH, "Mois", Icons.Default.CalendarMonth),
                Triple(CalendarViewMode.RESOURCES, "Ressources", Icons.Default.MeetingRoom)
            )

            modes.forEach { (mode, label, icon) ->
                val isSelected = activeMode == mode
                Surface(
                    onClick = { onSelectMode(mode) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodNavigationBar(
    viewMode: CalendarViewMode,
    selectedDate: LocalDate,
    selectedYearMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onResetToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (viewMode == CalendarViewMode.RESOURCES) return

    val today = LocalDate.now()
    val isCurrent = when (viewMode) {
        CalendarViewMode.DAY -> selectedDate == today
        CalendarViewMode.WEEK -> {
            val currentMon = today.with(java.time.DayOfWeek.MONDAY)
            val selectedMon = selectedDate.with(java.time.DayOfWeek.MONDAY)
            currentMon == selectedMon
        }
        CalendarViewMode.MONTH -> selectedYearMonth == YearMonth.now()
        CalendarViewMode.RESOURCES -> true
    }

    val periodTitle = when (viewMode) {
        CalendarViewMode.DAY -> {
            val raw = selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
            raw.replaceFirstChar { it.titlecase(Locale.FRENCH) }
        }
        CalendarViewMode.WEEK -> {
            val monday = selectedDate.with(java.time.DayOfWeek.MONDAY)
            val sunday = selectedDate.with(java.time.DayOfWeek.SUNDAY)
            val monFmt = monday.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
            val sunFmt = sunday.format(DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH))
            "Semaine • $monFmt - $sunFmt"
        }
        CalendarViewMode.MONTH -> {
            val raw = selectedYearMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH))
            raw.replaceFirstChar { it.titlecase(Locale.FRENCH) }
        }
        CalendarViewMode.RESOURCES -> ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Période précédente",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = periodTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!isCurrent) {
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    onClick = onResetToday,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = "Aujourd'hui",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = onNext,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Période suivante",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
