package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class ThemeMode(val titleRu: String, val subtitleRu: String) {
    SYSTEM("Системная", "Следует настройкам вашего устройства"),
    LIGHT("Светлая", "Чистый дневной интерфейс с мягкими тенями"),
    DARK("Тёмная", "Глубокий контраст и комфорт для глаз ночью");

    companion object {
        fun fromRaw(raw: String?): ThemeMode =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

enum class ThemePalette(
    val titleRu: String,
    val descriptionRu: String,
    val previewPrimary: Color,
    val previewSecondary: Color,
    val previewTertiary: Color
) {
    INDIGO(
        titleRu = "Полуночный Индиго",
        descriptionRu = "Благородный индиго с фиолетовыми и янтарными акцентами",
        previewPrimary = Color(0xFF4F46E5),
        previewSecondary = Color(0xFF7C3AED),
        previewTertiary = Color(0xFFF59E0B)
    ),
    EMERALD(
        titleRu = "Изумрудный Сад",
        descriptionRu = "Свежие природные тона шалфея и малахита для концентрации",
        previewPrimary = Color(0xFF059669),
        previewSecondary = Color(0xFF0D9488),
        previewTertiary = Color(0xFFD97706)
    ),
    AMBER(
        titleRu = "Тёплый Янтарь",
        descriptionRu = "Уютная солнечная палитра с терракотовыми оттенками",
        previewPrimary = Color(0xFFD97706),
        previewSecondary = Color(0xFFEA580C),
        previewTertiary = Color(0xFF2563EB)
    ),
    ROSE(
        titleRu = "Розовый Кварц",
        descriptionRu = "Выразительные ягодные и коралловые тона для вдохновения",
        previewPrimary = Color(0xFFE11D48),
        previewSecondary = Color(0xFFDB2777),
        previewTertiary = Color(0xFF7C3AED)
    ),
    OCEAN(
        titleRu = "Тихий Океан",
        descriptionRu = "Кристальный сапфир и морская бирюза для ясного планирования",
        previewPrimary = Color(0xFF0284C7),
        previewSecondary = Color(0xFF0891B2),
        previewTertiary = Color(0xFF6366F1)
    );

    companion object {
        fun fromRaw(raw: String?): ThemePalette =
            entries.firstOrNull { it.name == raw } ?: INDIGO
    }
}

fun buildLightColorScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.INDIGO -> lightColorScheme(
        primary = Color(0xFF4338CA),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE0E7FF),
        onPrimaryContainer = Color(0xFF1E1B4B),
        secondary = Color(0xFF6D28D9),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFEDE9FE),
        onSecondaryContainer = Color(0xFF2E1065),
        tertiary = Color(0xFFD97706),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFEF3C7),
        onTertiaryContainer = Color(0xFF451A03),
        background = Color(0xFFF8FAFC),
        onBackground = Color(0xFF0F172A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F172A),
        surfaceVariant = Color(0xFFF1F5F9),
        onSurfaceVariant = Color(0xFF475569),
        outline = Color(0xFFCBD5E1)
    )
    ThemePalette.EMERALD -> lightColorScheme(
        primary = Color(0xFF047857),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD1FAE5),
        onPrimaryContainer = Color(0xFF064E3B),
        secondary = Color(0xFF0F766E),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCCFBF1),
        onSecondaryContainer = Color(0xFF134E4A),
        tertiary = Color(0xFFB45309),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFEF3C7),
        onTertiaryContainer = Color(0xFF451A03),
        background = Color(0xFFF6FBF9),
        onBackground = Color(0xFF0F2922),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0F2922),
        surfaceVariant = Color(0xFFE6F4EF),
        onSurfaceVariant = Color(0xFF3D5A50),
        outline = Color(0xFFA7F3D0)
    )
    ThemePalette.AMBER -> lightColorScheme(
        primary = Color(0xFFB45309),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFEF3C7),
        onPrimaryContainer = Color(0xFF451A03),
        secondary = Color(0xFFC2410C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFFEDD5),
        onSecondaryContainer = Color(0xFF431407),
        tertiary = Color(0xFF1D4ED8),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFDBEAFE),
        onTertiaryContainer = Color(0xFF1E3A8A),
        background = Color(0xFFFFFBEB),
        onBackground = Color(0xFF291805),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF291805),
        surfaceVariant = Color(0xFFFEF3C7),
        onSurfaceVariant = Color(0xFF6B4C23),
        outline = Color(0xFFFDE68A)
    )
    ThemePalette.ROSE -> lightColorScheme(
        primary = Color(0xFFBE123C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE4E6),
        onPrimaryContainer = Color(0xFF4C0519),
        secondary = Color(0xFFBE185D),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFFCE7F3),
        onSecondaryContainer = Color(0xFF500724),
        tertiary = Color(0xFF6D28D9),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEDE9FE),
        onTertiaryContainer = Color(0xFF2E1065),
        background = Color(0xFFFFF8F9),
        onBackground = Color(0xFF2C0B14),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF2C0B14),
        surfaceVariant = Color(0xFFFFE4E6),
        onSurfaceVariant = Color(0xFF6E3B47),
        outline = Color(0xFFFECDD3)
    )
    ThemePalette.OCEAN -> lightColorScheme(
        primary = Color(0xFF0369A1),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFE0F2FE),
        onPrimaryContainer = Color(0xFF082F49),
        secondary = Color(0xFF0E7490),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCFFAFE),
        onSecondaryContainer = Color(0xFF083344),
        tertiary = Color(0xFF4F46E5),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFE0E7FF),
        onTertiaryContainer = Color(0xFF1E1B4B),
        background = Color(0xFFF4FAFF),
        onBackground = Color(0xFF0B253A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF0B253A),
        surfaceVariant = Color(0xFFE2F1FC),
        onSurfaceVariant = Color(0xFF3B5C76),
        outline = Color(0xFFBAE6FD)
    )
}

fun buildDarkColorScheme(palette: ThemePalette): ColorScheme = when (palette) {
    ThemePalette.INDIGO -> darkColorScheme(
        primary = Color(0xFF818CF8),
        onPrimary = Color(0xFF1E1B4B),
        primaryContainer = Color(0xFF312E81),
        onPrimaryContainer = Color(0xFFE0E7FF),
        secondary = Color(0xFFA78BFA),
        onSecondary = Color(0xFF2E1065),
        secondaryContainer = Color(0xFF4C1D95),
        onSecondaryContainer = Color(0xFFEDE9FE),
        tertiary = Color(0xFFFBBF24),
        onTertiary = Color(0xFF451A03),
        tertiaryContainer = Color(0xFF78350F),
        onTertiaryContainer = Color(0xFFFEF3C7),
        background = Color(0xFF0B0F19),
        onBackground = Color(0xFFF1F5F9),
        surface = Color(0xFF131B2E),
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = Color(0xFF1E293B),
        onSurfaceVariant = Color(0xFF94A3B8),
        outline = Color(0xFF334155)
    )
    ThemePalette.EMERALD -> darkColorScheme(
        primary = Color(0xFF34D399),
        onPrimary = Color(0xFF064E3B),
        primaryContainer = Color(0xFF065F46),
        onPrimaryContainer = Color(0xFFD1FAE5),
        secondary = Color(0xFF2DD4BF),
        onSecondary = Color(0xFF134E4A),
        secondaryContainer = Color(0xFF115E59),
        onSecondaryContainer = Color(0xFFCCFBF1),
        tertiary = Color(0xFFFBBF24),
        onTertiary = Color(0xFF451A03),
        tertiaryContainer = Color(0xFF78350F),
        onTertiaryContainer = Color(0xFFFEF3C7),
        background = Color(0xFF071713),
        onBackground = Color(0xFFECFDF5),
        surface = Color(0xFF0F2620),
        onSurface = Color(0xFFECFDF5),
        surfaceVariant = Color(0xFF16382F),
        onSurfaceVariant = Color(0xFF99C2B5),
        outline = Color(0xFF235347)
    )
    ThemePalette.AMBER -> darkColorScheme(
        primary = Color(0xFFFBBF24),
        onPrimary = Color(0xFF451A03),
        primaryContainer = Color(0xFF78350F),
        onPrimaryContainer = Color(0xFFFEF3C7),
        secondary = Color(0xFFFB923C),
        onSecondary = Color(0xFF431407),
        secondaryContainer = Color(0xFF7C2D12),
        onSecondaryContainer = Color(0xFFFFEDD5),
        tertiary = Color(0xFF60A5FA),
        onTertiary = Color(0xFF1E3A8A),
        tertiaryContainer = Color(0xFF1E40AF),
        onTertiaryContainer = Color(0xFFDBEAFE),
        background = Color(0xFF171108),
        onBackground = Color(0xFFFEF3C7),
        surface = Color(0xFF241B0E),
        onSurface = Color(0xFFFEF3C7),
        surfaceVariant = Color(0xFF362815),
        onSurfaceVariant = Color(0xFFD6B88A),
        outline = Color(0xFF574122)
    )
    ThemePalette.ROSE -> darkColorScheme(
        primary = Color(0xFFFB7185),
        onPrimary = Color(0xFF4C0519),
        primaryContainer = Color(0xFF881337),
        onPrimaryContainer = Color(0xFFFFE4E6),
        secondary = Color(0xFFF472B6),
        onSecondary = Color(0xFF500724),
        secondaryContainer = Color(0xFF831843),
        onSecondaryContainer = Color(0xFFFCE7F3),
        tertiary = Color(0xFFC084FC),
        onTertiary = Color(0xFF3B0764),
        tertiaryContainer = Color(0xFF581C87),
        onTertiaryContainer = Color(0xFFF3E8FF),
        background = Color(0xFF180A10),
        onBackground = Color(0xFFFFE4E6),
        surface = Color(0xFF26111B),
        onSurface = Color(0xFFFFE4E6),
        surfaceVariant = Color(0xFF381928),
        onSurfaceVariant = Color(0xFFD8A0B0),
        outline = Color(0xFF59283E)
    )
    ThemePalette.OCEAN -> darkColorScheme(
        primary = Color(0xFF38BDF8),
        onPrimary = Color(0xFF082F49),
        primaryContainer = Color(0xFF075985),
        onPrimaryContainer = Color(0xFFE0F2FE),
        secondary = Color(0xFF22D3EE),
        onSecondary = Color(0xFF083344),
        secondaryContainer = Color(0xFF155E75),
        onSecondaryContainer = Color(0xFFCFFAFE),
        tertiary = Color(0xFF818CF8),
        onTertiary = Color(0xFF1E1B4B),
        tertiaryContainer = Color(0xFF312E81),
        onTertiaryContainer = Color(0xFFE0E7FF),
        background = Color(0xFF07131E),
        onBackground = Color(0xFFE0F2FE),
        surface = Color(0xFF0E2133),
        onSurface = Color(0xFFE0F2FE),
        surfaceVariant = Color(0xFF17324C),
        onSurfaceVariant = Color(0xFF93B8D8),
        outline = Color(0xFF244B70)
    )
}

data class NoteCardStyle(
    val labelRu: String,
    val lightBg: Color,
    val lightBorder: Color,
    val darkBg: Color,
    val darkBorder: Color,
    val badgeColor: Color
)

val NoteCardStyles = listOf(
    NoteCardStyle(
        labelRu = "Классический",
        lightBg = Color(0xFFFFFFFF),
        lightBorder = Color(0xFFE2E8F0),
        darkBg = Color(0xFF1E293B),
        darkBorder = Color(0xFF334155),
        badgeColor = Color(0xFF6366F1)
    ),
    NoteCardStyle(
        labelRu = "Лаванда",
        lightBg = Color(0xFFF5F3FF),
        lightBorder = Color(0xFFDDD6FE),
        darkBg = Color(0xFF271E47),
        darkBorder = Color(0xFF4C1D95),
        badgeColor = Color(0xFF8B5CF6)
    ),
    NoteCardStyle(
        labelRu = "Мята",
        lightBg = Color(0xFFECFDF5),
        lightBorder = Color(0xFFA7F3D0),
        darkBg = Color(0xFF112E28),
        darkBorder = Color(0xFF065F46),
        badgeColor = Color(0xFF10B981)
    ),
    NoteCardStyle(
        labelRu = "Янтарь",
        lightBg = Color(0xFFFFFBEB),
        lightBorder = Color(0xFFFDE68A),
        darkBg = Color(0xFF33240F),
        darkBorder = Color(0xFF78350F),
        badgeColor = Color(0xFFF59E0B)
    ),
    NoteCardStyle(
        labelRu = "Коралл",
        lightBg = Color(0xFFFFF1F2),
        lightBorder = Color(0xFFFECDD3),
        darkBg = Color(0xFF361622),
        darkBorder = Color(0xFF881337),
        badgeColor = Color(0xFFF43F5E)
    ),
    NoteCardStyle(
        labelRu = "Небо",
        lightBg = Color(0xFFF0F9FF),
        lightBorder = Color(0xFFBAE6FD),
        darkBg = Color(0xFF11283E),
        darkBorder = Color(0xFF075985),
        badgeColor = Color(0xFF0EA5E9)
    )
)
