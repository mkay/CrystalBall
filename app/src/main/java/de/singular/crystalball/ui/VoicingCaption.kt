// SPDX-License-Identifier: GPL-3.0-only

package de.singular.crystalball.ui

import android.content.Context
import android.icu.text.MessageFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import de.singular.crystalball.Capo
import de.singular.crystalball.R
import de.singular.crystalball.ShapeLine
import de.singular.crystalball.chords.ShapeKind
import de.singular.crystalball.chords.Voicing

/**
 * What a grip is called, under its diagram: where on the neck it sits, and which shape it is.
 *
 * The [Voicing] carries the two facts and no words — see [ShapeKind] — so this is where they become
 * a caption, in whatever language the app is running in. The PDF wants the same sentence and cannot
 * be a composable, hence the plain [Context] form with a thin composable over it.
 *
 * The fret is spelled by ICU rather than by us. English wants 1st/2nd/3rd/5th, German wants "5.",
 * and other languages want things neither of us has thought about; the ordinal rules for all of
 * them ship with the platform, and the translation carries the branches it needs (English four,
 * German one). The alternative — a suffix table in Kotlin — is an English table with other
 * languages wedged into it.
 */
fun voicingCaption(context: Context, voicing: Voicing): String {
    val where =
        if (voicing.position == 0) context.getString(R.string.voicing_open)
        else MessageFormat(context.getString(R.string.voicing_fret), context.primaryLocale())
            .format(arrayOf<Any>(voicing.position))
    val shape = voicing.shape ?: return where
    return context.getString(R.string.voicing_caption, where, context.shapeName(shape))
}

@Composable
fun voicingCaption(voicing: Voicing): String = voicingCaption(LocalContext.current, voicing)

/**
 * The second name under a chord, worded: "D, A shape", or "sounds as E".
 *
 * [Capo.shapeLine] decides *which* line it is and leaves the words alone — see [ShapeLine]. The
 * shape clause is dropped rather than guessed at when the grip is a transposition of nothing.
 */
@Composable
fun chordShapeLine(line: ShapeLine): String {
    val context = LocalContext.current
    return when (line) {
        is ShapeLine.Fingered -> {
            val shape = line.shape ?: return line.chord.name
            stringResource(R.string.shape_line_fingered, line.chord.name, context.shapeName(shape))
        }
        is ShapeLine.Sounds -> stringResource(R.string.shape_line_sounds, line.chord.name)
    }
}

/**
 * The same line with the capo on the end, for the pages that do not state it themselves.
 *
 * The two pages that do — the detect result's own capo button sits right under it — use
 * [chordShapeLine] instead, so the capo is not said twice in three lines.
 */
@Composable
fun chordSubtitle(line: ShapeLine, capo: Int): String =
    stringResource(R.string.shape_line_with_capo, chordShapeLine(line), capo)

/** A shape's name: the open chord it transposes, or one of the two triads. */
private fun Context.shapeName(shape: ShapeKind): String = when (shape) {
    // The chord name is a symbol, not a word — it stays as it is, and the sentence goes around it.
    is ShapeKind.Grip -> getString(R.string.shape_grip, shape.chordName)
    ShapeKind.TopTriad -> getString(R.string.shape_top_triad)
    ShapeKind.MiddleTriad -> getString(R.string.shape_middle_triad)
}

/**
 * The locale the strings are actually coming from, which is not necessarily the JVM default: the
 * in-app language picker changes the one on the resources, and [MessageFormat] would otherwise
 * count ordinals by the device's.
 */
private fun Context.primaryLocale() = resources.configuration.locales[0]
