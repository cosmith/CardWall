# CardWall

CardWall is an Android app that asks you to review AnkiDroid flashcards before
you can use selected apps. By default, you review three cards to get ten minutes
of access. You choose the apps, decks, number of reviews, and access time.

[![Android CI](https://github.com/cosmith/CardWall/actions/workflows/android.yml/badge.svg)](https://github.com/cosmith/CardWall/actions/workflows/android.yml)
[![Release APK](https://github.com/cosmith/CardWall/actions/workflows/release.yml/badge.svg)](https://github.com/cosmith/CardWall/actions/workflows/release.yml)

[Releases](https://github.com/cosmith/CardWall/releases) |
[Installation guide](INSTALL.md) |
[Report a bug](https://github.com/cosmith/CardWall/issues)

> [!WARNING]
> **CardWall is purely vibe-coded.** It is an experimental project built with
> AI-generated code. It may contain bugs and hasn't had an independent security
> audit. CardWall writes review grades to AnkiDroid, so back up your collection
> before using it.

## Screenshots

| Question | Answer and grades | Settings |
| :---: | :---: | :---: |
| <img src="docs/screenshots/question.png" alt="CardWall question screen with review progress and optional Skip button" width="240"> | <img src="docs/screenshots/answer.png" alt="CardWall answer screen with Again, Hard, Good, and Easy grading buttons" width="240"> | <img src="docs/screenshots/settings.png" alt="CardWall setup and settings screen" width="240"> |

The screenshots show the app running in an Android emulator. The review screens
use a sample Italian card, and the settings screen shows initial setup.

## How it works

1. Choose which apps require a review before use, then choose your AnkiDroid decks.
   Selecting a parent deck includes its subdecks, including any you add later.
2. Open a selected app to start a review session. Reveal each answer and grade it
   with Anki's Again, Hard, Good, or Easy button. Every submitted grade counts
   toward the required number of reviews.
3. Finish the required reviews to use the app for the time you've set.
   Each app has its own access period, and time keeps passing when you switch apps
   or lock the screen.
4. After the access period ends, CardWall requires another review session before
   you can use the app.

CardWall uses your existing AnkiDroid collection and saves the grades you submit
back to AnkiDroid. You don't need a separate collection or account.

## Settings

| Setting | Default | Options |
| --- | --- | --- |
| Reviews to unlock | 3 cards | 1 to 20 reviews per session |
| Access window | 10 minutes | 1 to 120 minutes per app |
| Apps | Your selection | Choose which installed apps require reviews |
| Decks | Your selection | Choose which decks and subdecks to review |
| Show skip button | Off | Show or hide the optional Skip button |

When you enable Show skip button, you can skip a review session after confirming
in a dialog. Skipping gives you the usual access time without grading the current
card. Canceling the dialog returns you to the same card.

Unfinished review progress is discarded when you leave the app or lock the screen.
Access periods are cleared when the accessibility service or phone restarts.

## Installation

CardWall is intended for a Pixel running Android 17. Other devices and Android
versions haven't been validated. You install CardWall from an APK file, outside
the Play Store.

1. Install [AnkiDroid](https://ankidroid.org/) and sync or import your decks.
2. Install a CardWall APK from [GitHub Releases](https://github.com/cosmith/CardWall/releases).
   Android may ask you to allow installation from your browser or Files app.
   If no APK is available, see the build instructions below.
3. Open CardWall and grant access to your AnkiDroid collection. Choose your decks
   and apps in the settings.
4. Enable CardWall in Android Settings under Accessibility, then open a selected
   app to try a review session.

The [installation guide](INSTALL.md) includes setup checks and troubleshooting.
Updates signed with the same key can replace an existing installation without
uninstalling it.

## Privacy and permissions

CardWall has no ads, analytics, or cloud backend. It doesn't request internet
permission, and it blocks remote requests from flashcards. Your collection stays
in AnkiDroid, while CardWall stores its settings on your device.

The accessibility service detects which app you're using so it can show the
review screen. It doesn't request access to the contents of other apps' screens.
You can disable the service or uninstall CardWall in Android settings at any time.

## Limitations

- When no cards are due, CardWall shows random eligible cards with a Reviewed
  button. Reviewing them doesn't change their schedules.
- If AnkiDroid is unavailable or a grade can't be saved, CardWall allows access
  to the app and shows a warning.
- Cards keep their HTML, CSS, and JavaScript, but images, video, and audio are
  replaced with placeholders.
- Automated checks and emulator screenshots don't verify the AnkiDroid integration
  or accessibility behavior on your phone. Test both before relying on CardWall.

## Build and contribute

CardWall is written in Kotlin. It uses Compose for settings, a native Android
view for the review screen, and a WebView to display flashcards.

Install JDK 17, Android SDK 36, and Build Tools 37.0.0, then run:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is saved in `app/build/outputs/apk/debug/`.

For a signed release, copy `signing.properties.example` to `signing.properties`
and enter your keystore details. Run `./gradlew assembleRelease` to build the APK.
Use the original release key to update an existing installation, and never commit
the keystore or its passwords.

Every push to `main` starts the [release workflow](.github/workflows/release.yml).
The workflow runs tests and lint, then builds and signs the APK. It publishes the
APK to GitHub Releases with a checksum and a tag identifying the commit. Publishing
requires the repository's signing secrets. See the [release setup guide](docs/RELEASING.md)
for instructions and details on capturing screenshots.

To [report a bug](https://github.com/cosmith/CardWall/issues), include your Android
and AnkiDroid versions, what you expected, and what happened. Remove private card
content from screenshots and logs before sharing them.
