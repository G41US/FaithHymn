# FaithHymns 3

The church hymn book app, rebuilt in Kotlin + Jetpack Compose.

- 1,001 hymns stored on the phone (works fully offline)
- Music markings kept and explained: coloured voice-part pills (Sop, ATB, S/A…), dynamics (p, mf, ff, rit.),
  echo lines, chorus repeat signs and time signatures, verse numbers, and a "Key" sheet. Pick your part to dim the others.
- Search by number, title or lyrics, with filters, grouped results, voice search, history and typo tolerance
- Home: greeting, hymn of the day (Read / Play), 1–100 jump chips, A–Z / number scrubber, audio badges
- Reader: collapsing header, chorus cards, verses highlight and scroll along with the recording, long-press a verse to share it as an image
- Library: favourites (grid or list), playlists with gradient covers, service planner (date + slots), drag to reorder, swipe actions, share by link or QR
- Player: full screen with lyrics, repeat, tempo and key change for choir practice, sleep timer, download all
- Present mode: black / gradient / photo backgrounds, leader view, TV output (phone becomes the remote), follow the recording
- Settings: Auto / Light / Dark / warm Night theme, accent colours, app icon choices, in-app updates, share-the-app QR

## Audio files
The app plays `https://github.com/G41US/FaithHymn/releases/download/audio/<number>.mp3`.
To add recordings: create a GitHub release with tag **`audio`** and upload MP3s named by hymn number
(`1.mp3`, `245.mp3`, …). Hymns without a file show "No recording for this hymn yet".
Change the location in `app/build.gradle.kts` → `AUDIO_BASE_URL`.

## Building
GitHub Actions builds an APK on every push (Actions → latest run → *FaithHymn-apk*).
When the signing secrets are set, every push to `main` is also published as release `v3.0.<run>`; the app's Settings → Check for updates installs it.

Signing: add these repo secrets so every build installs as an update over the last one:
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` (= `faithhymn`), `KEY_PASSWORD`.

Local: `./gradlew assembleRelease`. Screenshots: `./gradlew recordRoborazziDebug` → `app/screenshots/`.
