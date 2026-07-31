// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import de.singular.crystalball.NameStyle
import de.singular.crystalball.R
import de.singular.crystalball.Settings
import de.singular.crystalball.ThemeMode
import de.singular.crystalball.audio.NoteNaming
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * The two halves of the settings, and the page that is not a setting at all.
 *
 * The split is by *when you come here*, not by what the settings technically are, matching Title
 * Track. **Chords** is everything that changes what the next strum tells you — how a chord is named
 * once a capo is on, and whether the capo is asked about at all. **System** is the app's own set-up:
 * the screen, the theme, where your songs go. One is visited when something on the chord page reads
 * wrong; the other is visited twice.
 *
 * Song backup sits under System rather than Chords, which is the one placement worth arguing:
 * capturing a song is very much part of the playing loop, but backing the library up is not — it is
 * the thing you do before a reinstall, with the guitar back on its stand.
 *
 * **About** is the odd one, and is here because the tab row is the only always-visible strip on this
 * screen. It was a row at the foot of System opening a screen of its own, which is where a footer
 * item belongs right up until the settings acquire tabs — then it is a destination buried inside one
 * of two halves, with nothing about "System" that says the app's version and its bug tracker are in
 * there. A tab is one tap from either half and says its own name whichever half is showing.
 *
 * The honest cost is that two of these tabs set things and the third does not, so the row is no
 * longer three of a kind. The alternative was pinning the About row under the tabs, which puts a
 * destination *above* the settings and inverts where a footer sits. Between a slightly mixed tab row
 * and a slightly upside-down page, the tab row is the one that stays legible as it grows.
 */
enum class SettingsTab(@StringRes val title: Int) {
    CHORDS(R.string.settings_tab_chords),
    SYSTEM(R.string.settings_tab_system),
    ABOUT(R.string.settings_tab_about),
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
    onGermanNotesChange: (Boolean) -> Unit,
    onShowCapoOnStartChange: (Boolean) -> Unit,
    onBackupSongs: () -> Unit,
    onRestoreSongs: () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
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
                        text = { Text(stringResource(entry.title)) },
                    )
                }
            }
            // Each tab scrolls on its own, rather than the settings being poured into one shared
            // scroller. Two reasons: About brings its own layout and its own scroll — it is a page,
            // not a column of rows — and a shared scroll state carries its offset across a tab
            // switch, landing you halfway down the one you just arrived at.
            when (tab) {
                SettingsTab.CHORDS -> SettingsPage {
                    ChordSettings(
                        settings = settings,
                        onNameStyleChange = onNameStyleChange,
                        onGermanNotesChange = onGermanNotesChange,
                        onShowCapoOnStartChange = onShowCapoOnStartChange,
                    )
                }

                SettingsTab.SYSTEM -> SettingsPage {
                    SystemSettings(
                        settings = settings,
                        onKeepScreenOnChange = onKeepScreenOnChange,
                        onThemeModeChange = onThemeModeChange,
                        onBackupSongs = onBackupSongs,
                        onRestoreSongs = onRestoreSongs,
                    )
                }

                SettingsTab.ABOUT -> AboutScreen()
            }
        }
    }
}

/** The ground the two settings halves stand on: the whole tab, scrolling, clear of the bottom. */
@Composable
private fun SettingsPage(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 16.dp),
        content = content,
    )
}

@Composable
private fun ChordSettings(
    settings: Settings,
    onNameStyleChange: (NameStyle) -> Unit,
    onGermanNotesChange: (Boolean) -> Unit,
    onShowCapoOnStartChange: (Boolean) -> Unit,
) {
    SettingsSectionLabel(R.string.settings_section_capo)
    SettingSwitchRow(
        R.string.setting_show_capo_on_start,
        ImageVector.vectorResource(R.drawable.ic_capo),
        settings.showCapoOnStart,
        onShowCapoOnStartChange,
    )
    SettingsCaption(R.string.setting_show_capo_on_start_caption)

    SettingsSectionLabel(R.string.settings_section_chord_names)
    SettingSwitchRow(
        R.string.setting_german_notes,
        Icons.Default.MusicNote,
        settings.noteNaming == NoteNaming.GERMAN,
        onGermanNotesChange,
    )
    SettingsCaption(R.string.setting_german_notes_caption)

    SettingsSectionLabel(R.string.settings_section_capo_names)
    SettingsCaption(R.string.setting_name_style_caption)
    Column(Modifier.selectableGroup()) {
        NameStyleOption(
            selected = settings.nameStyle == NameStyle.SOUNDING_FIRST,
            title = R.string.setting_name_sounding,
            example = R.string.setting_name_sounding_example,
            onClick = { onNameStyleChange(NameStyle.SOUNDING_FIRST) },
        )
        NameStyleOption(
            selected = settings.nameStyle == NameStyle.SHAPE_FIRST,
            title = R.string.setting_name_shape,
            example = R.string.setting_name_shape_example,
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
) {
    SettingsSectionLabel(R.string.settings_section_screen)
    SettingSwitchRow(
        R.string.setting_keep_screen_on,
        ImageVector.vectorResource(R.drawable.ic_brightness_alert),
        settings.keepScreenOn,
        onKeepScreenOnChange,
    )
    SettingsCaption(R.string.setting_keep_screen_on_caption)

    SettingsSectionLabel(R.string.settings_section_appearance)
    ThemeModeChips(
        mode = settings.themeMode,
        onSelect = onThemeModeChange,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )

    SettingsSectionLabel(R.string.settings_section_language)
    LanguageSetting()

    SettingsSectionLabel(R.string.settings_section_songs)
    SettingActionRow(
        R.string.setting_backup,
        stringResource(R.string.setting_backup_subtitle),
        Icons.Default.Save,
        onBackupSongs,
    )
    SettingActionRow(
        R.string.setting_restore,
        stringResource(R.string.setting_restore_subtitle),
        Icons.Default.Restore,
        onRestoreSongs,
    )
    SettingsCaption(R.string.setting_backup_caption)
}

@Composable
private fun SettingsSectionLabel(@StringRes text: Int) {
    Text(
        stringResource(text).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}

/** The line under a setting that says what it costs you, where the label alone is not enough. */
@Composable
private fun SettingsCaption(@StringRes text: Int) {
    Text(
        stringResource(text),
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
            ThemeMode.SYSTEM to R.string.theme_system,
            ThemeMode.LIGHT to R.string.theme_light,
            ThemeMode.DARK to R.string.theme_dark,
        )
        ThemeMode.entries.forEach { m ->
            FilterChip(
                selected = mode == m,
                onClick = { onSelect(m) },
                label = { Text(stringResource(label.getValue(m))) },
                shape = ControlShape,
            )
        }
    }
}

/** A settings row: icon + label with a trailing switch; tapping anywhere on the row toggles it. */
@Composable
private fun SettingSwitchRow(
    @StringRes label: Int,
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
        Text(
            stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
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
    @StringRes label: Int,
    // A string rather than a resource: the language row's subtitle is the chosen language's own
    // name for itself, which is not in our string table at all.
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
            Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
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
    @StringRes title: Int,
    @StringRes example: Int,
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
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(example),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * One offered language: its BCP-47 [tag], or null for "follow the device".
 *
 * [label] is deliberately the language's name *in that language* — Deutsch, not German. Someone who
 * has landed in a language they can't read needs to find their way out, and the only word on the
 * screen they are sure to recognise is their own language's name for itself.
 */
private data class LanguageChoice(val tag: String?, val label: String)

/**
 * The languages on offer: the device default, then everything listed in `supported_locales`,
 * sorted by how each names itself.
 */
@Composable
private fun rememberLanguageChoices(): List<LanguageChoice> {
    val systemLabel = stringResource(R.string.language_system)
    val tags = stringArrayResource(R.array.supported_locales)
    return remember(systemLabel, tags) {
        val offered = tags.map { tag ->
            val locale = Locale.forLanguageTag(tag)
            // Ask the locale to name itself, then fix the case: several languages write their own
            // name lowercase mid-sentence (français, español) but expect a capital when it stands
            // alone as a label.
            val own = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
            LanguageChoice(tag, own)
        }.sortedBy { it.label }
        listOf(LanguageChoice(null, systemLabel)) + offered
    }
}

/** The current app language as a BCP-47 tag, or null when it is following the device. */
private fun currentLanguageTag(): String? =
    AppCompatDelegate.getApplicationLocales().toLanguageTags().takeIf { it.isNotEmpty() }
        ?.substringBefore(',')

/**
 * The language row and its picker.
 *
 * Applying a choice goes through [AppCompatDelegate], not a preference of our own: on Android 13+
 * that writes through to the system's per-app language, so this picker and the one in Android's
 * own Settings agree with each other instead of quietly disagreeing. Selecting recreates the
 * activity, which is what re-reads the resources — so there is nothing to do afterwards.
 */
@Composable
private fun LanguageSetting() {
    val choices = rememberLanguageChoices()
    // Read once per composition rather than held in state: the activity is recreated on change,
    // so this is re-read with the new value on the way back up.
    val currentTag = currentLanguageTag()
    val current = choices.firstOrNull { it.tag == currentTag } ?: choices.first()
    var picking by remember { mutableStateOf(false) }

    SettingActionRow(
        label = R.string.setting_language,
        subtitle = current.label,
        icon = Icons.Default.Language,
        onClick = { picking = true },
    )

    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.setting_language)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    choices.forEach { choice ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(ControlShape)
                                .clickable {
                                    picking = false
                                    AppCompatDelegate.setApplicationLocales(
                                        if (choice.tag == null) {
                                            LocaleListCompat.getEmptyLocaleList()
                                        } else {
                                            LocaleListCompat.forLanguageTags(choice.tag)
                                        },
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = choice.tag == current.tag,
                                onClick = null, // the whole row is the target
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { picking = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
