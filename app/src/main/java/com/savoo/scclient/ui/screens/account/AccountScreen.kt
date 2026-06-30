package com.savoo.scclient.ui.screens.account

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.platform.LocalContext
import com.savoo.scclient.R
import com.savoo.scclient.auth.AuthRepository
import com.savoo.scclient.auth.TokenStore
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.BadgeRepository
import com.savoo.scclient.data.remote.ClientIdProvider
import com.savoo.scclient.data.repository.SettingsRepository
import com.savoo.scclient.data.repository.TrackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val clientIdProvider: ClientIdProvider,
    val badgeRepository: BadgeRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccountUiState())
    val uiState = _uiState.asStateFlow()
    val developerMode = settingsRepository.settings.map { it.developerMode }

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

    fun onWebToken(value: String) {
        viewModelScope.launch { tokenStore.saveWebToken(value) }
    }

    fun onCookies(cookies: String) {
        viewModelScope.launch { tokenStore.saveCookies(cookies) }
    }

    fun logout() = authRepository.logout()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    viewModel: AccountViewModel = hiltViewModel(),
    onOpenSettings: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    var showLogin by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.account_title)) }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                showLogin -> OAuthWebViewScreen(
                    onTokenReceived = { token ->
                        viewModel.onWebToken(token)
                        showLogin = false
                    },
                    onCookiesReceived = { cookies ->
                        viewModel.onCookies(cookies)
                        showLogin = false
                    },
                    onCancel = { showLogin = false }
                )
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.isLoggedIn -> {
                    val userBadges by state.user?.id?.let { viewModel.badgeRepository.getBadges(it) }
                        ?.collectAsState() ?: remember { mutableStateOf(emptyList<String>()) }
                    val isDeveloper by viewModel.developerMode.collectAsState(initial = false)
                    LoggedInContent(
                        user = state.user,
                        badges = userBadges,
                        showId = isDeveloper,
                        onLogout = { viewModel.logout() },
                        onSettings = onOpenSettings,
                    )
                }
                else -> LoggedOutContent(onSignIn = { showLogin = true })
            }
        }
    }
}

@Composable
private fun LoggedInContent(
    user: User?,
    badges: List<String> = emptyList(),
    showId: Boolean = false,
    onLogout: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    var selectedBadge by remember { mutableStateOf<String?>(null) }
    var logoutPressed by remember { mutableStateOf(false) }
    val logoutScale by animateFloatAsState(
        targetValue = if (logoutPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "logout",
        finishedListener = { logoutPressed = false }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        AsyncImage(
            model = user?.avatarUrl?.replace("-large", "-t500x500"),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
        )

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = user?.fullName?.ifBlank { null } ?: user?.username ?: "...",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            badges.forEach { badge ->
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = when (badge) {
                        "developer" -> Icons.Filled.Handyman
                        "supporter" -> Icons.Filled.Star
                        else -> Icons.Filled.Verified
                    },
                    contentDescription = badge,
                    tint = when (badge) {
                        "developer" -> MaterialTheme.colorScheme.tertiary
                        "supporter" -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier
                        .size(20.dp)
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

        user?.followersCount?.let {
            Text(
                text = "$it followers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        user?.permalinkUrl?.let { url ->
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.account_share_link),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Surface(
            onClick = { logoutPressed = true; onLogout() },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = logoutScale; scaleY = logoutScale },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_sign_out), style = MaterialTheme.typography.labelLarge)
            }
        }

        Spacer(Modifier.height(12.dp))

        Surface(
            onClick = onSettings,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_settings), style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    selectedBadge?.let { badge ->
        com.savoo.scclient.ui.components.BadgeBottomSheet(
            badge = badge,
            profileName = user?.fullName?.ifBlank { null } ?: user?.username ?: "",
            onDismiss = { selectedBadge = null },
        )
    }
}

@Composable
private fun LoggedOutContent(onSignIn: () -> Unit) {
    var buttonPressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (buttonPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh),
        label = "signIn",
        finishedListener = { buttonPressed = false }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Person,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )

        Spacer(Modifier.height(24.dp))

        Text(
            stringResource(R.string.account_sign_in_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            stringResource(R.string.account_sign_in_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            onClick = { buttonPressed = true; onSignIn() },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { scaleX = buttonScale; scaleY = buttonScale },
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.account_sign_in_soundcloud), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
