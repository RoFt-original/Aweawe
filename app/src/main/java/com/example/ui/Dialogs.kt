package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.DefaultNoteCategories
import com.example.data.Note
import com.example.data.ScheduleItem
import com.example.data.SchedulePriority
import com.example.ui.theme.NoteCardStyles
import java.util.Calendar

private data class ReminderPreset(
    val label: String,
    val offsetMillis: Long // 0 = off, >0 = relative offset from now, -1 = keep existing
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteEditorDialog(
    initialNote: Note?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        content: String,
        category: String,
        colorIndex: Int,
        pinned: Boolean,
        reminderEpochMillis: Long
    ) -> Unit
) {
    var title by remember(initialNote) { mutableStateOf(initialNote?.title ?: "") }
    var content by remember(initialNote) { mutableStateOf(initialNote?.content ?: "") }
    val categories = remember { DefaultNoteCategories.filter { it != "Все" } }
    var selectedCategory by remember(initialNote) {
        mutableStateOf(initialNote?.category?.takeIf { it.isNotBlank() } ?: "Личное")
    }
    var selectedColorIndex by remember(initialNote) {
        mutableIntStateOf((initialNote?.colorIndex ?: 0L).toInt().coerceIn(0, 5))
    }
    var pinned by remember(initialNote) { mutableStateOf(initialNote?.pinned ?: false) }

    val presets = remember {
        listOf(
            ReminderPreset("Выкл", 0L),
            ReminderPreset("Через 1 мин", 60_000L),
            ReminderPreset("Через 15 мин", 15 * 60_000L),
            ReminderPreset("Через 1 час", 60 * 60_000L),
            ReminderPreset("Завтра 09:00", -2L)
        )
    }
    var selectedPresetIndex by remember(initialNote) {
        mutableIntStateOf(if ((initialNote?.reminderEpochMillis ?: 0L) > System.currentTimeMillis()) 1 else 0)
    }
    var customReminderMillis by remember(initialNote) {
        mutableLongStateOf(initialNote?.reminderEpochMillis ?: 0L)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialNote == null) "Новая заметка" else "Редактировать заметку",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(150) },
                    label = { Text("Заголовок заметки") },
                    placeholder = { Text("Например: Идеи для проекта") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it.take(10000) },
                    label = { Text("Текст заметки") },
                    placeholder = { Text("Запишите важные мысли, чек-лист или конспект…") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )

                Text(
                    text = "Категория",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }

                Text(
                    text = "Цвет карточки",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteCardStyles.forEachIndexed { idx, style ->
                        val isSelected = selectedColorIndex == idx
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(style.badgeColor)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = idx }
                                .testTag("note_color_$idx"),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = style.labelRu,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Закрепить",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Закрепить наверху",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Switch(
                        checked = pinned,
                        onCheckedChange = { pinned = it },
                        modifier = Modifier.testTag("note_pin_switch")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = "Push-напоминание",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Push-напоминание",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.forEachIndexed { idx, preset ->
                        FilterChip(
                            selected = selectedPresetIndex == idx,
                            onClick = {
                                selectedPresetIndex = idx
                                customReminderMillis = when (preset.offsetMillis) {
                                    0L -> 0L
                                    -2L -> {
                                        Calendar.getInstance().apply {
                                            add(Calendar.DAY_OF_YEAR, 1)
                                            set(Calendar.HOUR_OF_DAY, 9)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                        }.timeInMillis
                                    }
                                    else -> System.currentTimeMillis() + preset.offsetMillis
                                }
                            },
                            label = { Text(preset.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.ifBlank { "Без названия" },
                        content,
                        selectedCategory,
                        selectedColorIndex,
                        pinned,
                        if (selectedPresetIndex == 0) 0L else customReminderMillis
                    )
                },
                modifier = Modifier.testTag("save_note_button")
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleItemEditorDialog(
    initialItem: ScheduleItem?,
    selectedDateKey: String,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        dateKey: String,
        startTime: String,
        endTime: String,
        priority: SchedulePriority,
        reminderEnabled: Boolean
    ) -> Unit
) {
    var title by remember(initialItem) { mutableStateOf(initialItem?.title ?: "") }
    var description by remember(initialItem) { mutableStateOf(initialItem?.description ?: "") }
    var startTime by remember(initialItem) { mutableStateOf(initialItem?.startTime ?: "09:00") }
    var endTime by remember(initialItem) { mutableStateOf(initialItem?.endTime ?: "10:00") }
    var priority by remember(initialItem) {
        mutableStateOf(initialItem?.priorityEnum ?: SchedulePriority.MEDIUM)
    }
    var reminderEnabled by remember(initialItem) {
        mutableStateOf(initialItem?.reminderEnabled ?: true)
    }

    val quickTimeSlots = remember {
        listOf(
            "08:00" to "09:00",
            "09:00" to "10:00",
            "11:00" to "12:30",
            "14:00" to "15:00",
            "16:00" to "17:30",
            "19:00" to "20:00"
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialItem == null) "Новый пункт плана на день" else "Изменить пункт плана",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it.take(150) },
                    label = { Text("Название задачи или события") },
                    placeholder = { Text("Например: Командный созвон") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_title_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(2000) },
                    label = { Text("Детали или подзадачи") },
                    placeholder = { Text("Что нужно подготовить или обсудить…") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("schedule_desc_input")
                )

                Text(
                    text = "Быстрый выбор времени",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickTimeSlots.forEach { (start, end) ->
                        FilterChip(
                            selected = startTime == start && endTime == end,
                            onClick = {
                                startTime = start
                                endTime = end
                            },
                            label = { Text("$start – $end") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it.take(5) },
                        label = { Text("Начало (ЧЧ:ММ)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it.take(5) },
                        label = { Text("Конец (ЧЧ:ММ)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = "Приоритет задачи",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SchedulePriority.entries.forEach { prio ->
                        FilterChip(
                            selected = priority == prio,
                            onClick = { priority = prio },
                            label = { Text(prio.labelRu) }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = "Push-уведомление",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Push-напоминание",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "Уведомить к началу слота ($startTime)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        modifier = Modifier.testTag("schedule_reminder_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title.ifBlank { "Пункт плана" },
                        description,
                        initialItem?.dateKey?.takeIf { it.isNotBlank() } ?: selectedDateKey,
                        startTime,
                        endTime,
                        priority,
                        reminderEnabled
                    )
                },
                modifier = Modifier.testTag("save_schedule_button")
            ) {
                Text("Сохранить в план")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
