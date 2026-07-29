// SPDX-License-Identifier: GPL-3.0-only

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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import de.singular.crystalball.NameStyle
import de.singular.crystalball.R
import de.singular.crystalball.Settings
import de.singular.crystalball.ThemeMode

/**
 * The two halves of the settings.
 *
 * The split is by *when you come here*, not by what the settings technically are, matching Title
 * Track. **Chords** is everything that changes what the next strum tells you — how a chord is named
 * once a capo is on, and whether the capo is asked about at all. **System** is the app's own set-up:
 * the screen, the theme, where your songs go, what version this is. One is visited when something
 * on the chord page reads wrong; the other is visited twice.
 *
 * Song backup sits under System rather than Chords, which is the one placement worth arguing:
 * capturing a song is very much part of the playing loop, but backing the library up is not — it is
 * the thing you do before a reinstall, with the guitar back on its stand.
 */
enum class SettingsTab(val title: String) {
    CHORDS("Chords"),
    SYSTEM("System"),
}

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
    onBackupSongs: () -> Unit,
    onRestoreSongs: () -> Unit,
    onAbout: () -> Unit,
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
        // Local, unlike the song library's tab: back from here leaves the settings altogether, so
        // there is no back chain a level up that needs to know which half is showing.
        var tab by rememberSaveable { mutableStateOf(SettingsTab.CHORDS) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal, containerColor = Color.Transparent) {
                SettingsTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(entry.title) },
                    )
                }
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                when (tab) {
                    SettingsTab.CHORDS -> ChordSettings(
                        settings = settings,
                        onNameStyleChange = onNameStyleChange,
                        onShowCapoOnStartChange = onShowCapoOnStartChange,
                    )

                    SettingsTab.SYSTEM -> SystemSettings(
                        settings = settings,
                        onKeepScreenOnChange = onKeepScreenOnChange,
                        onThemeModeChange = onThemeModeChange,
                        onBackupSongs = onBackupSongs,
                        onRestoreSongs = onRestoreSongs,
                        onAbout = onAbout,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChordSettings(
    settings: Settings,
    onNameStyleChange: (NameStyle) -> Unit,
    onShowCapoOnStartChange: (Boolean) -> Unit,
) {
    SettingsSectionLabel("Capo")
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
            example = "D, with \"sounds as E · capo 2\" beneath",
            onClick = { onNameStyleChange(NameStyle.SHAPE_FIRST) },
        )
    }
}

@Composable
private fun SystemSettings(
    settings: Settings,
    onKeepScreenOnChange: (Boolean) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onBackupSongs: () -> Unit,
    onRestoreSongs: () -> Unit,
    onAbout: () -> Unit,
) {
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

    SettingsSectionLabel("Songs")
    SettingActionRow(
        "Back up songs",
        "Save every song to a file",
        Icons.Default.Save,
        onBackupSongs,
    )
    SettingActionRow(
        "Restore songs",
        "Replace every song from a backup file",
        Icons.Default.Restore,
        onRestoreSongs,
    )
    SettingsCaption(
        "A backup holds your songs and nothing else — the settings on this page stay as " +
            "you set them here.",
    )

    // Last, and on this tab rather than in the side panel: the panel's slots belong to what you
    // reach for with a guitar in your hands. This is the page visited once out of curiosity and
    // once when filing a bug.
    SettingsSectionLabel("About")
    SettingActionRow(
        "About this app",
        "Version, source code, and how to get in touch",
        Icons.Default.Info,
        onAbout,
    )
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

/**
 * A settings row that does something rather than holding a value: icon, label, and a line saying
 * what the press will do.
 *
 * [SettingSwitchRow] with the switch taken out and a subtitle put in — these two are the only rows
 * here whose consequence is not simply the label being true or false, and "Replace every song" is a
 * thing to know before pressing rather than after.
 */
@Composable
private fun SettingActionRow(
    label: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
