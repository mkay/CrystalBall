# Changelog

All notable changes to this project are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.5] - 2026-08-23

The chord library goes well past what the microphone can name.

### Added
- **Thirteen more chord qualities** — `dim`, `aug`, `m7b5`, `dim7`, `7sus4`, `6`, `m6`, `add9`, `9`, `maj9`, `m9`, `6/9` and `13`, at all twelve roots. The library draws 240 chords now, up from 84. Look one up without playing a note, or name one by hand when writing a part down — a bossa chart finally comes out right.
- The quality row holds one tier at a time, with a chip that swaps between the seven the app can hear and the thirteen it can only draw. Twenty chips in one scrolling row said nothing about how far it went.

### Changed
- **Detection is unchanged**, deliberately: it still hears the same seven qualities, ranks them the same way, and is as accurate as it was. The extensions are drawn but never scored, because scoring them would cost accuracy on the chords people actually play — and several cannot be resolved at all, a C6 and an Am7 being the same four pitch classes. Altered dominants stay out under a rule rather than a preference: admit `7b9` and you owe `7#9`, `7#11`, `7b13` and every combination.
- The extended shapes have no open chord to be named after, so they are named by the string their root sits on — "6th-string root" rather than a grip that does not exist.
- Following **Settings** from the "screen stays on" notice lands on the System tab, where that switch actually is, instead of on Chords.

### Fixed
- Content no longer scrolls up through the menu icon and the wordmark. The page now fades out behind them rather than colliding with them — most visible in the chord library, whose chip rows were drawn straight through "Crystal Ball".

## [0.4] - 2026-07-31

Crystal Ball speaks German, and the licence it ships under now says what was always meant.

### Added
- An "About this app" page (Settings → About): the version, where the source lives, where to report a bug, and the licence. Long-pressing the version copies it, so a bug report can name the exact build.
- A `COPYRIGHT` file carrying the copyright notice, the artwork's licence and the terms on the name, and an `SPDX-License-Identifier` header on every source file.
- **German**, and a language picker (Settings → System → Language) to choose it without changing the whole phone. On Android 13 and up the choice is the system's own per-app language, so the two agree; below that the app remembers it itself. Anything not yet translated falls back to English.
- **German note names (H/B)** as a setting of its own (Settings → Chords), independent of the app's language: what is elsewhere a B is written H, and B is what is elsewhere B flat. Spelling only — the songs you have already written down are re-spelled, not changed.
- A **Recent songs** list in the side panel: the last five songs you opened or changed, straight to the song rather than by way of the library. Recency counts both — a sheet you read from the stand every evening stays at the top even if you never edit it.

### Changed
- Settings are split into **Chords**, **System** and **About** tabs, matching Title Track. The division is by when you come to the page — Chords holds the capo prompt and how a chord is named, System holds the screen, the theme and song backup. About is a tab rather than a row at the foot of System, so it is one tap away whichever half is showing.
- Relicensed from AGPL-3.0 to **GPL-3.0-only**, matching the other apps here. The Affero clause covered network use this app does not have — it asks for no network permission at all — so the plain GPL says what was actually meant. Version 3 only, not "or any later version".
- The wordmark and the icon are now **CC BY 4.0** — free to use and modify with credit, rather than held back. Artwork withheld from the licence earns F-Droid's NonFreeAssets flag, and CC BY avoids the GPL's obligation to ship the editable SVGs as the artwork's source. The name stays outside both grants: a fork needs its own.

## [0.3] - 2026-07-18

The first published release. 0.1 and 0.2 were tagged but never shipped, so everything below is new to anyone installing from F-Droid.

### Added
- Song sheets — capture a part chord by chord, name it *Intro* or *Chorus*, and reorder the parts as the song takes shape. The finished sheet reads back as chord diagrams rather than letters, so it is playable from the music stand.
- A song sheet stays correctable — rename a part, fix a chord that was misheard without playing it again, and pick the voicing you prefer. Changing the capo keeps the key and redraws the shapes.
- Share a song sheet to any app that accepts it, and back the whole collection up to a file (Settings → Songs) to restore after a reinstall.
- Every way to play a chord, with your pick remembered — the alternative voicings walking up the neck are selectable, not just illustrative.
- Reach the capo from the chord library, and the "Capo set to N" line says where it is.
- The home screen is headed by the "Crystal Ball" wordmark and its tagline.

### Changed
- Chord detection is the way into a song: "Detect multiple chords" captures a run right on the home screen, then **Done → Save as…** names the part and its destination. The Songs library is open-and-manage only.
- The capo sheet closes once it has been used.

### Fixed
- The fret-number label no longer collides with a barre.
- A song page is no longer printed twice.

### Known issues
- Capturing a run of chords relies on you muting between them: a chord left ringing is read as the next one, so a forgotten mute yields a *wrong* chord rather than a missing one. The capture screen asks for the mute, and the review pane after **Done** is there to correct what slipped through.

## [0.2] - 2026-07-18

Tagged while preparing the F-Droid submission, never published. Its contents ship as 0.3.

## [0.1] - 2026-07-16

Tagged but never published.

### Added
- Detect a chord by ear, with ranked runner-ups, chord shapes, capo-aware redrawing, a chord dictionary, and System/Light/Dark themes.

[Unreleased]: https://github.com/mkay/CrystalBall/compare/v0.5...HEAD
[0.5]: https://github.com/mkay/CrystalBall/releases/tag/v0.5
[0.4]: https://github.com/mkay/CrystalBall/releases/tag/v0.4
[0.3]: https://github.com/mkay/CrystalBall/releases/tag/v0.3
[0.2]: https://github.com/mkay/CrystalBall/releases/tag/v0.2
[0.1]: https://github.com/mkay/CrystalBall/releases/tag/v0.1
