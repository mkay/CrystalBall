package de.singular.crystalball.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import de.singular.crystalball.NameStyle
import de.singular.crystalball.R
import de.singular.crystalball.Settings
import de.singular.crystalball.ThemeMode

/**
 * How the app behaves, as opposed to which fret the capo is on.
 *
 * A full screen shown over whatever is underneath, matching Rubber Ring: the set-once options live
 * here, reached from the side panel, while [CapoSheet] keeps the capo alone — that is the one
 * setting you reach for mid-session with a guitar in your hands, so it stays a sheet that flicks
 * open over the chord you are looking at. [onClose] backs out to the previous screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onNameStyleChange: (NameStyle) -> Unit,
    onShowCapoOnStartChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text("Settings") },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        ) {
            SettingsSectionLabel("Chords")
            SettingSwitchRow(
                "Show capo on start",
                ImageVector.vectorResource(R.drawable.ic_capo),
                settings.showCapoOnStart,
                onShowCapoOnStartChange,
            )
            SettingsCaption(
                "Opens the capo sheet each time the app opens, so a capo you moved since " +
                    "yesterday is the first thing you set.",
            )

            SettingsSectionLabel("Chord names")
            SettingsCaption(
                "With a capo, the chord you hear and the shape you finger have different names.",
            )
            Column(Modifier.selectableGroup()) {
                NameStyleOption(
                    selected = settings.nameStyle == NameStyle.SOUNDING_FIRST,
                    title = "Name the chord you hear",
                    example = "E, with \"D shape · capo 2\" beneath",
                    onClick = { onNameStyleChange(NameStyle.SOUNDING_FIRST) },
                )
                NameStyleOption(
                    selected = settings.nameStyle == NameStyle.SHAPE_FIRST,
                    title = "Name the shape you play",
                    example = "D, with \"sounds E · capo 2\" beneath",
                    onClick = { onNameStyleChange(NameStyle.SHAPE_FIRST) },
                )
            }

            SettingsSectionLabel("Screen")
            SettingSwitchRow(
                "Keep screen on",
                ImageVector.vectorResource(R.drawable.ic_brightness_alert),
                settings.keepScreenOn,
                onKeepScreenOnChange,
            )
            SettingsCaption(
                "The display won't dim or lock while the app is open. Handy with a guitar in your " +
                    "hands, but it uses more battery.",
            )

            SettingsSectionLabel("Appearance")
            ThemeModeChips(
                mode = settings.themeMode,
                onSelect = onThemeModeChange,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** The line under a setting that says what it costs you, where the label alone is not enough. */
@Composable
private fun SettingsCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, bottom = 4.dp),
    )
}

/** A single-select row of Follow system / Light / Dark chips. */
@Composable
private fun ThemeModeChips(
    mode: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val label = mapOf(
            ThemeMode.SYSTEM to "System",
            ThemeMode.LIGHT to "Light",
            ThemeMode.DARK to "Dark",
        )
        ThemeMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onSelect(m) },
                label = { Text(label.getValue(m)) },
                shape = ControlShape,
            )
        }
    }
}

/** A settings row: icon + label with a trailing switch; tapping anywhere on the row toggles it. */
@Composable
private fun SettingSwitchRow(
    label: String,
    icon: ImageVector,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle(it) })
    }
}

@Composable
private fun NameStyleOption(
    selected: Boolean,
    title: String,
    example: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                example,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
