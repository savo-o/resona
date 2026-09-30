package com.savoo.scclient.ui.screens.account

import com.savoo.scclient.ui.components.badgeIcon
import com.savoo.scclient.ui.components.badgeTint
import com.savoo.scclient.ui.components.badgeTitle
import com.savoo.scclient.ui.components.followersCountText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import com.savoo.scclient.ui.haptics.rememberHapticTick
import java.text.NumberFormat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.savoo.scclient.ui.components.AppDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.savoo.scclient.R
import com.savoo.scclient.auth.AuthRepository
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.BadgeRepository
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.data.repository.FavoritesRepository
import com.savoo.scclient.data.repository.FavoritesSyncManager
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import com.savoo.scclient.debug.DebugLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.ui.text.style.TextOverflow

data class AccountUiState(
    val isLoggedIn: Boolean = false,
    val user: User? = null,
    val isLoading: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
    private val trackRepository: TrackRepository,
    private val favoritesRepository: FavoritesRepository,
    val clientIdProvider: ClientIdProvider,
    val badgeRepository: BadgeRepository,
    private val settingsRepository: SettingsRepository,
    private val favoritesSyncManager: FavoritesSyncManager,
) : ViewModel() {

    val localOnlyFavorites = favoritesSyncManager.localOnlyCount
    val favoritesPush = favoritesSyncManager.state

    fun pushLocalFavorites() {
        favoritesSyncManager.start()
    }

    val spamWarning = favoritesSyncManager.spamWarning

    private val _isAckingSpamWarning = MutableStateFlow(false)
    val isAckingSpamWarning = _isAckingSpamWarning.asStateFlow()

    fun acknowledgeSpamWarning(onFailed: () -> Unit) {
        if (_isAckingSpamWarning.value) return
        _isAckingSpamWarning.value = true
        viewModelScope.launch {
            val ok = favoritesSyncManager.acknowledgeSpamWarning()
            _isAckingSpamWarning.value = false
            if (!ok) onFailed()
        }
    }

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState = _uiState.asStateFlow()
    val developerMode = settingsRepository.settings.map { it.developerMode }
    val onlineFavoritesEnabled = settingsRepository.settings.map { it.onlineFavoritesEnabled }

    private val _isSyncingFavorites = MutableStateFlow(false)
    val isSyncingFavorites = _isSyncingFavorites.asStateFlow()

    fun setOnlineFavoritesEnabled(value: Boolean) {
        viewModelScope.launch { settingsRepository.setOnlineFavoritesEnabled(value) }
    }

    fun syncOnlineFavoritesNow(onDone: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            _isSyncingFavorites.value = true
            DebugLog.log(TAG, "manual online favorites sync triggered from Account")
            val result = runCatching { trackRepository.getLikedTracks() }
                .onSuccess { favoritesRepository.syncOnlineLikes(it) }
            _isSyncingFavorites.value = false
            onDone(result.map { it.size })
        }
    }

    init {
        viewModelScope.launch {
            tokenStore.isLoggedIn.collect { loggedIn ->
                _uiState.value = _uiState.value.copy(isLoggedIn = loggedIn)
                if (loggedIn) loadProfile()
            }
        }
    }

    private fun loadProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            runCatching { trackRepository.getMe() }
                .onSuccess { user -> _uiState.value = _uiState.value.copy(user = user, isLoading = false) }
                .onFailure { _uiState.value = _uiState.value.copy(isLoading = false) }
        }
    }

    fun onProfileSaved(user: User?) {
        if (user != null) _uiState.value = _uiState.value.copy(user = user) else loadProfile()
    }

    fun onWebToken(value: String) {
        viewModelScope.launch { tokenStore.saveWebToken(value) }
    }

    fun onCookies(cookies: String) {
        viewModelScope.launch { tokenStore.saveCookies(cookies) }
    }

    fun logout() = authRepository.logout()

    companion object {
        private const val TAG = "AccountViewModel"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editingProfile by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.account_title), maxLines = 1, overflow = TextOverflow.Ellipsis) }) },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                state.isLoggedIn -> {
                    val userBadges by state.user?.id?.let { viewModel.badgeRepository.getBadges(it) }
                        ?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
                    val isDeveloper by viewModel.developerMode.collectAsState(initial = false)
                    val onlineFavoritesEnabled by viewModel.onlineFavoritesEnabled.collectAsState(initial = false)
                    val isSyncingFavorites by viewModel.isSyncingFavorites.collectAsState()
                    val localOnlyFavorites by viewModel.localOnlyFavorites.collectAsState(initial = 0)
                    val favoritesPush by viewModel.favoritesPush.collectAsState()
                    val spamWarning by viewModel.spamWarning.collectAsState()
                    val isAckingSpamWarning by viewModel.isAckingSpamWarning.collectAsState()
                    LoggedInContent(
                        user = state.user,
                        badges = userBadges,
                        showId = isDeveloper,
                        onlineFavoritesEnabled = onlineFavoritesEnabled,
                        onOnlineFavoritesChange = { viewModel.setOnlineFavoritesEnabled(it) },
                        hasSpamWarning = spamWarning != null,
                        isAckingSpamWarning = isAckingSpamWarning,
                        onAckSpamWarning = {
                            viewModel.acknowledgeSpamWarning {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.spam_warning_ack_failed)) }
                            }
                        },
                        localOnlyFavorites = localOnlyFavorites,
                        isPushingFavorites = favoritesPush != null,
                        onPushFavorites = { viewModel.pushLocalFavorites() },
                        isSyncingFavorites = isSyncingFavorites,
                        onSyncFavoritesNow = {
                            viewModel.syncOnlineFavoritesNow { result ->
                                scope.launch {
                                    val message = result.fold(
                                        onSuccess = { context.getString(R.string.favorites_online_sync_now_done, it) },
                                        onFailure = { context.getString(R.string.favorites_online_sync_now_failed) },
                                    )
                                    snackbarHostState.showSnackbar(message)
                                }
                            }
                        },
                        onLogout = { viewModel.logout() },
                        onSettings = onOpenSettings,
                        onEditProfile = { editingProfile = true },
                    )
                    val editUser = state.user
                    if (editingProfile && editUser != null) {
                        ProfileEditSheet(
                            user = editUser,
                            onDismiss = { editingProfile = false },
                            onSaved = { updated ->
                                editingProfile = false
                                viewModel.onProfileSaved(updated)
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.profile_edit_saved)) }
                            },
                        )
                    }
                }
                else -> LoginScreen(
                    onTokenReceived = { token -> viewModel.onWebToken(token) },
                    onCookiesReceived = { cookies -> viewModel.onCookies(cookies) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoggedInContent(
    user: User?,
    badges: List<String> = emptyList(),
    showId: Boolean = false,
    onlineFavoritesEnabled: Boolean = false,
    onOnlineFavoritesChange: (Boolean) -> Unit = {},
    isSyncingFavorites: Boolean = false,
    onSyncFavoritesNow: () -> Unit = {},
    localOnlyFavorites: Int = 0,
    isPushingFavorites: Boolean = false,
    onPushFavorites: () -> Unit = {},
    hasSpamWarning: Boolean = false,
    isAckingSpamWarning: Boolean = false,
    onAckSpamWarning: () -> Unit = {},
    onLogout: () -> Unit,
    onSettings: () -> Unit,
    onEditProfile: () -> Unit = {},
) {
    val context = LocalContext.current
    val haptic = rememberHapticTick()
    var selectedBadge by remember { mutableStateOf<String?>(null) }
    var confirmPush by remember { mutableStateOf(false) }

    if (confirmPush) {
        AlertDialog(
            onDismissRequest = { confirmPush = false },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.favorites_push_danger_title)) },
            text = { Text(stringResource(R.string.favorites_push_danger_body, localOnlyFavorites)) },
            confirmButton = {
                TextButton(onClick = { haptic(); confirmPush = false; onPushFavorites() }) {
                    Text(stringResource(R.string.favorites_push_danger_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPush = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    var logoutPressed by remember { mutableStateOf(false) }
    val logoutScale by animateFloatAsState(
        targetValue = if (logoutPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "logout",
        finishedListener = { logoutPressed = false }
    )

    val displayName = user?.fullName?.ifBlank { null } ?: user?.username ?: "..."
    val share: (() -> Unit)? = user?.permalinkUrl?.let { url ->
        {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
            context.startActivity(Intent.createChooser(intent, null))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(MaterialShapes.Cookie12Sided.toShape())
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val avatar = user?.avatarUrl?.replace("-large", "-t500x500")
            if (avatar != null) {
                AsyncImage(
                    model = avatar,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 1.2f; scaleY = 1.2f },
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(56.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            badges.forEach { badge ->
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = badgeIcon(badge),
                    contentDescription = badgeTitle(badge),
                    tint = badgeTint(badge),
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable { selectedBadge = badge },
                )
            }
        }

        user?.username?.let {
            Text(
                text = "@$it",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showId) {
            user?.id?.let {
                Text(
                    text = "ID: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        user?.description?.trim()?.takeIf { it.isNotBlank() }?.let { bio ->
            Text(
                text = bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, start = 12.dp, end = 12.dp),
            )
        }

        if (user != null) {
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                AccountStatTile(
                    value = user.followersCount ?: 0L,
                    label = stringResource(R.string.account_stat_followers),
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp, topEnd = 10.dp, bottomEnd = 10.dp),
                    modifier = Modifier.weight(1f),
                )
                AccountStatTile(
                    value = (user.trackCount ?: 0).toLong(),
                    label = stringResource(R.string.account_stat_tracks),
                    shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp, topEnd = 28.dp, bottomEnd = 28.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { haptic(); onEditProfile() },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.account_edit_profile), style = MaterialTheme.typography.titleSmall)
                }
                if (share != null) {
                    Spacer(Modifier.width(10.dp))
                    FilledTonalIconButton(
                        onClick = { haptic(); share() },
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(52.dp),
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.account_share_link))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        AnimatedVisibility(
            visible = hasSpamWarning,
            enter = fadeIn() + expandVertically(spring(dampingRatio = Spring.DampingRatioMediumBouncy)),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        stringResource(R.string.spam_warning_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.spam_warning_body), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { haptic(); onAckSpamWarning() },
                        enabled = !isAckingSpamWarning,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        if (isAckingSpamWarning) {
                            LoadingIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onError)
                        } else {
                            Text(stringResource(R.string.spam_warning_ack), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.animateContentSize(spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow))) {
                com.savoo.scclient.ui.components.SwitchItem(
                    title = stringResource(R.string.favorites_online_toggle_title),
                    subtitle = stringResource(R.string.favorites_online_toggle_desc),
                    checked = onlineFavoritesEnabled,
                    onCheckedChange = onOnlineFavoritesChange,
                )
                if (onlineFavoritesEnabled) {
                    AppDivider()
                    AccountActionRow(
                        icon = Icons.Filled.Sync,
                        title = stringResource(R.string.favorites_online_sync_now),
                        description = stringResource(R.string.favorites_online_sync_now_desc),
                        loading = isSyncingFavorites,
                        onClick = { haptic(); onSyncFavoritesNow() },
                    )
                }
                if ((onlineFavoritesEnabled && localOnlyFavorites > 0) || isPushingFavorites) {
                    AppDivider()
                    AccountActionRow(
                        icon = Icons.Filled.Warning,
                        title = stringResource(R.string.favorites_push_title),
                        description = stringResource(R.string.favorites_push_desc, localOnlyFavorites),
                        loading = isPushingFavorites,
                        danger = true,
                        onClick = { haptic(); confirmPush = true },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            AccountActionRow(
                icon = Icons.Filled.Settings,
                title = stringResource(R.string.account_settings),
                description = null,
                loading = false,
                onClick = { haptic(); onSettings() },
            )
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            onClick = { haptic(); logoutPressed = true; onLogout() },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = logoutScale; scaleY = logoutScale },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_sign_out), style = MaterialTheme.typography.titleSmall)
            }
        }
    }

    selectedBadge?.let { badge ->
        com.savoo.scclient.ui.components.BadgeBottomSheet(
            badge = badge,
            profileName = user?.fullName?.ifBlank { null } ?: user?.username ?: "",
            onDismiss = { selectedBadge = null },
            onOpenUrl = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        )
    }
}

@Composable
private fun AccountStatTile(value: Long, label: String, shape: Shape, modifier: Modifier = Modifier) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
        ) {
            Text(
                NumberFormat.getIntegerInstance().format(value),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountActionRow(
    icon: ImageVector,
    title: String,
    description: String?,
    loading: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (danger) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (danger) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (loading) {
                    LoadingIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (danger) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        stringResource(R.string.favorites_push_danger_badge),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
