package com.example.data

import android.content.Context
import com.example.R
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePalette
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ChronoRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAuthenticatedUid(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Необходим вход через Google для синхронизации с облаком.")
        check(uid == expectedUserId && uid.isNotBlank()) {
            "Аккаунт пользователя изменился. Повторите действие."
        }
        return uid
    }

    fun observeNotes(userId: String): Flow<List<Note>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/notes"
        emitAll(
            db.collection("users").document(userId).collection("notes")
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(
                        Note::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    list.sortedWith(
                        compareByDescending<Note> { it.pinned }
                            .thenByDescending { (it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)).seconds }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeScheduleItems(userId: String): Flow<List<ScheduleItem>> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/scheduleItems"
        emitAll(
            db.collection("users").document(userId).collection("scheduleItems")
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(
                        ScheduleItem::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    list.sortedWith(
                        compareBy<ScheduleItem> { it.completed }
                            .thenBy { it.startTime }
                            .thenBy { it.title }
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserSettings(userId: String): Flow<UserSettings?> = flow {
        require(userId.isNotBlank()) { "User ID must not be blank" }
        val path = "users/$userId/settings/preferences"
        emitAll(
            db.collection("users").document(userId).collection("settings").document("preferences")
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            UserSettings::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    suspend fun addNote(
        userId: String,
        title: String,
        content: String,
        category: String,
        colorIndex: Int,
        pinned: Boolean,
        reminderEpochMillis: Long
    ): Result<String> {
        var docPath = "users/$userId/notes"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val cleanTitle = title.trim().take(150).ifEmpty { "Без названия" }
            val cleanContent = content.trim().take(10000)
            val cleanCategory = category.trim().take(40).ifEmpty { "Личное" }
            val safeColor = colorIndex.coerceIn(0, 5)
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("users").document(uid).collection("notes").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "content" to cleanContent,
                "category" to cleanCategory,
                "colorIndex" to safeColor,
                "pinned" to pinned,
                "reminderEpochMillis" to safeReminder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        userId: String,
        noteId: String,
        title: String,
        content: String,
        category: String,
        colorIndex: Int,
        pinned: Boolean,
        reminderEpochMillis: Long
    ): Result<Unit> {
        var docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            require(noteId.isNotBlank()) { "Note ID must not be blank" }
            val cleanTitle = title.trim().take(150).ifEmpty { "Без названия" }
            val cleanContent = content.trim().take(10000)
            val cleanCategory = category.trim().take(40).ifEmpty { "Личное" }
            val safeColor = colorIndex.coerceIn(0, 5)
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "category" to cleanCategory,
                    "colorIndex" to safeColor,
                    "pinned" to pinned,
                    "reminderEpochMillis" to safeReminder,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleNotePinned(userId: String, note: Note): Result<Unit> {
        var docPath = "users/$userId/notes/${note.id}"
        return try {
            val uid = requireAuthenticatedUid(userId)
            require(note.id.isNotBlank()) { "Note ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("notes").document(note.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "pinned" to !note.pinned,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        var docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            require(noteId.isNotBlank()) { "Note ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addScheduleItem(
        userId: String,
        title: String,
        description: String,
        dateKey: String,
        startTime: String,
        endTime: String,
        priority: SchedulePriority,
        reminderEnabled: Boolean,
        reminderEpochMillis: Long
    ): Result<String> {
        var docPath = "users/$userId/scheduleItems"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val cleanTitle = title.trim().take(150).ifEmpty { "Пункт плана" }
            val cleanDesc = description.trim().take(2000)
            val safeDateKey = if (dateKey.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) dateKey else "2026-11-17"
            val safeStart = if (startTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) startTime else "09:00"
            val safeEnd = if (endTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) endTime else "10:00"
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("users").document(uid).collection("scheduleItems").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "description" to cleanDesc,
                "dateKey" to safeDateKey,
                "startTime" to safeStart,
                "endTime" to safeEnd,
                "priority" to priority.name,
                "completed" to false,
                "reminderEnabled" to reminderEnabled,
                "reminderEpochMillis" to safeReminder,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateScheduleItem(
        userId: String,
        itemId: String,
        title: String,
        description: String,
        dateKey: String,
        startTime: String,
        endTime: String,
        priority: SchedulePriority,
        completed: Boolean,
        reminderEnabled: Boolean,
        reminderEpochMillis: Long
    ): Result<Unit> {
        var docPath = "users/$userId/scheduleItems/$itemId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            require(itemId.isNotBlank()) { "ScheduleItem ID must not be blank" }
            val cleanTitle = title.trim().take(150).ifEmpty { "Пункт плана" }
            val cleanDesc = description.trim().take(2000)
            val safeDateKey = if (dateKey.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) dateKey else "2026-11-17"
            val safeStart = if (startTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) startTime else "09:00"
            val safeEnd = if (endTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) endTime else "10:00"
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("users").document(uid).collection("scheduleItems").document(itemId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "dateKey" to safeDateKey,
                    "startTime" to safeStart,
                    "endTime" to safeEnd,
                    "priority" to priority.name,
                    "completed" to completed,
                    "reminderEnabled" to reminderEnabled,
                    "reminderEpochMillis" to safeReminder,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleScheduleCompleted(userId: String, item: ScheduleItem): Result<Unit> {
        var docPath = "users/$userId/scheduleItems/${item.id}"
        return try {
            val uid = requireAuthenticatedUid(userId)
            require(item.id.isNotBlank()) { "ScheduleItem ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("scheduleItems").document(item.id)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "completed" to !item.completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteScheduleItem(userId: String, itemId: String): Result<Unit> {
        var docPath = "users/$userId/scheduleItems/$itemId"
        return try {
            val uid = requireAuthenticatedUid(userId)
            require(itemId.isNotBlank()) { "ScheduleItem ID must not be blank" }
            val docRef = db.collection("users").document(uid).collection("scheduleItems").document(itemId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun saveUserSettings(
        userId: String,
        themeMode: ThemeMode,
        palette: ThemePalette,
        notificationsEnabled: Boolean,
        alreadyExists: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/settings/preferences"
        return try {
            val uid = requireAuthenticatedUid(userId)
            val docRef = db.collection("users").document(uid).collection("settings").document("preferences")
            docPath = docRef.path
            if (alreadyExists) {
                docRef.update(
                    mapOf(
                        "themeMode" to themeMode.name,
                        "paletteName" to palette.name,
                        "notificationsEnabled" to notificationsEnabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "themeMode" to themeMode.name,
                        "paletteName" to palette.name,
                        "notificationsEnabled" to notificationsEnabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, if (alreadyExists) OperationType.UPDATE else OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedStarterNotes(userId: String): Result<Unit> {
        return try {
            val samples = listOf(
                Triple(
                    "Добро пожаловать в ChronoNote ☁️",
                    "Ваши заметки и расписание автоматически синхронизируются между всеми устройствами через облако Firebase Firestore. Попробуйте закрепить заметку, сменить цветовую карточку или включить напоминание!",
                    "Личное" to 1
                ),
                Triple(
                    "Идеи для проекта и продуктивности",
                    "• Составлять план на день с вечера во вкладке «Расписание»\n• Выделять 2 главные задачи с высоким приоритетом\n• Использовать цветовые карточки для быстрого визуального поиска",
                    "Идеи" to 2
                ),
                Triple(
                    "Список покупок на неделю",
                    "• Свежеобжаренный кофе в зернах\n• Авокадо, шпинат и томаты черри\n• Овсяное молоко и греческий йогурт\n• Орехи и темный шоколад 85%",
                    "Покупки" to 3
                )
            )
            samples.forEachIndexed { index, (title, body, meta) ->
                addNote(
                    userId = userId,
                    title = title,
                    content = body,
                    category = meta.first,
                    colorIndex = meta.second,
                    pinned = index == 0,
                    reminderEpochMillis = 0L
                ).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun seedStarterScheduleForDate(userId: String, dateKey: String): Result<Unit> {
        return try {
            data class StarterSlot(
                val title: String,
                val desc: String,
                val start: String,
                val end: String,
                val priority: SchedulePriority
            )
            val slots = listOf(
                StarterSlot(
                    title = "Утренний обзор целей и приоритетов",
                    desc = "Просмотреть расписание в календаре и ключевые заметки на день",
                    start = "09:00",
                    end = "09:30",
                    priority = SchedulePriority.HIGH
                ),
                StarterSlot(
                    title = "Глубокая работа над главной задачей",
                    desc = "Сфокусированный блок без отвлечений с включенным напоминанием",
                    start = "10:00",
                    end = "12:00",
                    priority = SchedulePriority.HIGH
                ),
                StarterSlot(
                    title = "Обед и прогулка на свежем воздухе",
                    desc = "Перезагрузка, отдых для глаз и легкая разминка",
                    start = "13:00",
                    end = "14:00",
                    priority = SchedulePriority.LOW
                ),
                StarterSlot(
                    title = "Командный созвон и синхронизация",
                    desc = "Обсуждение текущих задач и подведение промежуточных итогов",
                    start = "15:30",
                    end = "16:30",
                    priority = SchedulePriority.MEDIUM
                ),
                StarterSlot(
                    title = "Подведение итогов дня и план на завтра",
                    desc = "Отметить выполненные пункты и записать новые идеи в заметки",
                    start = "19:00",
                    end = "19:30",
                    priority = SchedulePriority.MEDIUM
                )
            )
            for (slot in slots) {
                addScheduleItem(
                    userId = userId,
                    title = slot.title,
                    description = slot.desc,
                    dateKey = dateKey,
                    startTime = slot.start,
                    endTime = slot.end,
                    priority = slot.priority,
                    reminderEnabled = false,
                    reminderEpochMillis = 0L
                ).getOrThrow()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
