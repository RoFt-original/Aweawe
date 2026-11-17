package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ChronoRepository
import com.example.data.LocalThemePreferences
import com.example.data.MemberProfile
import com.example.data.Note
import com.example.data.ScheduleItem
import com.example.data.SchedulePriority
import com.example.data.UiState
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
    private val themePreferences: LocalThemePreferences
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
    private var lastSyncedMemberId = ""

    val membersState: StateFlow<UiState<List<MemberProfile>>> = repository.observeMembers()
        .map<List<MemberProfile>, UiState<List<MemberProfile>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing members", rootError)
            emit(UiState.Error(rootError.message ?: "Ошибка синхронизации списка участников"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val notesState: StateFlow<UiState<List<Note>>> = repository.observeNotes()
        .map<List<Note>, UiState<List<Note>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing shared notes", rootError)
            emit(UiState.Error(rootError.message ?: "Ошибка загрузки общих заметок"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading
        )

    val scheduleState: StateFlow<UiState<List<ScheduleItem>>> = repository.observeScheduleItems()
        .map<List<ScheduleItem>, UiState<List<ScheduleItem>>> { list ->
            UiState.Success(list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing shared schedule", rootError)
            emit(UiState.Error(rootError.message ?: "Ошибка загрузки общего расписания"))
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

    fun syncOwnProfileOnStartup(profile: MemberProfile) {
        if (profile.memberId.isBlank() || lastSyncedMemberId == profile.memberId) return
        lastSyncedMemberId = profile.memberId
        viewModelScope.launch {
            repository.upsertMemberProfile(profile)
        }
    }

    fun saveAndSyncProfile(
        nickname: String,
        statusText: String,
        avatarType: String,
        avatarData: String,
        colorIndex: Int
    ) {
        viewModelScope.launch {
            val savedProfile = themePreferences.saveProfile(
                nickname = nickname,
                statusText = statusText,
                avatarType = avatarType,
                avatarData = avatarData,
                colorIndex = colorIndex
            )
            lastSyncedMemberId = savedProfile.memberId
            repository.upsertMemberProfile(savedProfile).onSuccess {
                val notes = (notesState.value as? UiState.Success)?.data ?: emptyList()
                val sched = (scheduleState.value as? UiState.Success)?.data ?: emptyList()
                repository.propagateAuthorProfileToItems(savedProfile, notes, sched)
                _snackbarMessage.value = "Профиль «${savedProfile.nickname}» обновлён для всех участников!"
            }.onFailure {
                _snackbarMessage.value = "Профиль сохранён"
            }
        }
    }

    fun saveNote(
        context: Context,
        author: MemberProfile,
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
                    author = author,
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
                            title = "Заметка (${author.nickname}): ${title.trim().ifEmpty { "Напоминание" }}",
                            message = content.trim().ifEmpty { "Откройте общее пространство ChronoNote" },
                            triggerAtMillis = reminderEpochMillis
                        )
                        _snackbarMessage.value = "Заметка добавлена в общее пространство с напоминанием"
                    } else {
                        _snackbarMessage.value = "Заметка синхронизирована со всеми участниками"
                    }
                }.onFailure { err ->
                    _snackbarMessage.value = "Ошибка сохранения заметки: ${err.localizedMessage ?: ""}"
                }
            } else {
                val result = repository.updateNote(
                    author = author,
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
                            title = "Заметка (${author.nickname}): ${title.trim().ifEmpty { "Напоминание" }}",
                            message = content.trim().ifEmpty { "Откройте общее пространство ChronoNote" },
                            triggerAtMillis = reminderEpochMillis
                        )
                    } else {
                        ReminderScheduler.cancelReminder(context.applicationContext, "note_$existingNoteId")
                    }
                    _snackbarMessage.value = "Заметка обновлена в общем пространстве"
                }.onFailure { err ->
                    _snackbarMessage.value = "Не удалось обновить заметку: ${err.localizedMessage ?: ""}"
                }
            }
        }
    }

    fun toggleNotePinned(note: Note) {
        viewModelScope.launch {
            repository.toggleNotePinned(note).onFailure { err ->
                _snackbarMessage.value = "Не удалось изменить закрепление: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun deleteNote(context: Context, note: Note) {
        viewModelScope.launch {
            ReminderScheduler.cancelReminder(context.applicationContext, "note_${note.id}")
            repository.deleteNote(note.id).onSuccess {
                _snackbarMessage.value = "Заметка удалена из общего пространства"
            }.onFailure { err ->
                _snackbarMessage.value = "Не удалось удалить заметку: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun saveScheduleItem(
        context: Context,
        author: MemberProfile,
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
                    author = author,
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
                            title = "Общий план ($startTime): $title",
                            message = description.ifBlank { "Автор: ${author.nickname}" },
                            triggerAtMillis = computedEpoch
                        )
                        _snackbarMessage.value = "Задача добавлена в общий план и напоминание включено"
                    } else {
                        _snackbarMessage.value = "Задача синхронизирована в общем календаре"
                    }
                }.onFailure { err ->
                    _snackbarMessage.value = "Ошибка сохранения плана: ${err.localizedMessage ?: ""}"
                }
            } else {
                val result = repository.updateScheduleItem(
                    author = author,
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
                            title = "Общий план ($startTime): $title",
                            message = description.ifBlank { "Автор: ${author.nickname}" },
                            triggerAtMillis = computedEpoch
                        )
                    } else {
                        ReminderScheduler.cancelReminder(context.applicationContext, "sched_${existingItem.id}")
                    }
                    _snackbarMessage.value = "Общее расписание обновлено"
                }.onFailure { err ->
                    _snackbarMessage.value = "Не удалось обновить задачу: ${err.localizedMessage ?: ""}"
                }
            }
        }
    }

    fun toggleScheduleCompleted(item: ScheduleItem) {
        viewModelScope.launch {
            repository.toggleScheduleCompleted(item).onFailure { err ->
                _snackbarMessage.value = "Ошибка обновления статуса: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun deleteScheduleItem(context: Context, item: ScheduleItem) {
        viewModelScope.launch {
            ReminderScheduler.cancelReminder(context.applicationContext, "sched_${item.id}")
            repository.deleteScheduleItem(item.id).onSuccess {
                _snackbarMessage.value = "Задача удалена из расписания"
            }.onFailure { err ->
                _snackbarMessage.value = "Не удалось удалить задачу: ${err.localizedMessage ?: ""}"
            }
        }
    }

    fun updateThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            themePreferences.saveThemeMode(mode)
        }
    }

    fun updatePalette(palette: ThemePalette) {
        viewModelScope.launch {
            themePreferences.savePalette(palette)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            themePreferences.saveNotificationsEnabled(enabled)
        }
    }

    fun seedStarterNotes(author: MemberProfile) {
        viewModelScope.launch {
            repository.seedStarterNotes(author).onSuccess {
                _snackbarMessage.value = "Демо-заметки добавлены в общее пространство"
            }
        }
    }

    fun seedStarterScheduleForSelectedDate(author: MemberProfile) {
        val dateKey = _selectedDateKey.value
        viewModelScope.launch {
            repository.seedStarterScheduleForDate(author, dateKey).onSuccess {
                _snackbarMessage.value = "План на день создан в общем календаре"
            }
        }
    }

    fun ensureInitialDemoDataIfEmpty(
        author: MemberProfile,
        notes: List<Note>,
        scheduleItems: List<ScheduleItem>
    ) {
        if (hasSeededInitialData || author.memberId.isBlank()) return
        if (notes.isEmpty() && scheduleItems.isEmpty()) {
            hasSeededInitialData = true
            viewModelScope.launch {
                repository.seedStarterNotes(author)
                repository.seedStarterScheduleForDate(author, _selectedDateKey.value)
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
