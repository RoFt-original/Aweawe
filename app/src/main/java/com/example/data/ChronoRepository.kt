package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Timestamp
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
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    fun observeMembers(): Flow<List<MemberProfile>> = flow {
        val path = "shared_members"
        emitAll(
            db.collection(path)
                .snapshots()
                .map { snapshot ->
                    val list = snapshot.toObjects(
                        MemberProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                    list.sortedByDescending {
                        (it.updatedAt ?: it.createdAt ?: Timestamp(0, 0)).seconds
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeNotes(): Flow<List<Note>> = flow {
        val path = "shared_notes"
        emitAll(
            db.collection(path)
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
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeScheduleItems(): Flow<List<ScheduleItem>> = flow {
        val path = "shared_schedule"
        emitAll(
            db.collection(path)
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
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertMemberProfile(profile: MemberProfile): Result<Unit> {
        val cleanId = profile.memberId.trim().ifEmpty { "member_default" }
        val docPath = "shared_members/$cleanId"
        return try {
            val docRef = db.collection("shared_members").document(cleanId)
            val snap = docRef.get().await()
            val cleanNickname = profile.nickname.trim().take(40).ifEmpty { "Участник" }
            val cleanStatus = profile.statusText.trim().take(80)
            val cleanAvatarType = if (profile.avatarType == "PHOTO") "PHOTO" else "PRESET"
            val cleanAvatarData = profile.avatarData.ifBlank { "rocket" }.take(35000)
            val cleanColor = profile.colorIndex.coerceIn(0L, 5L)

            if (snap.exists()) {
                docRef.update(
                    mapOf(
                        "nickname" to cleanNickname,
                        "statusText" to cleanStatus,
                        "avatarType" to cleanAvatarType,
                        "avatarData" to cleanAvatarData,
                        "colorIndex" to cleanColor,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "memberId" to cleanId,
                        "nickname" to cleanNickname,
                        "statusText" to cleanStatus,
                        "avatarType" to cleanAvatarType,
                        "avatarData" to cleanAvatarData,
                        "colorIndex" to cleanColor,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addNote(
        author: MemberProfile,
        title: String,
        content: String,
        category: String,
        colorIndex: Int,
        pinned: Boolean,
        reminderEpochMillis: Long
    ): Result<String> {
        var docPath = "shared_notes"
        return try {
            val cleanTitle = title.trim().take(150).ifEmpty { "Без названия" }
            val cleanContent = content.trim().take(10000)
            val cleanCategory = category.trim().take(40).ifEmpty { "Личное" }
            val safeColor = colorIndex.coerceIn(0, 5)
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("shared_notes").document()
            docPath = docRef.path
            val payload = mapOf(
                "title" to cleanTitle,
                "content" to cleanContent,
                "category" to cleanCategory,
                "colorIndex" to safeColor,
                "pinned" to pinned,
                "reminderEpochMillis" to safeReminder,
                "authorId" to author.memberId.ifBlank { "member_default" },
                "authorName" to author.nickname.trim().take(40).ifEmpty { "Участник" },
                "authorAvatarType" to if (author.avatarType == "PHOTO") "PHOTO" else "PRESET",
                "authorAvatarData" to author.avatarData.ifBlank { "rocket" }.take(35000),
                "authorColorIndex" to author.colorIndex.coerceIn(0L, 5L),
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
        author: MemberProfile,
        noteId: String,
        title: String,
        content: String,
        category: String,
        colorIndex: Int,
        pinned: Boolean,
        reminderEpochMillis: Long
    ): Result<Unit> {
        var docPath = "shared_notes/$noteId"
        return try {
            require(noteId.isNotBlank()) { "Note ID must not be blank" }
            val cleanTitle = title.trim().take(150).ifEmpty { "Без названия" }
            val cleanContent = content.trim().take(10000)
            val cleanCategory = category.trim().take(40).ifEmpty { "Личное" }
            val safeColor = colorIndex.coerceIn(0, 5)
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("shared_notes").document(noteId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "category" to cleanCategory,
                    "colorIndex" to safeColor,
                    "pinned" to pinned,
                    "reminderEpochMillis" to safeReminder,
                    "authorId" to author.memberId.ifBlank { "member_default" },
                    "authorName" to author.nickname.trim().take(40).ifEmpty { "Участник" },
                    "authorAvatarType" to if (author.avatarType == "PHOTO") "PHOTO" else "PRESET",
                    "authorAvatarData" to author.avatarData.ifBlank { "rocket" }.take(35000),
                    "authorColorIndex" to author.colorIndex.coerceIn(0L, 5L),
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

    suspend fun toggleNotePinned(note: Note): Result<Unit> {
        var docPath = "shared_notes/${note.id}"
        return try {
            require(note.id.isNotBlank()) { "Note ID must not be blank" }
            val docRef = db.collection("shared_notes").document(note.id)
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

    suspend fun deleteNote(noteId: String): Result<Unit> {
        var docPath = "shared_notes/$noteId"
        return try {
            require(noteId.isNotBlank()) { "Note ID must not be blank" }
            val docRef = db.collection("shared_notes").document(noteId)
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
        author: MemberProfile,
        title: String,
        description: String,
        dateKey: String,
        startTime: String,
        endTime: String,
        priority: SchedulePriority,
        reminderEnabled: Boolean,
        reminderEpochMillis: Long
    ): Result<String> {
        var docPath = "shared_schedule"
        return try {
            val cleanTitle = title.trim().take(150).ifEmpty { "Пункт плана" }
            val cleanDesc = description.trim().take(2000)
            val safeDateKey = if (dateKey.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) dateKey else "2026-11-17"
            val safeStart = if (startTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) startTime else "09:00"
            val safeEnd = if (endTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) endTime else "10:00"
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("shared_schedule").document()
            docPath = docRef.path
            val payload = mapOf(
                "title" to cleanTitle,
                "description" to cleanDesc,
                "dateKey" to safeDateKey,
                "startTime" to safeStart,
                "endTime" to safeEnd,
                "priority" to priority.name,
                "completed" to false,
                "reminderEnabled" to reminderEnabled,
                "reminderEpochMillis" to safeReminder,
                "authorId" to author.memberId.ifBlank { "member_default" },
                "authorName" to author.nickname.trim().take(40).ifEmpty { "Участник" },
                "authorAvatarType" to if (author.avatarType == "PHOTO") "PHOTO" else "PRESET",
                "authorAvatarData" to author.avatarData.ifBlank { "rocket" }.take(35000),
                "authorColorIndex" to author.colorIndex.coerceIn(0L, 5L),
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
        author: MemberProfile,
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
        var docPath = "shared_schedule/$itemId"
        return try {
            require(itemId.isNotBlank()) { "ScheduleItem ID must not be blank" }
            val cleanTitle = title.trim().take(150).ifEmpty { "Пункт плана" }
            val cleanDesc = description.trim().take(2000)
            val safeDateKey = if (dateKey.matches(Regex("^[0-9]{4}-[0-9]{2}-[0-9]{2}$"))) dateKey else "2026-11-17"
            val safeStart = if (startTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) startTime else "09:00"
            val safeEnd = if (endTime.matches(Regex("^[0-9]{2}:[0-9]{2}$"))) endTime else "10:00"
            val safeReminder = reminderEpochMillis.coerceAtLeast(0L)

            val docRef = db.collection("shared_schedule").document(itemId)
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
                    "authorId" to author.memberId.ifBlank { "member_default" },
                    "authorName" to author.nickname.trim().take(40).ifEmpty { "Участник" },
                    "authorAvatarType" to if (author.avatarType == "PHOTO") "PHOTO" else "PRESET",
                    "authorAvatarData" to author.avatarData.ifBlank { "rocket" }.take(35000),
                    "authorColorIndex" to author.colorIndex.coerceIn(0L, 5L),
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

    suspend fun toggleScheduleCompleted(item: ScheduleItem): Result<Unit> {
        var docPath = "shared_schedule/${item.id}"
        return try {
            require(item.id.isNotBlank()) { "ScheduleItem ID must not be blank" }
            val docRef = db.collection("shared_schedule").document(item.id)
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

    suspend fun deleteScheduleItem(itemId: String): Result<Unit> {
        var docPath = "shared_schedule/$itemId"
        return try {
            require(itemId.isNotBlank()) { "ScheduleItem ID must not be blank" }
            val docRef = db.collection("shared_schedule").document(itemId)
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

    suspend fun propagateAuthorProfileToItems(
        profile: MemberProfile,
        notes: List<Note>,
        scheduleItems: List<ScheduleItem>
    ) {
        val cleanNick = profile.nickname.trim().take(40).ifEmpty { "Участник" }
        val cleanType = if (profile.avatarType == "PHOTO") "PHOTO" else "PRESET"
        val cleanData = profile.avatarData.ifBlank { "rocket" }.take(35000)
        val cleanColor = profile.colorIndex.coerceIn(0L, 5L)

        for (note in notes.filter { it.authorId == profile.memberId }) {
            try {
                db.collection("shared_notes").document(note.id).update(
                    mapOf(
                        "authorName" to cleanNick,
                        "authorAvatarType" to cleanType,
                        "authorAvatarData" to cleanData,
                        "authorColorIndex" to cleanColor,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }
        }

        for (item in scheduleItems.filter { it.authorId == profile.memberId }) {
            try {
                db.collection("shared_schedule").document(item.id).update(
                    mapOf(
                        "authorName" to cleanNick,
                        "authorAvatarType" to cleanType,
                        "authorAvatarData" to cleanData,
                        "authorColorIndex" to cleanColor,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } catch (_: Exception) {
            }
        }
    }

    suspend fun seedStarterNotes(author: MemberProfile): Result<Unit> {
        return try {
            val samples = listOf(
                Triple(
                    "Наше общее пространство ChronoNote 🚀",
                    "База данных уже вшита и синхронизируется между всеми участниками в реальном времени! У каждого участника равные права на создание и редактирование заметок и плана на день, а на карточках отображается ваш ник и аватарка.",
                    "Личное" to 1
                ),
                Triple(
                    "Идеи и общие задачи команды",
                    "• Каждый участник может поменять свой ник, статус и фото профиля в любой момент\n• Закрепляйте самые важные заметки наверху\n• Добавляйте задачи в общее расписание календаря с Push-напоминаниями",
                    "Идеи" to 2
                ),
                Triple(
                    "Общий список покупок и планов",
                    "• Свежеобжаренный кофе в зернах\n• Фрукты, ягоды и закуски для встречи\n• Проверить расписание на выходные",
                    "Покупки" to 3
                )
            )
            samples.forEachIndexed { index, (title, body, meta) ->
                addNote(
                    author = author,
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

    suspend fun seedStarterScheduleForDate(author: MemberProfile, dateKey: String): Result<Unit> {
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
                    title = "Утренняя сверка общего плана на день",
                    desc = "Просмотреть задачи участников и ключевые заметки пространства",
                    start = "09:00",
                    end = "09:30",
                    priority = SchedulePriority.HIGH
                ),
                StarterSlot(
                    title = "Фокус-блок по главным задачам",
                    desc = "Работа по приоритетным пунктам с уведомлением",
                    start = "10:00",
                    end = "12:00",
                    priority = SchedulePriority.HIGH
                ),
                StarterSlot(
                    title = "Перерыв и отдых",
                    desc = "Обед, прогулка и перезагрузка",
                    start = "13:00",
                    end = "14:00",
                    priority = SchedulePriority.LOW
                ),
                StarterSlot(
                    title = "Совместное обсуждение идей",
                    desc = "Обновить общие заметки и распределить планы на вечер",
                    start = "16:00",
                    end = "17:00",
                    priority = SchedulePriority.MEDIUM
                ),
                StarterSlot(
                    title = "Итоги дня и план на завтра",
                    desc = "Отметить выполненные задачи в общем календаре",
                    start = "19:30",
                    end = "20:00",
                    priority = SchedulePriority.MEDIUM
                )
            )
            for (slot in slots) {
                addScheduleItem(
                    author = author,
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
