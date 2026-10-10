# Installing CardWall on the Pixel

## 1. Prepare AnkiDroid

1. Install or update to the current stable AnkiDroid.
2. Open AnkiDroid and complete its first-run storage/collection setup.
3. Import or sync the decks you want CardWall to use.

## 2. Install the APK

1. Download the latest `CardWall` APK to the Pixel.
2. Open it from Files or Downloads.
3. If Android asks, allow that file source to install unknown apps.
4. Confirm installation and open CardWall.

Because this is a private sideloaded app, Play Protect may display an additional confirmation. Verify that the package name is `fr.cosmith.cardwall` before continuing.

## 3. Complete CardWall setup

In CardWall:

1. Under **AnkiDroid**, confirm the app is detected.
2. Tap **Set up** beside **Collection permission** and approve AnkiDroid collection access.
3. Select one or more **Anki deck branches**. Selecting a parent includes its current and future subdecks.
4. Select the **Distracting apps** to gate.
5. Adjust the global review count and access duration if desired. Defaults are 3 cards and 10 minutes.
6. Tap **Set up** beside **Foreground detection**. In Android Accessibility settings, choose CardWall and enable **Use CardWall**.
7. Optionally enable **Show skip button** under **Gate rules**. It is off by default. The gate will ask for confirmation before opening the current app for the configured access window without completing the remaining reviews.

Android shows a broad accessibility warning because this service can observe which app is foreground. CardWall requests no view-content access and does not read text from other apps.

## 4. Smoke test

For a quick first test:

1. Set **Reviews to unlock** to 1 and **Access window** to 1 minute.
2. Configure YouTube or X.
3. Leave CardWall and open the configured app.
4. Reveal and grade one due Anki card. The gate should disappear immediately.
5. Wait one minute without leaving the target app. The gate should reappear.
6. Start another gate, then use Back or Home. Reopening the app should restart at 0 of 1.

Restore your preferred values after testing.

To check the optional skip button:

1. With **Show skip button** off, open a gated app and confirm there is no Skip button.
2. Enable the setting, return to the gated app, and tap **Skip** once a card has loaded.
3. Choose **Keep reviewing**, or dismiss the dialog with Back. The same card and review progress should remain.
4. Tap **Skip** again and confirm. Only that app should unlock for the configured duration; skipped cards should remain due in AnkiDroid.
5. Open the dialog and switch apps or lock the screen. The dialog and gate should both disappear; returning should start a fresh review session.
6. Disable the setting and check that the button is absent from the next gate. The setting should persist after restarting CardWall.

## Expected fail-open cases

CardWall allows the attempted app for the normal access-window duration and shows a warning when:

- AnkiDroid is missing or its collection is not initialized;
- collection permission is missing or revoked;
- selected deck branches no longer exist or contain no eligible cards; or
- AnkiDroid fails while saving a scheduled grade.

This prevents an integration failure from trapping you behind an unusable gate.

## Disable or uninstall

To disable gating, turn off CardWall under Android **Settings → Accessibility**. To remove it, disable the service and uninstall CardWall normally. To allow occasional skips instead, enable **Show skip button** in CardWall.

## Updating

Future APKs must be signed by the same CardWall release key to install over this version. Keep the separately delivered signing key backup private and safe.
