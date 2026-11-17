package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChronoRepository
import com.example.data.LocalThemePreferences
import com.example.data.Note
import com.example.data.ScheduleItem
import com.example.data.SchedulePriority
import com.example.data.UiState
import com.example.data.UserSettings
import com.example.notifications.ReminderScheduler
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePalette
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class CalendarDayUi(
    val dateKey: String,
    val dayOfWeekShort: String,
    val dayOfMonth: String,
    val monthTitle: String,
    val fullLabel: String,
    val isToday: Boolean
)

class ChronoViewModel(
    private val repository: ChronoRepository,
    private val themePreferences: LocalThemePreferences,
    private val currentUserId: String
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("Все")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()

    val calendarDays: List<CalendarDayUi> = buildCalendarStrip()

    private val _selectedDateKey = MutableStateFlow(todayDateKey())
    val selectedDateKey: StateFlow<String> = _selectedDateKey.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var hasSeededInitialData = false

    val notesState: StateFlow<UiState<List<Note>>> = repository.observeNotes(currentUserId)
        .map<List<Note>, UiState<List<Note>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing notes", rootError)
            emit(UiState.Error(rootError.message ?: "Ошибка загрузки заметок из облака"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val scheduleState: StateFlow<UiState<List<ScheduleItem>>> = repository.observeScheduleItems(currentUserId)
        .map<List<ScheduleItem>, UiState<List<ScheduleItem>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing schedule items", rootError)
            emit(UiState.Error(rootError.message ?: "Ошибка загрузки расписания из облака"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val cloudSettingsState: StateFlow<UiState<UserSettings?>> = repository.observeUserSettings(currentUserId)
        .map<UserSettings?, UiState<UserSettings?>> { settings ->
            if (settings != null) {
                themePreferences.updateFromCloud(
                    mode = ThemeMode.fromRaw(settings.themeMode),
                    palette = ThemePalette.fromRaw(settings.paletteName),
                    notificationsEnabled = settings.notificationsEnabled
                )
            }
            UiState.Success(settings)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing user settings", rootError)
            emit(UiState.Error(rootError.message ?: "Ошибка синхронизации настроек темы"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }

    fun onDateSelected(dateKey: String) {
        _selectedDateKey.value = dateKey
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    fun showMessage(msg: String) {
        _snackbarMessage.value = msg
    }

    fun saveNote(
        context: Context,
        existingNoteId: String?,
        title: String,
        content: String,
        category: String,
        colorIndex: Int,
        pinned: Boolean,
        reminderEpochMillis: Long
    ) {
        viewModelScope.launch {
            if (existingNoteId.isNullOrBlank()) {
                val result = repository.addNote(
                    userId = currentUserId,
                    title = title,
                    content = content,
                    category = category,
                    colorIndex = colorIndex,
                    pinned = pinned,
                    reminderEpochMillis = reminderEpochMillis
                )
                result.onSuccess { newId ->
                    if (reminderEpochMillis > System.currentTimeMillis()) {
                        ReminderScheduler.scheduleReminder(
                            context = context.applicationContext,
                            uniqueKey = "note_$newId",
                            title = "Заметка: ${title.trim().ifEmpty { "Напоминание" }}",
                            message = content.trim().ifEmpty { "Откройте ChronoNote для просмотра заметки" },
                            triggerAtMillis = reminderEpochMillis
                        )
                        _snackbarMessage.value = "Заметка сохранена в облаке и напоминание установлено"
                    } else {
                        _snackbarMessage.value = "Заметка синхронизирована с облаком"
                    }
                }.onFailure { err ->
                    _snackbarMessage.value = "Ошибка сохранения заметки: ${err.localizedMessage ?: "проверьте сеть"}"
                }
            } else {
                val result = repository.updateNote(
                    userId = currentUserId,
                    noteId = existingNoteId,
                    title = title,
                    content = content,
                    category = category,
                    colorIndex = colorIndex,
                    pinned = pinned,
                    reminderEpochMillis = reminderEpochMillis
                )
                result.onSuccess {
                    if (reminderEpochMillis > System.currentTimeMillis()) {
                        ReminderScheduler.scheduleReminder(
                            context = context.applicationContext,
                            uniqueKey = "note_$existingNoteId",
                            title = "Заметка: ${title.trim().ifEmpty { "Напоминание" }}",
                            message = content.trim().ifEmpty { "Откройте ChronoNote для просмотра заметки" },
                            triggerAtMillis = reminderEpochMillis
                        )
                    } else {
                        ReminderScheduler.cancelReminder(context.applicationContext, "note_$existingNoteId")
                    }
                    _snackbarMessage.value = "Изменения сохранены в облаке"
                }.onFailure { err ->
                    _snackbarMessage.value = "Не удалось обновить заметку: ${err.localizedMessage ?: ""}"
                }
            }
        }
    }

    fun toggleNotePinned(note: Note) {
        viewModelScope.launch {
            repository.toggleNotePinned(currentUserId, note).onFailure { err ->
                _snackbarMessage.value = "Не удалось изменить закрепление: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun deleteNote(context: Context, note: Note) {
        viewModelScope.launch {
            ReminderScheduler.cancelReminder(context.applicationContext, "note_${note.id}")
            repository.deleteNote(currentUserId, note.id).onSuccess {
                _snackbarMessage.value = "Заметка удалена"
            }.onFailure { err ->
                _snackbarMessage.value = "Не удалось удалить заметку: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun saveScheduleItem(
        context: Context,
        existingItem: ScheduleItem?,
        title: String,
        description: String,
        dateKey: String,
        startTime: String,
        endTime: String,
        priority: SchedulePriority,
        reminderEnabled: Boolean
    ) {
        viewModelScope.launch {
            val computedEpoch = if (reminderEnabled) {
                computeScheduleEpochMillis(dateKey, startTime)
            } else {
                0L
            }
            if (existingItem == null || existingItem.id.isBlank()) {
                val result = repository.addScheduleItem(
                    userId = currentUserId,
                    title = title,
                    description = description,
                    dateKey = dateKey,
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    reminderEnabled = reminderEnabled,
                    reminderEpochMillis = computedEpoch
                )
                result.onSuccess { newId ->
                    if (reminderEnabled && computedEpoch > System.currentTimeMillis()) {
                        ReminderScheduler.scheduleReminder(
                            context = context.applicationContext,
                            uniqueKey = "sched_$newId",
                            title = "План на день ($startTime): $title",
                            message = description.ifBlank { "Запланированная задача в вашем расписании ChronoNote" },
                            triggerAtMillis = computedEpoch
                        )
                        _snackbarMessage.value = "Пункт плана добавлен и push-напоминание включено"
                    } else {
                        _snackbarMessage.value = "Пункт плана сохранен в облаке"
                    }
                }.onFailure { err ->
                    _snackbarMessage.value = "Ошибка сохранения плана: ${err.localizedMessage ?: ""}"
                }
            } else {
                val result = repository.updateScheduleItem(
                    userId = currentUserId,
                    itemId = existingItem.id,
                    title = title,
                    description = description,
                    dateKey = dateKey,
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    completed = existingItem.completed,
                    reminderEnabled = reminderEnabled,
                    reminderEpochMillis = computedEpoch
                )
                result.onSuccess {
                    if (reminderEnabled && computedEpoch > System.currentTimeMillis()) {
                        ReminderScheduler.scheduleReminder(
                            context = context.applicationContext,
                            uniqueKey = "sched_${existingItem.id}",
                            title = "План на день ($startTime): $title",
                            message = description.ifBlank { "Запланированная задача в вашем расписании ChronoNote" },
                            triggerAtMillis = computedEpoch
                        )
                    } else {
                        ReminderScheduler.cancelReminder(context.applicationContext, "sched_${existingItem.id}")
                    }
                    _snackbarMessage.value = "Расписание обновлено"
                }.onFailure { err ->
                    _snackbarMessage.value = "Не удалось обновить пункт плана: ${err.localizedMessage ?: ""}"
                }
            }
        }
    }

    fun toggleScheduleCompleted(item: ScheduleItem) {
        viewModelScope.launch {
            repository.toggleScheduleCompleted(currentUserId, item).onFailure { err ->
                _snackbarMessage.value = "Ошибка обновления статуса: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun deleteScheduleItem(context: Context, item: ScheduleItem) {
        viewModelScope.launch {
            ReminderScheduler.cancelReminder(context.applicationContext, "sched_${item.id}")
            repository.deleteScheduleItem(currentUserId, item.id).onSuccess {
                _snackbarMessage.value = "Пункт расписания удален"
            }.onFailure { err ->
                _snackbarMessage.value = "Не удалось удалить пункт: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun updateThemeAndSync(
        mode: ThemeMode,
        palette: ThemePalette,
        notificationsEnabled: Boolean
    ) {
        viewModelScope.launch {
            themePreferences.updateFromCloud(mode, palette, notificationsEnabled)
            val alreadyExists = (cloudSettingsState.value as? UiState.Success)?.data != null
            repository.saveUserSettings(
                userId = currentUserId,
                themeMode = mode,
                palette = palette,
                notificationsEnabled = notificationsEnabled,
                alreadyExists = alreadyExists
            ).onFailure { err ->
                Log.w(TAG, "Failed to sync theme settings to cloud", err)
            }
        }
    }

    fun seedStarterNotes() {
        viewModelScope.launch {
            repository.seedStarterNotes(currentUserId).onSuccess {
                _snackbarMessage.value = "Демо-заметки созданы и синхронизированы с облаком"
            }.onFailure { err ->
                _snackbarMessage.value = "Ошибка создания заметок: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun seedStarterScheduleForSelectedDate() {
        val dateKey = _selectedDateKey.value
        viewModelScope.launch {
            repository.seedStarterScheduleForDate(currentUserId, dateKey).onSuccess {
                _snackbarMessage.value = "План на день создан и синхронизирован с облаком"
            }.onFailure { err ->
                _snackbarMessage.value = "Ошибка создания плана: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun ensureInitialDemoDataIfEmpty(notes: List<Note>, scheduleItems: List<ScheduleItem>) {
        if (hasSeededInitialData) return
        if (notes.isEmpty() && scheduleItems.isEmpty()) {
            hasSeededInitialData = true
            viewModelScope.launch {
                repository.seedStarterNotes(currentUserId)
                repository.seedStarterScheduleForDate(currentUserId, _selectedDateKey.value)
            }
        } else {
            hasSeededInitialData = true
        }
    }

    companion object {
        private const val TAG = "ChronoVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun todayDateKey(): String {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return fmt.format(Date())
        }

        fun buildCalendarStrip(): List<CalendarDayUi> {
            val cal = Calendar.getInstance()
            val todayKey = todayDateKey()
            // Start 2 days before today and show 14 days total
            cal.add(Calendar.DAY_OF_YEAR, -2)
            val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dowFmt = SimpleDateFormat("EE", Locale.forLanguageTag("ru"))
            val dayFmt = SimpleDateFormat("dd", Locale.US)
            val monthFmt = SimpleDateFormat("d MMMM", Locale.forLanguageTag("ru"))
            val fullFmt = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru"))

            val result = mutableListOf<CalendarDayUi>()
            for (i in 0 until 14) {
                val date = cal.time
                val dKey = keyFmt.format(date)
                result.add(
                    CalendarDayUi(
                        dateKey = dKey,
                        dayOfWeekShort = dowFmt.format(date).uppercase(Locale.forLanguageTag("ru")).take(2),
                        dayOfMonth = dayFmt.format(date),
                        monthTitle = monthFmt.format(date),
                        fullLabel = fullFmt.format(date).replaceFirstChar { it.titlecase(Locale.forLanguageTag("ru")) },
                        isToday = dKey == todayKey
                    )
                )
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return result
        }

        fun computeScheduleEpochMillis(dateKey: String, startTime: String): Long {
            return try {
                val parts = dateKey.split("-")
                val timeParts = startTime.split(":")
                val cal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, parts[0].toInt())
                    set(Calendar.MONTH, parts[1].toInt() - 1)
                    set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                    set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    set(Calendar.MINUTE, timeParts[1].toInt())
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val target = cal.timeInMillis
                if (target <= System.currentTimeMillis()) {
                    // If the chosen time today has already passed, schedule a reminder in 60 seconds so user can experience it
                    System.currentTimeMillis() + 60_000L
                } else {
                    target
                }
            } catch (_: Exception) {
                System.currentTimeMillis() + 60_000L
            }
        }
    }
}
