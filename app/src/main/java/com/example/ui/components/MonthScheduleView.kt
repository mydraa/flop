package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CourseEvent
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun MonthScheduleView(
    monthCourses: Map<LocalDate, List<CourseEvent>>,
    selectedYearMonth: YearMonth,
    selectedDate: LocalDate,
    now: LocalDateTime,
    onSelectDate: (LocalDate) -> Unit,
    onSwitchToDayView: () -> Unit,
    modifier: Modifier = Modifier,
    isFilterActive: Boolean = false,
    onClearFilter: () -> Unit = {}
) {
    val today = LocalDate.now()
    val firstDayOfMonth = selectedYearMonth.atDay(1)
    val daysInMonth = selectedYearMonth.lengthOfMonth()

    // Lundi = 1, Dimanche = 7 -> Décalage avant le 1er jour du mois
    val startDayOfWeek = firstDayOfMonth.dayOfWeek.value // 1..7 (Lun..Dim)
    val leadingEmptyCells = startDayOfWeek - 1

    val dayHeaders = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

    val selectedDayCourses = monthCourses[selectedDate] ?: emptyList()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Grille du Calendrier
        item(key = "calendar_matrix") {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // En-têtes de colonnes (Lun - Dim)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        dayHeaders.forEach { header ->
                            Text(
                                text = header,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Lignes de cellules
                    val totalCells = leadingEmptyCells + daysInMonth
                    val rowsCount = (totalCells + 6) / 7

                    for (row in 0 until rowsCount) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0..6) {
                                val cellIndex = row * 7 + col
                                val dayNumber = cellIndex - leadingEmptyCells + 1

                                if (cellIndex < leadingEmptyCells || dayNumber > daysInMonth) {
                                    // Case vide hors du mois
                                    Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    val cellDate = selectedYearMonth.atDay(dayNumber)
                                    val isSelected = cellDate == selectedDate
                                    val isToday = cellDate == today
                                    val courseCount = monthCourses[cellDate]?.size ?: 0

                                    Surface(
                                        onClick = { onSelectDate(cellDate) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> Color.Transparent
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(2.dp)
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Text(
                                                text = dayNumber.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSelected -> Color.White
                                                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )

                                            // Pastilles indicatrices de cours
                                            if (courseCount > 0) {
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    val dotColor = if (isSelected) Color.White else MaterialTheme.colorScheme.primary
                                                    repeat(courseCount.coerceAtMost(3)) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(dotColor)
                                                                .padding(horizontal = 0.5.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(1.5.dp))
                                                    }
                                                }
                                            } else {
                                                Spacer(modifier = Modifier.height(6.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item(key = "day_preview_header") {
            Spacer(modifier = Modifier.height(16.dp))
            val raw = selectedDate.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH))
            val title = raw.replaceFirstChar { it.titlecase(Locale.FRENCH) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (selectedDayCourses.isEmpty()) "Aucun cours ce jour" else "${selectedDayCourses.size} cours programmés",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (selectedDayCourses.isNotEmpty()) {
                    TextButton(onClick = onSwitchToDayView) {
                        Text("Vue Jour")
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (selectedDayCourses.isEmpty()) {
            item(key = "empty_selected_month_day") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isFilterActive) "Aucun cours ne correspond aux filtres ce jour."
                            else "Aucun cours n'est prévu pour cette journée.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (isFilterActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            TextButton(onClick = onClearFilter) {
                                Text("Effacer les filtres", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        } else {
            items(selectedDayCourses, key = { "month_detail_${it.id}" }) { course ->
                CourseCard(
                    course = course,
                    now = now,
                    isHeroOngoing = course.isCurrentlyOngoing(now),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
