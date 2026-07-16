package de.singular.crystalball.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.singular.crystalball.Capo
import de.singular.crystalball.Settings

/**
 * The capo, as a bottom sheet.
 *
 * This is musical context rather than an app preference — the one thing that changes with a guitar
 * already in your hands — so it stays in a sheet that flicks open over the chord you are looking at,
 * while [AppSettingsSheet] holds the set-once options.
 *
 * The capo is not on the main screen, so the one thing it must not do is go unnoticed: a capo left
 * set from yesterday would silently make every diagram wrong. It does not, because a non-zero capo
 * always shows up in the result's own subtitle ("D shape · capo 2") and in the accent-coloured nut
 * on every diagram — the setting is out of sight but its effect never is. Players who reposition a
 * capo most sessions can also have this sheet greet them at launch, via "Show capo on start".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapoSheet(
    settings: Settings,
    onCapoChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Capo", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(16.dp))
            Text(
                "Detection is unaffected — the microphone hears the real chord either way. " +
                    "This changes the shapes you're shown, so they match your hands.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (fret in 0..Capo.MAX_FRET) {
                    FilterChip(
                        selected = settings.capo == fret,
                        onClick = { onCapoChange(fret) },
                        shape = ControlShape,
                        label = { Text(if (fret == 0) "None" else "$fret") },
                    )
                }
            }
        }
    }
}
