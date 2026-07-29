// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.singular.crystalball.songs.Song

/**
 * The side panel: where you are going, and how the app behaves.
 *
 * Split deliberately from the capo sheet. The capo is *musical context* you change with a guitar in
 * your hands, mid-session, so it is not buried in here — it sits on the detect page itself, one tap
 * from the button you are already reaching for. Everything here is either navigation or an app
 * preference — things you set once and forget.
 *
 * The panel navigates, it does not act: [onDetect] lands on the detect page with the microphone
 * still closed, leaving the press that opens it to the user.
 */
@Composable
fun CrystalDrawer(
    onDetect: () -> Unit,
    onSongs: () -> Unit,
    onShowChords: () -> Unit,
    onSettings: () -> Unit,
    onQuickHelp: () -> Unit,
    recents: List<Song> = emptyList(),
    onOpenRecent: (Song) -> Unit = {},
) {
    // Take 4/5 of the screen width, leaving a strip of dimmed scrim on the right to tap-to-close.
    ModalDrawerSheet(Modifier.fillMaxWidth(0.8f)) {
        Column(Modifier.fillMaxSize()) {
            Text(
                "Crystal Ball",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp),
            )

            val itemPadding = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            NavigationDrawerItem(
                label = { Text("Detect chord") },
                icon = { Icon(Icons.Default.Mic, contentDescription = null) },
                selected = false,
                onClick = onDetect,
                modifier = itemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Chord Library") },
                icon = { Icon(Icons.Default.LibraryMusic, contentDescription = null) },
                selected = false,
                onClick = onShowChords,
                modifier = itemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Songs") },
                icon = { Icon(Icons.Default.QueueMusic, contentDescription = null) },
                selected = false,
                onClick = onSongs,
                modifier = itemPadding,
            )
            NavigationDrawerItem(
                label = { Text("Settings") },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selected = false,
                onClick = onSettings,
                modifier = itemPadding,
            )

            // Songs you were just at, straight from the panel — Rubber Ring's recents list, for the
            // same reason: the library screen is two taps away and sorted, but the song you want
            // back is nearly always one of the last few, and naming them saves the trip.
            //
            // Below the destinations rather than above them: this is a shortcut into Songs, and a
            // shortcut that pushes the fixed navigation down the panel has stopped being one.
            if (recents.isNotEmpty()) {
                DrawerSectionLabel("Recent songs")
                recents.forEach { song ->
                    NavigationDrawerItem(
                        label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                        selected = false,
                        onClick = { onOpenRecent(song) },
                        modifier = itemPadding,
                    )
                }
            }

            // Push help to the bottom — the conventional spot for help/about.
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            NavigationDrawerItem(
                label = { Text("Quick help") },
                icon = { Icon(Icons.Default.HelpOutline, contentDescription = null) },
                selected = false,
                onClick = onQuickHelp,
                modifier = Modifier
                    .padding(NavigationDrawerItemDefaults.ItemPadding)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

/** The quiet heading over a group of panel items, matching the settings screen's section labels. */
@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}
