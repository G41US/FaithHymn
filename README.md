# FaithHymn 3

The church hymn book app, rebuilt in Kotlin + Jetpack Compose.

- 1,001 hymns stored on the phone (works fully offline)
- Search by number, title or **any words from the lyrics**
- Swipe between hymns, pinch to resize text, dark mode
- Favourites, recent, and playlists (e.g. "Sunday Service") with play-all
- **Present mode**: one verse at a time in big text for a projector / TV / choir
- Share a verse as an image or as text
- Audio with lock-screen controls; download hymns for offline listening

## Audio files
The app plays `https://github.com/G41US/FaithHymn/releases/download/audio/<number>.mp3`.
To add recordings: create a GitHub release with tag **`audio`** and upload MP3s named by hymn number
(`1.mp3`, `245.mp3`, …). Hymns without a file show "No recording for this hymn yet".
Change the location in `app/build.gradle.kts` → `AUDIO_BASE_URL`.

## Building
GitHub Actions builds an APK on every push (Actions → latest run → *FaithHymn-apk*).
Push a tag like `v3.0.0` to also publish it as a GitHub release.

Signing: add these repo secrets so every build installs as an update over the last one:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `faithhymn`), `KEY_PASSWORD`.

Local: `./gradlew assembleRelease`. Screenshots: `./gradlew recordRoborazziDebug` → `app/screenshots/`.
