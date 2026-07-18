# Changelog

All notable changes to this project are documented here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/mkay/CrystalBall/compare/v0.3...HEAD
[0.3]: https://github.com/mkay/CrystalBall/releases/tag/v0.3
[0.2]: https://github.com/mkay/CrystalBall/releases/tag/v0.2
[0.1]: https://github.com/mkay/CrystalBall/releases/tag/v0.1
