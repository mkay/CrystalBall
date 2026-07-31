// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.annotation.StringRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.singular.crystalball.R

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
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleLarge)

            HelpSection(R.string.help_detecting_title, R.string.help_detecting_body)
            HelpSection(R.string.help_wrong_chord_title, R.string.help_wrong_chord_body)
            HelpSection(R.string.help_capo_title, R.string.help_capo_body)
            HelpSection(R.string.help_vocabulary_title, R.string.help_vocabulary_body)
        }
    }
}

@Composable
private fun HelpSection(@StringRes title: Int, @StringRes body: Int) {
    Spacer(Modifier.height(20.dp))
    Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(2.dp))
    Text(
        stringResource(body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
