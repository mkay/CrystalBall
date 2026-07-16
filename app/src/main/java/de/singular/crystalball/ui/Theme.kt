package de.singular.crystalball.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.singular.crystalball.ThemeMode

/** The app's single accent, used for the primary action, the level meter, and the fretted dots. */
private val BrandPrimary = Color(0xFF919AC5)

/**
 * Content sitting *on* the accent. Dark, not white: [BrandPrimary] is a light periwinkle, so white
 * text over it lands around 2.8:1 — under the 4.5:1 needed to stay readable — while this near-black
 * clears 6:1. The same colour serves both themes, since the accent itself does not change.
 */
private val OnBrand = Color(0xFF1B1C22)

private val CrystalDarkColors = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = OnBrand,
    secondaryContainer = BrandPrimary,
    onSecondaryContainer = OnBrand,
)

private val CrystalLightColors = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = OnBrand,
    secondaryContainer = BrandPrimary,
    onSecondaryContainer = OnBrand,
)

/** Controls use a gentle corner rather than the fully-rounded Material default, as in RubberRing. */
val ControlShape = RoundedCornerShape(5.dp)

/** Whether [mode] means dark right now — resolving SYSTEM against the OS setting. */
@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun CrystalBallTheme(mode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isDark(mode)) CrystalDarkColors else CrystalLightColors,
        content = content,
    )
}
