package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AvatarPhotoHelper
import com.example.data.MemberProfile
import com.example.data.PresetAvatars
import com.example.data.findPresetAvatar
import com.example.ui.theme.NoteCardStyles
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.launch

@Composable
fun MemberAvatarBadge(
    avatarType: String,
    avatarData: String,
    colorIndex: Int,
    size: Dp = 40.dp,
    fontSize: TextUnit = 18.sp,
    modifier: Modifier = Modifier
) {
    val safeIndex = colorIndex.coerceIn(0, NoteCardStyles.lastIndex)
    val accentColor = NoteCardStyles[safeIndex].badgeColor
    val decodedBitmap = remember(avatarType, avatarData) {
        if (avatarType == "PHOTO") {
            AvatarPhotoHelper.decodeBase64ToImageBitmap(avatarData)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = 0.2f))
            .border(2.dp, accentColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (decodedBitmap != null) {
            Image(
                bitmap = decodedBitmap,
                contentDescription = "Аватар участника",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            val preset = findPresetAvatar(avatarData)
            Text(
                text = preset.emoji,
                fontSize = fontSize,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun AuthorAttributionBadge(
    authorName: String,
    authorAvatarType: String,
    authorAvatarData: String,
    authorColorIndex: Int,
    modifier: Modifier = Modifier
) {
    val safeIndex = authorColorIndex.coerceIn(0, NoteCardStyles.lastIndex)
    val badgeColor = NoteCardStyles[safeIndex].badgeColor

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = badgeColor.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MemberAvatarBadge(
                avatarType = authorAvatarType,
                avatarData = authorAvatarData,
                colorIndex = safeIndex,
                size = 22.dp,
                fontSize = 11.sp
            )
            Text(
                text = authorName.ifBlank { "Участник" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MembersHeaderStrip(
    currentProfile: MemberProfile,
    members: List<MemberProfile>,
    onEditOwnProfileClick: () -> Unit,
    onMemberClick: (MemberProfile) -> Unit
) {
    val combinedMembers = remember(currentProfile, members) {
        val others = members.filter { it.memberId != currentProfile.memberId && it.nickname.isNotBlank() }
        listOf(currentProfile) + others
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Group,
                        contentDescription = "Участники пространства",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Общее пространство • Участники (${combinedMembers.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onEditOwnProfileClick)
                        .testTag("edit_own_profile_chip")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Изменить профиль",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Мой профиль",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                modifier = Modifier.testTag("members_header_strip")
            ) {
                items(combinedMembers, key = { it.memberId.ifBlank { it.nickname } }) { member ->
                    val isCurrent = member.memberId == currentProfile.memberId
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isCurrent) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                }
                            )
                            .clickable {
                                if (isCurrent) onEditOwnProfileClick() else onMemberClick(member)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MemberAvatarBadge(
                            avatarType = member.avatarType,
                            avatarData = member.avatarData,
                            colorIndex = member.colorIndex.toInt(),
                            size = 34.dp,
                            fontSize = 16.sp
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = member.nickname.ifBlank { "Участник" },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isCurrent) {
                                    Text(
                                        text = "(Вы)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            if (member.statusText.isNotBlank()) {
                                Text(
                                    text = member.statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileOnboardingScreen(
    currentThemeMode: ThemeMode,
    currentPalette: ThemePalette,
    onThemeModeChange: (ThemeMode) -> Unit,
    onPaletteChange: (ThemePalette) -> Unit,
    onCompleteProfile: (
        nickname: String,
        statusText: String,
        avatarType: String,
        avatarData: String,
        colorIndex: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nickname by remember { mutableStateOf("") }
    var statusText by remember { mutableStateOf("В общем пространстве ✨") }
    var avatarType by remember { mutableStateOf("PRESET") }
    var avatarData by remember { mutableStateOf("rocket") }
    var colorIndex by remember { mutableIntStateOf(0) }
    var photoError by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = AvatarPhotoHelper.encodeUriToBase64Thumbnail(context, uri)
                if (base64 != null) {
                    avatarType = "PHOTO"
                    avatarData = base64
                    photoError = null
                } else {
                    photoError = "Не удалось обработать выбранное изображение"
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Hero Welcome Header
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.img_hero_planner),
                            contentDescription = "Общее пространство ChronoNote",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp)
                        ) {
                            Text(
                                text = "Добро пожаловать в ChronoNote",
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Введите любой ник и выберите аватарку — общее пространство заметок и календаря уже настроено для вас и ваших участников!",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.92f)
                            )
                        }
                    }
                }

                // Live Preview & Editor Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MemberAvatarBadge(
                            avatarType = avatarType,
                            avatarData = avatarData,
                            colorIndex = colorIndex,
                            size = 84.dp,
                            fontSize = 38.sp
                        )

                        Text(
                            text = nickname.ifBlank { "Ваш никнейм" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.testTag("pick_gallery_avatar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddAPhoto,
                                contentDescription = "Выбрать фото из галереи"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Загрузить своё фото из галереи")
                        }

                        if (photoError != null) {
                            Text(
                                text = photoError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        OutlinedTextField(
                            value = nickname,
                            onValueChange = { nickname = it.take(40) },
                            label = { Text("Ваш никнейм") },
                            placeholder = { Text("Например: Артём") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_nickname_input")
                        )

                        OutlinedTextField(
                            value = statusText,
                            onValueChange = { statusText = it.take(80) },
                            label = { Text("Статус или подпись (необязательно)") },
                            placeholder = { Text("Например: На фокусе 🚀") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_status_input")
                        )

                        Text(
                            text = "Или выберите стильный аватар из коллекции:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PresetAvatars.forEach { preset ->
                                val isSelected = avatarType == "PRESET" && avatarData == preset.key
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            }
                                        )
                                        .border(
                                            width = if (isSelected) 2.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            avatarType = "PRESET"
                                            avatarData = preset.key
                                        }
                                        .testTag("preset_avatar_${preset.key}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = preset.emoji, fontSize = 22.sp)
                                }
                            }
                        }

                        Text(
                            text = "Цвет рамки и бейджа автора:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            NoteCardStyles.forEachIndexed { idx, style ->
                                val selected = colorIndex == idx
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(style.badgeColor)
                                        .border(
                                            width = if (selected) 3.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { colorIndex = idx }
                                        .testTag("profile_color_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (selected) {
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

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                onCompleteProfile(
                                    nickname.trim().ifEmpty { "Участник" },
                                    statusText.trim(),
                                    avatarType,
                                    avatarData,
                                    colorIndex
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .testTag("enter_workspace_button"),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "Войти в общее пространство",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Quick Theme & Palette Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = "Темы оформления",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Тема оформления (Светлая / Тёмная и 5 палитр)",
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = currentThemeMode == mode,
                                    onClick = { onThemeModeChange(mode) },
                                    label = { Text(mode.titleRu) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (mode) {
                                                ThemeMode.LIGHT -> Icons.Default.LightMode
                                                ThemeMode.DARK -> Icons.Default.DarkMode
                                                ThemeMode.SYSTEM -> Icons.Default.Palette
                                            },
                                            contentDescription = mode.titleRu,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ThemePalette.entries.forEach { palette ->
                                val selected = palette == currentPalette
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(palette.previewPrimary, palette.previewSecondary)
                                            )
                                        )
                                        .border(
                                            width = if (selected) 2.5.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { onPaletteChange(palette) }
                                )
                            }
                            Text(
                                text = currentPalette.titleRu,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditorDialog(
    initialProfile: MemberProfile,
    onDismiss: () -> Unit,
    onSave: (
        nickname: String,
        statusText: String,
        avatarType: String,
        avatarData: String,
        colorIndex: Int
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var nickname by remember(initialProfile) { mutableStateOf(initialProfile.nickname) }
    var statusText by remember(initialProfile) { mutableStateOf(initialProfile.statusText) }
    var avatarType by remember(initialProfile) { mutableStateOf(initialProfile.avatarType) }
    var avatarData by remember(initialProfile) { mutableStateOf(initialProfile.avatarData) }
    var colorIndex by remember(initialProfile) {
        mutableIntStateOf(initialProfile.colorIndex.toInt().coerceIn(0, 5))
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val base64 = AvatarPhotoHelper.encodeUriToBase64Thumbnail(context, uri)
                if (base64 != null) {
                    avatarType = "PHOTO"
                    avatarData = base64
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Редактировать мой профиль",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemberAvatarBadge(
                    avatarType = avatarType,
                    avatarData = avatarData,
                    colorIndex = colorIndex,
                    size = 76.dp,
                    fontSize = 34.sp
                )

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.testTag("dialog_pick_gallery_avatar_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddAPhoto,
                        contentDescription = "Выбрать фото из галереи"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Фото из галереи")
                }

                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it.take(40) },
                    label = { Text("Ваш никнейм") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_nickname_input")
                )

                OutlinedTextField(
                    value = statusText,
                    onValueChange = { statusText = it.take(80) },
                    label = { Text("Статус или подпись") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_status_input")
                )

                Text(
                    text = "Коллекция стильных аватаров:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetAvatars.forEach { preset ->
                        val isSelected = avatarType == "PRESET" && avatarData == preset.key
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    avatarType = "PRESET"
                                    avatarData = preset.key
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = preset.emoji, fontSize = 20.sp)
                        }
                    }
                }

                Text(
                    text = "Цвет рамки и бейджа:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Start)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NoteCardStyles.forEachIndexed { idx, style ->
                        val selected = colorIndex == idx
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(style.badgeColor)
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorIndex = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = style.labelRu,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
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
                    onSave(
                        nickname.trim().ifEmpty { "Участник" },
                        statusText.trim(),
                        avatarType,
                        avatarData,
                        colorIndex
                    )
                },
                modifier = Modifier.testTag("save_profile_button")
            ) {
                Text("Сохранить профиль")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun MemberDetailDialog(
    member: MemberProfile,
    authoredNotesCount: Int,
    authoredScheduleCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MemberAvatarBadge(
                    avatarType = member.avatarType,
                    avatarData = member.avatarData,
                    colorIndex = member.colorIndex.toInt(),
                    size = 54.dp,
                    fontSize = 24.sp
                )
                Column {
                    Text(
                        text = member.nickname.ifBlank { "Участник" },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (member.statusText.isNotBlank()) {
                        Text(
                            text = member.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Участник общего пространства с полными правами редактирования.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "• Создано или обновлено заметок: $authoredNotesCount\n• Задач в расписании календаря: $authoredScheduleCount",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}
