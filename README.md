# Crystal Ball

An Android app that listens to your guitar and tells you what chord you just played. Press **Detect
Chord**, strum, and it shows the shape it thinks you fretted — plus the chords it nearly picked
instead, and other ways to play the same one up the neck.

Early-stage — expect rough edges. Feedback and bug reports welcome via Issues.

## Features

- **Detect a chord by ear** — press, strum, and it answers as soon as the ranking settles.
- **Ranked alternatives** — the best fit is a guess, not gospel, so the runner-ups are one tap away.
- **Chord dictionary** — *Show chords* looks any chord up by hand, without playing it.
- **Capo aware** — set a capo and the shapes match your hands, named the way you prefer.
- **Session comforts** — System/Light/Dark themes, keep-screen-on, and an in-app quick help sheet.
  Preferences persist across launches.

## How it works

Press the button and it listens. A strum is detected as a sharp rise in level; the pick transient is
skipped (it is a broadband click with no stable pitch), and chroma evidence is accumulated from the
frames after it. The answer is offered as soon as the ranking settles — a clean open chord resolves
in roughly a third of a second — and at the latest after ~1.4 s of chord. Strum again to retry
without touching the button.

Under that:

1. **STFT → chromagram.** A Hann-windowed 8192-point FFT every 2048 samples, folded into 12
   pitch-class bins over 65–2000 Hz, and normalised per frame so playing volume does not matter.
2. **Harmonic template matching.** 12 roots × 7 qualities = 84 templates, each modelling the
   overtone series a real chord deposits rather than a bare note mask, scored by Pearson
   correlation. Triads carry a small prior over sevenths and sus chords, which share most of their
   notes.
3. **Bass evidence.** A second chroma fold weighted by 1/f², so it reports the *lowest* note rather
   than a low-register average. This is what separates chords that are otherwise identical: Csus2
   and Gsus4 are the same three pitch classes, and only the bass note says which one you played.

Honest about accuracy: template matching on clean triads is good, and degrades on a boomy room, a
badly tuned guitar, or a phone held across the room. It is a practice aid, not ground truth — hence
the runner-up row, which is one tap away from correcting it.

### Chord vocabulary

`maj`, `min`, `dom7`, `maj7`, `min7`, `sus2`, `sus4`, across all 12 roots. Denser extensions (6ths,
9ths, altered dominants) are deliberately out of scope: their templates overlap the above too
heavily to survive single-microphone template matching, and adding them mostly degrades the chords
people actually play.

### Chord shapes

Standard tuning (EADGBE). Two sources: a **curated** table of the open-position grips a player
expects to see by name, and **generated** movable CAGED forms transposed up the neck, which cover
all 84 chords including those with no open shape. Majors and minors carry 5–9 shapes; m7 and sus
chords have only the three barre forms below the 15th fret, and the app shows what exists rather
than padding the row with fingerings nobody reaches for.

Every shape — curated and generated — is verified by unit test to sound its chord's notes and
nothing else, so a mistyped fret cannot ship.

### Capo

A capo changes nothing about detection: the microphone hears real pitches, so a D shape behind a
capo at the 2nd fret genuinely *is* an E chord, and that is what gets reported. What it changes is
the shape you finger, which is purely a matter of what the app draws.

A capo is a movable nut, so shapes are computed for the chord below the sounding one and drawn with
the capo where the nut goes — no special case beyond an accent-coloured nut to mark it. Fret numbers
are the ones printed on your neck, not counted from the capo. Shapes the capo pushes off the end of
the neck are dropped rather than offered. The capo tops out at the 7th fret: past that, some chords
have no shape left that fits, and every position offered should be able to show you something real.

## Tech stack

- **Language:** Kotlin (Java 17 target)
- **UI:** Jetpack Compose (Canvas for the chord diagrams)
- **Build:** Android Gradle Plugin 9.2.1 + Gradle 9.5.1 (via wrapper)
- **SDK:** `minSdk` 26 · `compile`/`targetSdk` 36
- **Capture:** `AudioRecord`, preferring the `UNPROCESSED` source — the default mic path runs AGC and
  noise suppression tuned for speech, which reshape the exact spectral balance the chromagram reads
- **DSP:** custom (radix-2 FFT, chromagram, template matching), no native/NDK or GPL dependencies
- **Async:** Kotlin Coroutines + Flow

The FFT and the chromagram front-end are adapted from
[RubberRing](https://github.com/mkay/RubberRing)'s offline chord detector, reshaped for live audio.

## Building

Requires a JDK (17+) and the Android SDK. Point the build at your SDK by creating a
`local.properties` file in the project root (this file is git-ignored):

```properties
sdk.dir=/path/to/Android/Sdk
```

Then build a debug APK:

```sh
./gradlew assembleDebug
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`; sideload it to a device to run it.

Run the tests — the DSP and the shape tables are pure Kotlin, so they cover the interesting parts
without a device:

```sh
./gradlew test
```

## Project layout

```
app/src/main/java/de/singular/crystalball/
  MainActivity.kt        Compose entry point, microphone permission, navigation
  DetectViewModel.kt     screen state, drives the listener
  Settings.kt            preferences + capo arithmetic
  audio/
    Fft.kt               radix-2 FFT (from RubberRing)
    Chromagram.kt        STFT -> 12-bin chroma + bass fold
    Chord.kt             root + quality, and the notes they imply
    ChordTemplates.kt    the 84 harmonic templates and their priors
    ChordRecognizer.kt   accumulates chroma, ranks candidates
    StrumGate.kt         adaptive noise floor: is the guitar sounding?
    ChordListener.kt     AudioRecord capture + auto-stop
  chords/
    Voicing.kt           a fingering, and what it sounds
    ChordLibrary.kt      curated open grips + generated CAGED forms
  ui/
    ChordDiagram.kt      the chord box (Canvas)
    DetectScreen.kt      detect / listening / result / browse panes
    CrystalDrawer.kt     the side panel
    ChordSettingsSheet.kt  capo + chord naming
    AppSettingsSheet.kt  theme + keep screen on
    QuickHelpSheet.kt    what it does, and what it won't
    Theme.kt             colours
```

## Roadmap

- Saving a chord to a collection / song.

## Disclaimer

This project was developed with the assistance of Claude under my direction and functional review.
