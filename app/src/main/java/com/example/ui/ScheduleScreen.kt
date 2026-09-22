package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.CalendarSearchFilterBar
import com.example.ui.components.CalendarTopAppBar
import com.example.ui.components.CalendarViewModeSelector
import com.example.ui.components.DayScheduleView
import com.example.ui.components.MonthScheduleView
import com.example.ui.components.NotificationRestoreBanner
import com.example.ui.components.PeriodNavigationBar
import com.example.ui.components.ResourceExplorerView
import com.example.ui.components.WeekScheduleView
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }

    // Launcher de permission pour Android 13+
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onNotificationPermissionChanged(isGranted)
    }

    // Gestion du message d'erreur ou d'info
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar("Info : $msg (données locales affichées)")
            viewModel.dismissError()
        }
    }

    // Si aucune URL n'est configurée et que le cache est vide, inviter à renseigner le flux .ics
    if (uiState.isUrlLoaded && !uiState.hasConfiguredUrl && uiState.allCourses.isEmpty()) {
        IcsSetupScreen(
            modifier = modifier,
            onSaveUrl = { url, onError ->
                viewModel.saveIcsUrl(url) { success, err ->
                    if (!success && err != null) {
                        onError(err)
                    }
                }
            }
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CalendarTopAppBar(
                uiState = uiState,
                onRefresh = { viewModel.refreshFromNetwork() },
                onOpenSettings = { showSettingsDialog = true },
                onRestoreNotification = {
                    viewModel.restoreNotification()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.notification_restored_success))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Indicateur de synchronisation réseau
            if (uiState.isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }

            // Bannière de permission de notification (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !uiState.hasNotificationPermission
            ) {
                NotificationPermissionBanner(
                    onRequestPermission = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            // Bannière pour remettre la notification quand l'utilisateur l'a retirée
            NotificationRestoreBanner(
                visible = uiState.canRestoreNotification,
                onRestoreNotification = {
                    viewModel.restoreNotification()
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(context.getString(R.string.notification_restored_success))
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )

            // Bannière de cache périmé + hors ligne
            if (uiState.isCacheStaleAndOffline) {
                StaleCacheOfflineBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Sélecteur des modes de vue (Jour, Semaine, Mois, Ressources)
            CalendarViewModeSelector(
                activeMode = uiState.calendarViewMode,
                onSelectMode = { viewModel.setCalendarViewMode(it) }
            )

            // Barre de navigation temporelle (<, Titre, >, Aujourd'hui)
            PeriodNavigationBar(
                viewMode = uiState.calendarViewMode,
                selectedDate = uiState.selectedDate,
                selectedYearMonth = uiState.selectedYearMonth,
                onPrevious = { viewModel.previousPeriod() },
                onNext = { viewModel.nextPeriod() },
                onResetToday = { viewModel.resetToToday() }
            )

            // Barre de recherche et filtres par matière pour les vues Calendrier
            if (uiState.calendarViewMode != CalendarViewMode.RESOURCES) {
                CalendarSearchFilterBar(
                    uiState = uiState,
                    onQueryChange = { viewModel.setCalendarSearchQuery(it) },
                    onSelectSubject = { viewModel.setSelectedSubject(it) },
                    onClearFilters = { viewModel.clearCalendarFilters() },
                    onJumpToDate = { viewModel.jumpToCourseDate(it) }
                )
            }

            // Contenu principal selon la vue active
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.calendarViewMode) {
                    CalendarViewMode.DAY -> {
                        DayScheduleView(
                            courses = uiState.filteredDisplayedCourses,
                            now = uiState.currentTime,
                            selectedDate = uiState.selectedDate,
                            isFilterActive = uiState.isCalendarFilterActive,
                            onClearFilter = { viewModel.clearCalendarFilters() }
                        )
                    }
                    CalendarViewMode.WEEK -> {
                        WeekScheduleView(
                            weekCourses = uiState.filteredWeekCourses,
                            selectedDate = uiState.selectedDate,
                            now = uiState.currentTime,
                            onSelectDate = { viewModel.setSelectedDate(it) },
                            isFilterActive = uiState.isCalendarFilterActive,
                            onClearFilter = { viewModel.clearCalendarFilters() }
                        )
                    }
                    CalendarViewMode.MONTH -> {
                        MonthScheduleView(
                            monthCourses = uiState.filteredMonthCourses,
                            selectedYearMonth = uiState.selectedYearMonth,
                            selectedDate = uiState.selectedDate,
                            now = uiState.currentTime,
                            onSelectDate = { viewModel.setSelectedDate(it) },
                            onSwitchToDayView = { viewModel.setCalendarViewMode(CalendarViewMode.DAY) },
                            isFilterActive = uiState.isCalendarFilterActive,
                            onClearFilter = { viewModel.clearCalendarFilters() }
                        )
                    }
                    CalendarViewMode.RESOURCES -> {
                        ResourceExplorerView(
                            uiState = uiState,
                            onSearchQueryChange = { viewModel.setSearchQuery(it) },
                            onFilterChange = { viewModel.setResourceFilter(it) },
                            now = uiState.currentTime
                        )
                    }
                }
            }
        }
    }

    // Dialogue des Paramètres
    if (showSettingsDialog) {
        IcsSettingsDialog(
            currentUrl = uiState.icsUrl ?: "",
            hasNotificationPermission = uiState.hasNotificationPermission,
            isNotificationDismissed = uiState.isNotificationDismissed,
            onRestoreNotification = {
                viewModel.restoreNotification()
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.notification_restored_success))
                }
            },
            onDismiss = { showSettingsDialog = false },
            onSave = { newUrl ->
                viewModel.saveIcsUrl(newUrl) { success, _ ->
                    if (success) showSettingsDialog = false
                }
            },
            onReset = {
                viewModel.resetSchedule()
                showSettingsDialog = false
            }
        )
    }
}

/**
 * Bannière d'avertissement affichée lorsque le cache date de plus de 24 heures
 * et qu'aucune connexion internet n'est disponible.
 */
@Composable
fun StaleCacheOfflineBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag("stale_cache_offline_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.CloudOff,
                        contentDescription = stringResource(R.string.stale_cache_offline_title),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.stale_cache_offline_title),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.stale_cache_offline_desc),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                )
            }
        }
    }
}

/**
 * Bannière invitant à autoriser les notifications permanentes.
 */
@Composable
fun NotificationPermissionBanner(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.permission_notif_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = stringResource(R.string.permission_notif_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "Activer", fontSize = 12.sp)
            }
        }
    }
}

/**
 * Dialogue permettant de modifier ou effacer l'URL du flux iCal (.ics) Flop!EDT,
 * et de gérer la notification d'avancement de cours.
 */
@Composable
fun IcsSettingsDialog(
    currentUrl: String,
    hasNotificationPermission: Boolean = false,
    isNotificationDismissed: Boolean = false,
    onRestoreNotification: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "L'application synchronise vos cours directement via votre flux iCal (.ics) et les met en cache instantané :",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = {
                        urlText = it
                        errorMessage = null
                    },
                    label = { Text("URL du flux .ics") },
                    placeholder = { Text(stringResource(R.string.url_placeholder)) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Formats acceptés : https://, http:// ou webcal://",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (hasNotificationPermission && onRestoreNotification != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isNotificationDismissed) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = if (isNotificationDismissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Notification avec avancement",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isNotificationDismissed) "Notification retirée" else "Notification active",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isNotificationDismissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (isNotificationDismissed) {
                            FilledTonalButton(
                                onClick = onRestoreNotification,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("dialog_restore_notification_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.restore_notification_short),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clean = urlText.trim()
                    if (clean.isBlank()) {
                        errorMessage = "L'URL ne peut pas être vide"
                    } else if (!clean.startsWith("http://", ignoreCase = true) &&
                        !clean.startsWith("https://", ignoreCase = true) &&
                        !clean.startsWith("webcal://", ignoreCase = true)
                    ) {
                        errorMessage = "L'URL doit débuter par https:// ou webcal://"
                    } else {
                        onSave(clean)
                    }
                },
                enabled = urlText.isNotBlank()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text(stringResource(R.string.reset_default), color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.width(4.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}

/**
 * Écran d'intégration initiale invitant l'étudiant à saisir son URL .ics Flop!EDT.
 */
@Composable
fun IcsSetupScreen(
    onSaveUrl: (String, (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var urlText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 480.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.EventNote,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.ics_config_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = stringResource(R.string.ics_config_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    OutlinedTextField(
                        value = urlText,
                        onValueChange = {
                            urlText = it
                            errorMessage = null
                        },
                        label = { Text("URL du flux .ics") },
                        placeholder = { Text(stringResource(R.string.url_placeholder)) },
                        singleLine = true,
                        isError = errorMessage != null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Échec de connexion",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val clean = urlText.trim()
                            if (clean.isBlank()) {
                                errorMessage = "Veuillez coller votre URL de calendrier"
                                return@Button
                            }
                            isSaving = true
                            onSaveUrl(clean) { err ->
                                isSaving = false
                                errorMessage = err
                            }
                        },
                        enabled = urlText.isNotBlank() && !isSaving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.ics_validate_button))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = {
                            urlText = "https://edt.flop.org/export/schedule.ics"
                            errorMessage = null
                        }
                    ) {
                        Text(stringResource(R.string.ics_demo_button), fontSize = 12.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚡ Zéro latence : vos cours sont mis en cache localement et s'affichent instantanément à l'ouverture.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
