package com.savoo.scclient.ui.screens.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.savoo.scclient.R
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.repository.ProfileFields
import com.savoo.scclient.data.repository.ProfileRepository
import com.savoo.scclient.data.repository.ProfileUpdateException
import com.savoo.scclient.data.repository.toProfileFields
import com.savoo.scclient.ui.components.MorphingArtworkShape
import com.savoo.scclient.ui.haptics.rememberHapticTick
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AvatarChange { NONE, REPLACE, REMOVE }

data class ProfileEditState(
    val original: ProfileFields = ProfileFields("", "", "", "", ""),
    val fields: ProfileFields = original,
    val avatarUrl: String? = null,
    val pickedAvatar: Uri? = null,
    val avatarChange: AvatarChange = AvatarChange.NONE,
    val saving: Boolean = false,
    val error: String? = null,
) {
    val hasChanges: Boolean get() = fields != original || avatarChange != AvatarChange.NONE
    val nameValid: Boolean get() = fields.username.isNotBlank()
}

@HiltViewModel
class ProfileEditViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ProfileEditState())
    val state = _state.asStateFlow()
    private var session: Long? = null

    fun init(user: User, openedAt: Long) {
        if (session == openedAt) return
        session = openedAt
        val fields = user.toProfileFields()
        _state.value = ProfileEditState(original = fields, fields = fields, avatarUrl = user.avatarUrl)
    }

    fun edit(transform: ProfileFields.() -> ProfileFields) = _state.update { it.copy(fields = it.fields.transform(), error = null) }

    fun pickAvatar(uri: Uri) = _state.update { it.copy(pickedAvatar = uri, avatarChange = AvatarChange.REPLACE, error = null) }

    fun removeAvatar() = _state.update { it.copy(pickedAvatar = null, avatarChange = AvatarChange.REMOVE, error = null) }

    fun save(onSaved: (User?) -> Unit) {
        val current = _state.value
        if (current.saving || !current.nameValid) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = runCatching {
                when (current.avatarChange) {
                    AvatarChange.REPLACE -> current.pickedAvatar?.let { profileRepository.uploadAvatar(it) }
                    AvatarChange.REMOVE -> profileRepository.deleteAvatar()
                    AvatarChange.NONE -> Unit
                }
                profileRepository.updateProfile(current.original, current.fields.trimmed())
                runCatching { profileRepository.reloadMe() }.getOrNull()
            }
            result
                .onSuccess { user ->
                    _state.update { it.copy(saving = false) }
                    onSaved(user)
                }
                .onFailure { error ->
                    val message = (error as? ProfileUpdateException)?.let { it.serverMessage ?: "HTTP ${it.code}" }
                        ?: error.message
                    _state.update { it.copy(saving = false, error = message ?: "") }
                }
        }
    }

    private fun ProfileFields.trimmed() = copy(
        username = username.trim(),
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        city = city.trim(),
        description = description.trim(),
    )
}

private fun isDefaultAvatar(url: String?): Boolean = url.isNullOrBlank() || "default_avatar" in url

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ProfileEditSheet(
    user: User,
    onDismiss: () -> Unit,
    onSaved: (User?) -> Unit,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val openedAt = rememberSaveable { System.nanoTime() }
    LaunchedEffect(openedAt) { viewModel.init(user, openedAt) }
    val state by viewModel.state.collectAsState()
    val haptic = rememberHapticTick()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            haptic()
            viewModel.pickAvatar(uri)
        }
    }
    val openPicker = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val saving by rememberUpdatedState(state.saving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !saving },
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.profile_edit_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(20.dp))

            val shownAvatar: Any? = when (state.avatarChange) {
                AvatarChange.REPLACE -> state.pickedAvatar
                AvatarChange.REMOVE -> null
                AvatarChange.NONE -> state.avatarUrl?.takeUnless { isDefaultAvatar(it) }?.replace("-large", "-t500x500")
            }
            ProfileAvatar(
                model = shownAvatar,
                changed = state.avatarChange != AvatarChange.NONE,
                saving = state.saving,
                onClick = { if (!state.saving) openPicker() },
            )

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = openPicker, enabled = !state.saving) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_edit_change_photo))
                }
                val canRemove = state.avatarChange == AvatarChange.REPLACE ||
                    (state.avatarChange == AvatarChange.NONE && !isDefaultAvatar(state.avatarUrl))
                AnimatedVisibility(visible = canRemove, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(
                        onClick = { haptic(); viewModel.removeAvatar() },
                        enabled = !state.saving,
                    ) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.profile_edit_remove_photo))
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            val fieldShape = RoundedCornerShape(20.dp)
            OutlinedTextField(
                value = state.fields.username,
                onValueChange = { value -> viewModel.edit { copy(username = value) } },
                label = { Text(stringResource(R.string.profile_edit_display_name)) },
                singleLine = true,
                isError = !state.nameValid,
                supportingText = if (!state.nameValid) {
                    { Text(stringResource(R.string.profile_edit_name_required)) }
                } else {
                    null
                },
                enabled = !state.saving,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = state.fields.firstName,
                    onValueChange = { value -> viewModel.edit { copy(firstName = value) } },
                    label = { Text(stringResource(R.string.profile_edit_first_name)) },
                    singleLine = true,
                    enabled = !state.saving,
                    shape = fieldShape,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.fields.lastName,
                    onValueChange = { value -> viewModel.edit { copy(lastName = value) } },
                    label = { Text(stringResource(R.string.profile_edit_last_name)) },
                    singleLine = true,
                    enabled = !state.saving,
                    shape = fieldShape,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.fields.city,
                onValueChange = { value -> viewModel.edit { copy(city = value) } },
                label = { Text(stringResource(R.string.profile_edit_city)) },
                singleLine = true,
                enabled = !state.saving,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.fields.description,
                onValueChange = { value -> viewModel.edit { copy(description = value) } },
                label = { Text(stringResource(R.string.profile_edit_bio)) },
                minLines = 3,
                maxLines = 6,
                enabled = !state.saving,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.profile_edit_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedVisibility(
                visible = state.error != null,
                enter = fadeIn() + expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.profile_edit_error, state.error.orEmpty()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    haptic()
                    viewModel.save(onSaved)
                },
                enabled = state.hasChanges && state.nameValid && !state.saving,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                if (state.saving) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(stringResource(R.string.profile_edit_save), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProfileAvatar(model: Any?, changed: Boolean, saving: Boolean, onClick: () -> Unit) {
    val morph = remember { Morph(MaterialShapes.Circle.normalized(), MaterialShapes.Cookie12Sided.normalized()) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(changed) {
        progress.animateTo(
            if (changed) 1f else 0f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        )
    }
    val transition = rememberInfiniteTransition(label = "avatarSaving")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(6_000, easing = LinearEasing)),
        label = "spin",
    )
    val rotation = if (saving) spin else 0f

    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .graphicsLayer { rotationZ = rotation }
                .clip(MorphingArtworkShape(morph, progress.value.coerceIn(0f, 1f)))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val counterRotate = Modifier.graphicsLayer { rotationZ = -rotation }
            if (model != null) {
                AsyncImage(
                    model = model,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = counterRotate.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f },
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = counterRotate.size(56.dp),
                )
            }
            if (saving) {
                Box(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator(modifier = counterRotate.size(48.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
        FilledTonalIconButton(
            onClick = onClick,
            enabled = !saving,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(40.dp),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.profile_edit_change_photo), modifier = Modifier.size(18.dp))
        }
    }
}
