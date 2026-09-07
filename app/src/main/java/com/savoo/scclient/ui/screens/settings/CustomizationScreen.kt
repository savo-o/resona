package com.savoo.scclient.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.savoo.scclient.R
import com.savoo.scclient.data.repository.AppBackgroundMode
import com.savoo.scclient.data.repository.AppIconOption
import com.savoo.scclient.data.repository.DarkModeOption
import com.savoo.scclient.data.repository.HomeSection
import com.savoo.scclient.data.repository.HomeSectionConfig
import com.savoo.scclient.data.repository.PlayerBackgroundStyle
import com.savoo.scclient.data.repository.PlayerStyle
import com.savoo.scclient.data.repository.SeekBarStyle
import com.savoo.scclient.ui.components.SwitchItem
import com.savoo.scclient.ui.haptics.rememberHapticTick
import com.savoo.scclient.ui.theme.AppColorTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColorThemeSwatch(
    theme: AppColorTheme,
    selected: Boolean,
    onClick: () -> Unit,
    swatchColor: Color = theme.seedPrimary,
) {
    val shape = if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(shape)
                .background(swatchColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            theme.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun CustomColorPicker(color: Color, onColorChange: (Color) -> Unit) {
    val hsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember(color) { mutableFloatStateOf(hsv[0]) }
    var saturation by remember(color) { mutableFloatStateOf(hsv[1]) }
    var value by remember(color) { mutableFloatStateOf(hsv[2]) }
    val liveColor = Color.hsv(hue, saturation, value)

    fun commit() = onColorChange(Color.hsv(hue, saturation, value))

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(liveColor),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.settings_custom_color),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.settings_custom_color_hue),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = hue,
            onValueChange = { hue = it },
            onValueChangeFinished = { commit() },
            valueRange = 0f..360f,
        )
        Text(
            stringResource(R.string.settings_custom_color_saturation),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = saturation,
            onValueChange = { saturation = it },
            onValueChangeFinished = { commit() },
            valueRange = 0f..1f,
        )
        Text(
            stringResource(R.string.settings_custom_color_brightness),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { commit() },
            valueRange = 0.1f..1f,
        )
    }
}

private fun labelResFor(section: HomeSection): Int = when (section) {
    HomeSection.JUMP_BACK_IN -> R.string.home_section_jump_back_in
    HomeSection.FAVORITES -> R.string.home_section_favorites
    HomeSection.OFFLINE -> R.string.home_section_offline
    HomeSection.ARTISTS -> R.string.home_section_artists
    HomeSection.PLAYLISTS -> R.string.home_section_playlists
}

@Composable
private fun HomeSectionRow(
    config: HomeSectionConfig,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleVisible: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = config.visible, onCheckedChange = onToggleVisible)
        Text(
            stringResource(labelResFor(config.section)),
            style = MaterialTheme.typography.bodyLarge,
            color = if (config.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null)
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun CustomizationScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onBack: () -> Unit = {},
) {
    val settings by viewModel.settings.collectAsState()
    val haptic = rememberHapticTick()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_customization)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(0.dp))

            SettingsSectionCard(title = stringResource(R.string.settings_color_theme)) {
                FlowRow(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppColorTheme.entries.filter { it != AppColorTheme.DYNAMIC_TRACK }.forEach { theme ->
                        ColorThemeSwatch(
                            theme = theme,
                            selected = settings.colorTheme == theme,
                            onClick = { viewModel.setColorTheme(theme) },
                            swatchColor = if (theme == AppColorTheme.CUSTOM) settings.customSeedColor else theme.seedPrimary,
                        )
                    }
                }
                if (settings.colorTheme == AppColorTheme.CUSTOM) {
                    SettingsDivider()
                    CustomColorPicker(
                        color = settings.customSeedColor,
                        onColorChange = { viewModel.setCustomSeedColor(it) },
                    )
                }
                SettingsDivider()
                SwitchItem(
                    title = stringResource(R.string.settings_dynamic_color),
                    subtitle = stringResource(R.string.settings_dynamic_color_desc),
                    checked = settings.dynamicFromTrack,
                    onCheckedChange = { viewModel.setDynamicFromTrack(it) }
                )
            }

            SettingsSectionCard(title = stringResource(R.string.settings_app_icon)) {
                val iconOptions = AppIconOption.entries
                val iconLabelResIds = listOf(R.string.settings_app_icon_normal, R.string.settings_app_icon_dynamic)
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    iconOptions.forEachIndexed { index, option ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            iconOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.appIcon == option,
                            onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setAppIcon(option) } },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            Text(stringResource(iconLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                Text(
                    stringResource(R.string.settings_app_icon_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }

            SettingsSectionCard(title = stringResource(R.string.settings_dark_theme)) {
                val modes = DarkModeOption.entries
                val labelResIds = listOf(R.string.settings_dark_system, R.string.settings_dark_light, R.string.settings_dark_dark)
                ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    modes.forEachIndexed { index, mode ->
                        val shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        }
                        ToggleButton(
                            checked = settings.darkMode == mode,
                            onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setDarkMode(mode) } },
                            modifier = Modifier.weight(1f),
                            shapes = shapes,
                        ) {
                            Text(stringResource(labelResIds[index]), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_home_sections)) {
                Text(
                    stringResource(R.string.settings_home_sections_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    settings.homeSections.forEachIndexed { index, config ->
                        HomeSectionRow(
                            config = config,
                            canMoveUp = index > 0,
                            canMoveDown = index < settings.homeSections.lastIndex,
                            onToggleVisible = { visible ->
                                haptic()
                                val updated = settings.homeSections.toMutableList()
                                updated[index] = config.copy(visible = visible)
                                viewModel.setHomeSections(updated)
                            },
                            onMoveUp = {
                                haptic()
                                val updated = settings.homeSections.toMutableList()
                                updated[index] = updated[index - 1].also { updated[index - 1] = updated[index] }
                                viewModel.setHomeSections(updated)
                            },
                            onMoveDown = {
                                haptic()
                                val updated = settings.homeSections.toMutableList()
                                updated[index] = updated[index + 1].also { updated[index + 1] = updated[index] }
                                viewModel.setHomeSections(updated)
                            },
                        )
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_app_background)) {
                run {
                    val modes = AppBackgroundMode.entries
                    val modeLabelResIds = listOf(
                        R.string.settings_app_background_dynamic,
                        R.string.settings_app_background_default,
                        R.string.settings_app_background_custom,
                        R.string.settings_app_background_player_only,
                    )
                    val modeDescResIds = listOf(
                        R.string.settings_app_background_dynamic_desc,
                        R.string.settings_app_background_default_desc,
                        R.string.settings_app_background_custom_desc,
                        R.string.settings_app_background_player_only_desc,
                    )
                    ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        modes.forEachIndexed { index, mode ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = settings.backgroundMode == mode,
                                onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setBackgroundMode(mode) } },
                                modifier = Modifier.weight(1f),
                                shapes = shapes,
                            ) {
                                Text(stringResource(modeLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Text(
                        stringResource(modeDescResIds[modes.indexOf(settings.backgroundMode).coerceAtLeast(0)]),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
                    )
                    if (settings.backgroundMode == AppBackgroundMode.CUSTOM) {
                        CustomColorPicker(
                            color = settings.backgroundCustomColor,
                            onColorChange = { viewModel.setBackgroundCustomColor(it) },
                        )
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_player_style)) {
                run {
                    val styles = listOf(PlayerStyle.PIXEL, PlayerStyle.CLASSIC)
                    val styleLabelResIds = listOf(R.string.settings_player_style_pixel, R.string.settings_player_style_legacy)
                    ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        styles.forEachIndexed { index, style ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                styles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = settings.playerStyle == style,
                                onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setPlayerStyle(style) } },
                                modifier = Modifier.weight(1f),
                                shapes = shapes,
                            ) {
                                Text(stringResource(styleLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            if (settings.playerStyle == PlayerStyle.CLASSIC) {
                SettingsSectionCard(title = stringResource(R.string.settings_player_background)) {
                    run {
                        val styles = listOf(PlayerBackgroundStyle.ORB, PlayerBackgroundStyle.MINIMAL)
                        val styleLabelResIds = listOf(
                            R.string.settings_player_background_orb,
                            R.string.settings_player_background_minimal,
                        )
                        ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                            styles.forEachIndexed { index, style ->
                                val shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    styles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                                ToggleButton(
                                    checked = settings.playerBackgroundStyle == style,
                                    onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setPlayerBackgroundStyle(style) } },
                                    modifier = Modifier.weight(1f),
                                    shapes = shapes,
                                ) {
                                    Text(stringResource(styleLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            SettingsSectionCard(title = stringResource(R.string.settings_seek_bar_style)) {
                run {
                    val styles = SeekBarStyle.entries
                    val styleLabelResIds = listOf(R.string.settings_seek_bar_style_classic, R.string.settings_seek_bar_style_wavy)
                    ButtonGroup(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        styles.forEachIndexed { index, style ->
                            val shapes = when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                styles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            }
                            ToggleButton(
                                checked = settings.seekBarStyle == style,
                                onCheckedChange = { checked -> if (checked) { haptic(); viewModel.setSeekBarStyle(style) } },
                                modifier = Modifier.weight(1f),
                                shapes = shapes,
                            ) {
                                Text(stringResource(styleLabelResIds[index]), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
