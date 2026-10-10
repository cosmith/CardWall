# CardWall

CardWall puts a short Anki review between you and distracting Android apps.

When a configured app becomes foreground—whether from its launcher icon, Recents, a notification, a deep link, or a share action—CardWall covers it with a full-screen gate. Complete the configured number of reviews and the attempted app becomes available for a limited wall-clock window.

CardWall is a personal, local-first Android app. It has no account, analytics, ads, cloud service, or internet permission.

## MVP behavior

- Reads cards from the current stable AnkiDroid collection.
- Writes real scheduled reviews back with Again, Hard, Good, or Easy.
- Counts every submitted grade, including Again, toward the unlock target.
- Uses AnkiDroid's normal learning, review, and new-card queue.
- Chooses among selected deck branches randomly, weighted by their due-card counts.
- Includes all current and future subdecks when a parent deck is selected.
- Falls back to a random non-suspended, non-buried card when nothing is due.
- Shows one `Reviewed` action for fallback cards and never changes their schedule.
- Uses one global rule for all configured apps: 3 reviews and 10 minutes by default.
- Keeps independent access windows for each distracting app.
- Expires windows using monotonic elapsed time, including time spent in other apps.
- Shows a new gate immediately when a window expires while its app remains foreground.
- Discards unfinished progress on Back, Home, `Close app`, screen lock, or switching away.
- Clears all access windows when the accessibility service or phone restarts.
- Fails open with a visible warning if AnkiDroid is missing, unavailable, or cannot save a grade.

The in-app gate has no skip or emergency bypass. Android still lets the device owner disable CardWall's accessibility service or uninstall CardWall.

## Media limitation in v0.1

AnkiDroid's public content-provider API returns rendered card HTML and the names of media files, but scoped storage does not expose those media bytes to another app. CardWall v0.1 therefore:

- preserves rendered HTML, CSS, and card JavaScript;
- blocks all remote network requests; and
- replaces images, video, and audio with a small placeholder.

Direct local image support and audio are deferred.

## Requirements

- A Pixel running Android 17 (the intentionally narrow MVP target).
- Current stable AnkiDroid, with first-run collection setup complete.
- AnkiDroid's `READ_WRITE_DATABASE` permission granted to CardWall.
- CardWall accessibility service enabled.

The project compiles with SDK 36 and targets SDK 36; it is intended and tested structurally for forward compatibility with Android 17. Device-level validation must be performed on the target Pixel.

## Build

Install Android Studio with JDK 17 and Android SDK 36, then run:

```bash
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

For a signed release, copy `signing.properties.example` to `signing.properties`, create a private keystore, fill in the four values, and run:

```bash
./gradlew assembleRelease
```

Never commit `signing.properties` or a keystore. Both are ignored by Git.

## Architecture

- `MainActivity` and Compose provide onboarding, app/deck selection, and global rule settings.
- `CardWallAccessibilityService` observes only foreground package changes; it does not inspect screen text or view contents.
- A `TYPE_ACCESSIBILITY_OVERLAY` window renders the gate without relying on background activity launches.
- `AnkiRepository` uses AnkiDroid's public `content://com.ichi2.anki.flashcards` contract.
- Settings are stored locally in `SharedPreferences`; access windows and partial review progress stay memory-only.

The implementation is independent. No decompiled Ascent source or assets are included.

## Install and configure

See [INSTALL.md](INSTALL.md).
