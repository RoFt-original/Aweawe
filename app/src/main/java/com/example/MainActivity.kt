package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.authStateFlow
import com.example.auth.signOutUser
import com.example.data.ChronoRepository
import com.example.data.LocalThemePreferences
import com.example.data.LocalThemeState
import com.example.data.Note
import com.example.data.ScheduleItem
import com.example.data.UiState
import com.example.notifications.ReminderScheduler
import com.example.ui.NoteEditorDialog
import com.example.ui.NotesScreen
import com.example.ui.ScheduleItemEditorDialog
import com.example.ui.ScheduleScreen
import com.example.ui.ThemesAndCloudScreen
import com.example.ui.theme.ChronoNoteTheme
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePalette
import com.example.viewmodel.ChronoViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.ensureChannel(this)
        enableEdgeToEdge()
        setContent {
            ChronoNoteRootApp()
        }
    }
}

@Composable
fun ChronoNoteRootApp(auth: FirebaseAuth = Firebase.auth) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themePreferences = remember(context) { LocalThemePreferences(context.applicationContext) }
    val localThemeState by themePreferences.themeStateFlow.collectAsStateWithLifecycle(
        initialValue = LocalThemeState()
    )

    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

    ChronoNoteTheme(
        themeMode = localThemeState.themeMode,
        palette = localThemeState.palette
    ) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                currentThemeMode = localThemeState.themeMode,
                currentPalette = localThemeState.palette,
                onThemeModeChange = { mode ->
                    scope.launch { themePreferences.saveThemeMode(mode) }
                },
                onPaletteChange = { palette ->
                    scope.launch { themePreferences.savePalette(palette) }
                },
                onAuthSuccess = { /* authStateFlow updates automatically */ }
            )
        } else {
            key(user.uid) {
                MainAuthenticatedScreen(
                    currentUserId = user.uid,
                    userEmail = user.email,
                    localThemeState = localThemeState,
                    themePreferences = themePreferences
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAuthenticatedScreen(
    currentUserId: String,
    userEmail: String?,
    localThemeState: LocalThemeState,
    themePreferences: LocalThemePreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ChronoViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ChronoViewModel(
                    repository = ChronoRepository(db),
                    themePreferences = LocalThemePreferences(app),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val notesState by viewModel.notesState.collectAsStateWithLifecycle()
    val scheduleState by viewModel.scheduleState.collectAsStateWithLifecycle()
    val cloudSettingsState by viewModel.cloudSettingsState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedDateKey by viewModel.selectedDateKey.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    // Automatically seed starter notes & daily schedule on first launch if cloud is empty
    LaunchedEffect(notesState, scheduleState) {
        val notes = (notesState as? UiState.Success)?.data
        val schedule = (scheduleState as? UiState.Success)?.data
        if (notes != null && schedule != null) {
            viewModel.ensureInitialDemoDataIfEmpty(notes, schedule)
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = selectedTab != 0) {
        selectedTab = 0
    }

    var isNoteDialogOpen by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    var isScheduleDialogOpen by remember { mutableStateOf(false) }
    var editingScheduleItem by remember { mutableStateOf<ScheduleItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    var pendingPushTitle by remember { mutableStateOf<String?>(null) }
    var pendingPushBody by remember { mutableStateOf<String?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val title = pendingPushTitle ?: "ChronoNote Напоминание"
            val body = pendingPushBody ?: "Push-уведомления успешно активированы!"
            ReminderScheduler.triggerImmediateNotification(context, title, body)
            viewModel.showMessage(context.getString(R.string.test_notification_sent))
        } else {
            viewModel.showMessage("Разрешение на уведомления отклонено в настройках системы")
        }
        pendingPushTitle = null
        pendingPushBody = null
    }

    fun dispatchInstantNotification(title: String, body: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                pendingPushTitle = title
                pendingPushBody = body
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        ReminderScheduler.triggerImmediateNotification(context, title, body)
        viewModel.showMessage(context.getString(R.string.test_notification_sent))
    }

    val isDark = LocalIsDarkTheme.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (selectedTab) {
                                0 -> "ChronoNote • Заметки"
                                1 -> "Календарь и План"
                                else -> "Темы и Облако"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    // Instant 1-tap Light/Dark toggle in TopAppBar
                    IconButton(
                        onClick = {
                            val nextMode = if (isDark) ThemeMode.LIGHT else ThemeMode.DARK
                            viewModel.updateThemeAndSync(
                                mode = nextMode,
                                palette = localThemeState.palette,
                                notificationsEnabled = localThemeState.notificationsEnabled
                            )
                        },
                        modifier = Modifier.testTag("top_bar_theme_toggle")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Переключить светлую/тёмную тему"
                        )
                    }
                    // Instant 1-tap Push Reminder check in TopAppBar
                    IconButton(
                        onClick = {
                            dispatchInstantNotification(
                                title = "Напоминание ChronoNote 🔔",
                                body = "Синхронизация активна! Не забудьте проверить план на день и закрепленные заметки."
                            )
                        },
                        modifier = Modifier.testTag("top_bar_test_push")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Отправить push-напоминание"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            if (selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingNote = null
                        isNoteDialogOpen = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_add_note)) },
                    modifier = Modifier.testTag("fab_add_note")
                )
            } else if (selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        editingScheduleItem = null
                        isScheduleDialogOpen = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.fab_add_schedule)) },
                    modifier = Modifier.testTag("fab_add_schedule")
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Description else Icons.Outlined.Description,
                            contentDescription = stringResource(R.string.tab_notes)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_notes)) },
                    modifier = Modifier.testTag("nav_tab_notes")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.CalendarMonth else Icons.Outlined.CalendarMonth,
                            contentDescription = stringResource(R.string.tab_schedule)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_schedule)) },
                    modifier = Modifier.testTag("nav_tab_schedule")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 2) Icons.Filled.Palette else Icons.Outlined.Palette,
                            contentDescription = stringResource(R.string.tab_themes)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_themes)) },
                    modifier = Modifier.testTag("nav_tab_themes")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> NotesScreen(
                    notesState = notesState,
                    searchQuery = searchQuery,
                    selectedCategory = selectedCategory,
                    onSearchQueryChanged = viewModel::onSearchQueryChanged,
                    onCategorySelected = viewModel::onCategorySelected,
                    onAddNoteClick = {
                        editingNote = null
                        isNoteDialogOpen = true
                    },
                    onEditNoteClick = { note ->
                        editingNote = note
                        isNoteDialogOpen = true
                    },
                    onTogglePinClick = viewModel::toggleNotePinned,
                    onDeleteNoteClick = { note -> viewModel.deleteNote(context, note) },
                    onTriggerInstantReminder = { note ->
                        dispatchInstantNotification(
                            title = "Напоминание: ${note.title}",
                            body = note.content.ifBlank { "Категория: ${note.category}" }
                        )
                    },
                    onSeedSampleNotes = viewModel::seedStarterNotes
                )
                1 -> ScheduleScreen(
                    scheduleState = scheduleState,
                    calendarDays = viewModel.calendarDays,
                    selectedDateKey = selectedDateKey,
                    onDateSelected = viewModel::onDateSelected,
                    onAddScheduleClick = {
                        editingScheduleItem = null
                        isScheduleDialogOpen = true
                    },
                    onEditScheduleClick = { item ->
                        editingScheduleItem = item
                        isScheduleDialogOpen = true
                    },
                    onToggleCompletedClick = viewModel::toggleScheduleCompleted,
                    onDeleteScheduleClick = { item -> viewModel.deleteScheduleItem(context, item) },
                    onTriggerInstantReminder = { item ->
                        dispatchInstantNotification(
                            title = "План на день (${item.startTime} – ${item.endTime})",
                            body = "${item.title}: ${item.description.ifBlank { "Запланированная задача" }}"
                        )
                    },
                    onSeedDayPlan = viewModel::seedStarterScheduleForSelectedDate
                )
                else -> {
                    val notesCount = (notesState as? UiState.Success)?.data?.size ?: 0
                    val scheduleCount = (scheduleState as? UiState.Success)?.data?.size ?: 0
                    val activeSettings = (cloudSettingsState as? UiState.Success)?.data
                    ThemesAndCloudScreen(
                        userEmail = userEmail ?: activeSettings?.userId,
                        notesCount = notesCount,
                        scheduleCount = scheduleCount,
                        currentThemeMode = localThemeState.themeMode,
                        currentPalette = localThemeState.palette,
                        notificationsEnabled = localThemeState.notificationsEnabled,
                        onThemeModeSelected = { mode ->
                            viewModel.updateThemeAndSync(
                                mode = mode,
                                palette = localThemeState.palette,
                                notificationsEnabled = localThemeState.notificationsEnabled
                            )
                        },
                        onPaletteSelected = { palette ->
                            viewModel.updateThemeAndSync(
                                mode = localThemeState.themeMode,
                                palette = palette,
                                notificationsEnabled = localThemeState.notificationsEnabled
                            )
                        },
                        onNotificationsToggled = { enabled ->
                            viewModel.updateThemeAndSync(
                                mode = localThemeState.themeMode,
                                palette = localThemeState.palette,
                                notificationsEnabled = enabled
                            )
                        },
                        onSendTestPushNotification = {
                            dispatchInstantNotification(
                                title = "Проверка уведомлений ChronoNote ✅",
                                body = "Push-напоминания для заметок и календаря работают отлично!"
                            )
                        },
                        onSignOutClick = {
                            signOutUser(credentialManager, scope)
                        }
                    )
                }
            }
        }
    }

    if (isNoteDialogOpen) {
        NoteEditorDialog(
            initialNote = editingNote,
            onDismiss = {
                isNoteDialogOpen = false
                editingNote = null
            },
            onSave = { title, content, category, colorIndex, pinned, reminderMillis ->
                viewModel.saveNote(
                    context = context,
                    existingNoteId = editingNote?.id,
                    title = title,
                    content = content,
                    category = category,
                    colorIndex = colorIndex,
                    pinned = pinned,
                    reminderEpochMillis = reminderMillis
                )
                isNoteDialogOpen = false
                editingNote = null
            }
        )
    }

    if (isScheduleDialogOpen) {
        ScheduleItemEditorDialog(
            initialItem = editingScheduleItem,
            selectedDateKey = selectedDateKey,
            onDismiss = {
                isScheduleDialogOpen = false
                editingScheduleItem = null
            },
            onSave = { title, description, dateKey, startTime, endTime, priority, reminderEnabled ->
                viewModel.saveScheduleItem(
                    context = context,
                    existingItem = editingScheduleItem,
                    title = title,
                    description = description,
                    dateKey = dateKey,
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    reminderEnabled = reminderEnabled
                )
                isScheduleDialogOpen = false
                editingScheduleItem = null
            }
        )
    }
}
