package de.singular.crystalball.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * What the app does and — just as usefully — what it will not do. The honest limits belong in front
 * of the user, not only in the README: a player who knows the vocabulary stops at sevenths will read
 * a wrong answer as a wrong answer, rather than doubting their own ears.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickHelpSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Quick help", style = MaterialTheme.typography.titleLarge)

            HelpSection(
                "Detecting a chord",
                "Press Detect Chord and strum. It listens until the answer settles — usually well " +
                    "under a second — and strumming again restarts it, so a bad take needs no " +
                    "second press. Hold the phone near the guitar; a quiet unplugged instrument " +
                    "across the room is hard work.",
            )
            HelpSection(
                "If it picks the wrong chord",
                "The best fit is a guess, not gospel. Tap anything in the \"Or did you mean\" row " +
                    "to see that chord instead.",
            )
            HelpSection(
                "Using a capo",
                "Set it under Capo & chord names. It does not change detection — the microphone " +
                    "hears the real chord either way — but it changes the shapes you are shown so " +
                    "they match your hands, and fret numbers are the ones printed on your neck.",
            )
            HelpSection(
                "What it knows",
                "Major, minor, 7, maj7, m7, sus2 and sus4, in standard tuning. Richer chords " +
                    "(6ths, 9ths, altered dominants) are out of scope and will come back as the " +
                    "nearest chord it does know.",
            )
        }
    }
}

@Composable
private fun HelpSection(title: String, body: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(2.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
