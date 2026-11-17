package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "chrononote_theme_prefs")

data class LocalAppState(
    val isLoaded: Boolean = false,
    val profileCompleted: Boolean = false,
    val memberId: String = "",
    val nickname: String = "",
    val statusText: String = "В общем пространстве ✨",
    val avatarType: String = "PRESET",
    val avatarData: String = "rocket",
    val colorIndex: Int = 0,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val palette: ThemePalette = ThemePalette.INDIGO,
    val notificationsEnabled: Boolean = true
) {
    fun toMemberProfile(): MemberProfile = MemberProfile(
        id = memberId,
        memberId = memberId,
        nickname = nickname.ifBlank { "Участник" },
        statusText = statusText,
        avatarType = avatarType,
        avatarData = avatarData,
        colorIndex = colorIndex.toLong()
    )
}

class LocalThemePreferences(private val context: Context) {
    private val keyProfileCompleted = booleanPreferencesKey("profile_completed")
    private val keyMemberId = stringPreferencesKey("member_id")
    private val keyNickname = stringPreferencesKey("member_nickname")
    private val keyStatusText = stringPreferencesKey("member_status_text")
    private val keyAvatarType = stringPreferencesKey("member_avatar_type")
    private val keyAvatarData = stringPreferencesKey("member_avatar_data")
    private val keyColorIndex = intPreferencesKey("member_color_index")

    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyPalette = stringPreferencesKey("theme_palette")
    private val keyNotifications = booleanPreferencesKey("notifications_enabled")

    val appStateFlow: Flow<LocalAppState> = context.dataStore.data.map { prefs ->
        val existingId = prefs[keyMemberId] ?: ""
        LocalAppState(
            isLoaded = true,
            profileCompleted = prefs[keyProfileCompleted] ?: false,
            memberId = existingId,
            nickname = prefs[keyNickname] ?: "",
            statusText = prefs[keyStatusText] ?: "В общем пространстве ✨",
            avatarType = prefs[keyAvatarType] ?: "PRESET",
            avatarData = prefs[keyAvatarData] ?: "rocket",
            colorIndex = (prefs[keyColorIndex] ?: 0).coerceIn(0, 5),
            themeMode = ThemeMode.fromRaw(prefs[keyThemeMode]),
            palette = ThemePalette.fromRaw(prefs[keyPalette]),
            notificationsEnabled = prefs[keyNotifications] ?: true
        )
    }

    suspend fun saveProfile(
        nickname: String,
        statusText: String,
        avatarType: String,
        avatarData: String,
        colorIndex: Int
    ): MemberProfile {
        var resolvedId = ""
        val cleanNick = nickname.trim().take(40).ifEmpty { "Участник" }
        val cleanStatus = statusText.trim().take(80)
        val cleanType = if (avatarType == "PHOTO") "PHOTO" else "PRESET"
        val cleanData = avatarData.ifBlank { "rocket" }.take(35000)
        val cleanColor = colorIndex.coerceIn(0, 5)

        context.dataStore.edit { prefs ->
            val currentId = prefs[keyMemberId]
            resolvedId = if (currentId.isNullOrBlank()) {
                "member_" + UUID.randomUUID().toString().replace("-", "").take(12)
            } else {
                currentId
            }
            prefs[keyMemberId] = resolvedId
            prefs[keyProfileCompleted] = true
            prefs[keyNickname] = cleanNick
            prefs[keyStatusText] = cleanStatus
            prefs[keyAvatarType] = cleanType
            prefs[keyAvatarData] = cleanData
            prefs[keyColorIndex] = cleanColor
        }

        return MemberProfile(
            id = resolvedId,
            memberId = resolvedId,
            nickname = cleanNick,
            statusText = cleanStatus,
            avatarType = cleanType,
            avatarData = cleanData,
            colorIndex = cleanColor.toLong()
        )
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[keyThemeMode] = mode.name
        }
    }

    suspend fun savePalette(palette: ThemePalette) {
        context.dataStore.edit { prefs ->
            prefs[keyPalette] = palette.name
        }
    }

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[keyNotifications] = enabled
        }
    }
}
