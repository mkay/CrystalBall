package de.singular.crystalball.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The side panel: where you are going, and how the app behaves.
 *
 * Split deliberately from the chord-settings sheet. Capo and chord naming are *musical context* you
 * change with a guitar in your hands, mid-session, and they belong in a sheet you can flick open
 * over the chord you are looking at. Everything here is either navigation or an app preference —
 * things you set once and forget.
 */
@Composable
fun CrystalDrawer(
    onDetect: () -> Unit,
    onShowChords: () -> Unit,
    onChordSettings: () -> Unit,
    onAppSettings: () -> Unit,
    onQuickHelp: () -> Unit,
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
                label = { Text("Show chords") },
                icon = { Icon(Icons.Default.GridView, contentDescription = null) },
                selected = false,
                onClick = onShowChords,
                modifier = itemPadding,
            )

            DrawerSectionLabel("Settings")
            NavigationDrawerItem(
                label = { Text("Capo & chord names") },
                icon = { Icon(Icons.Default.Tune, contentDescription = null) },
                selected = false,
                onClick = onChordSettings,
                modifier = itemPadding,
            )
            NavigationDrawerItem(
                label = { Text("App settings") },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                selected = false,
                onClick = onAppSettings,
                modifier = itemPadding,
            )

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

@Composable
private fun DrawerSectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
    )
}
