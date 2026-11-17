package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import kotlin.math.min

data class PresetAvatar(
    val key: String,
    val emoji: String,
    val labelRu: String
)

val PresetAvatars = listOf(
    PresetAvatar("rocket", "🚀", "Ракета"),
    PresetAvatar("crown", "👑", "Корона"),
    PresetAvatar("sparkles", "✨", "Искра"),
    PresetAvatar("cat", "🐱", "Котик"),
    PresetAvatar("fire", "🔥", "Огонь"),
    PresetAvatar("bolt", "⚡", "Молния"),
    PresetAvatar("music", "🎧", "Музыка"),
    PresetAvatar("palette", "🎨", "Арт"),
    PresetAvatar("heart", "💜", "Сердце"),
    PresetAvatar("planet", "🪐", "Планета"),
    PresetAvatar("coffee", "☕", "Кофе"),
    PresetAvatar("star", "🌟", "Звезда")
)

fun findPresetAvatar(key: String): PresetAvatar =
    PresetAvatars.firstOrNull { it.key == key } ?: PresetAvatars.first()

@Keep
@IgnoreExtraProperties
data class MemberProfile(
    @DocumentId val id: String = "",
    val memberId: String = "",
    val nickname: String = "",
    val statusText: String = "",
    val avatarType: String = "PRESET",
    val avatarData: String = "rocket",
    val colorIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class Note(
    @DocumentId val id: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = "Личное",
    val colorIndex: Long = 0L,
    val pinned: Boolean = false,
    val reminderEpochMillis: Long = 0L,
    val authorId: String = "",
    val authorName: String = "Участник",
    val authorAvatarType: String = "PRESET",
    val authorAvatarData: String = "rocket",
    val authorColorIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class SchedulePriority(val labelRu: String) {
    HIGH("Высокий"),
    MEDIUM("Средний"),
    LOW("Обычный");

    companion object {
        fun fromRaw(raw: String?): SchedulePriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

@Keep
@IgnoreExtraProperties
data class ScheduleItem(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val dateKey: String = "",
    val startTime: String = "09:00",
    val endTime: String = "10:00",
    val priority: String = SchedulePriority.MEDIUM.name,
    val completed: Boolean = false,
    val reminderEnabled: Boolean = false,
    val reminderEpochMillis: Long = 0L,
    val authorId: String = "",
    val authorName: String = "Участник",
    val authorAvatarType: String = "PRESET",
    val authorAvatarData: String = "rocket",
    val authorColorIndex: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val priorityEnum: SchedulePriority
        get() = SchedulePriority.fromRaw(priority)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

object AvatarPhotoHelper {
    suspend fun encodeUriToBase64Thumbnail(context: Context, uri: Uri): String? =
        withContext(Dispatchers.IO) {
            try {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null
                val original = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@withContext null
                val side = min(original.width, original.height)
                val xOffset = (original.width - side) / 2
                val yOffset = (original.height - side) / 2
                val cropped = Bitmap.createBitmap(original, xOffset, yOffset, side, side)
                val scaled = Bitmap.createScaledBitmap(cropped, 128, 128, true)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 72, out)
                val encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (encoded.length <= 34000) encoded else null
            } catch (e: Exception) {
                Log.w("AvatarPhotoHelper", "Failed to encode avatar photo", e)
                null
            }
        }

    fun decodeBase64ToImageBitmap(base64Str: String): ImageBitmap? {
        if (base64Str.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64Str, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            bmp?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

val DefaultNoteCategories = listOf(
    "Все",
    "Личное",
    "Работа",
    "Идеи",
    "Учеба",
    "Покупки"
)
