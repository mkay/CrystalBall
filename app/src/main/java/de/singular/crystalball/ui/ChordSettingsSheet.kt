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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.singular.crystalball.Capo
import de.singular.crystalball.NameStyle
import de.singular.crystalball.Settings

/**
 * Capo and chord naming, as a bottom sheet.
 *
 * These are musical context rather than app preferences — things you change with a guitar already
 * in your hands — so they stay in a sheet that flicks open over the chord you are looking at,
 * while [AppSettingsSheet] holds the set-once options.
 *
 * The capo is not on the main screen, so the one thing it must not do is go unnoticed: a capo left
 * set from yesterday would silently make every diagram wrong. It does not, because a non-zero capo
 * always shows up in the result's own subtitle ("D shape · capo 2") and in the accent-coloured nut
 * on every diagram — the setting is out of sight but its effect never is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChordSettingsSheet(
    settings: Settings,
    onCapoChange: (Int) -> Unit,
    onNameStyleChange: (NameStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Scrollable: the content is taller than the sheet's resting height on a phone, and the
        // last setting must not be one the user cannot reach.
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Capo & chord names", style = MaterialTheme.typography.titleLarge)

            Spacer(Modifier.height(24.dp))
            Text("Capo", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
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

            Spacer(Modifier.height(28.dp))
            Text("Chord names", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "With a capo, the chord you hear and the shape you finger have different names.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
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
            .padding(vertical = 4.dp),
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
