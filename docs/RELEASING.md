# Automated releases

Every push to `main` starts **Release APK**. It tests and lints the release variant,
builds the optimized APK, signs it with the existing CardWall key, verifies the
signature, and publishes the APK plus `SHA256SUMS.txt` to GitHub Releases.

Each push builds its head commit. A release is tagged `main-<full commit SHA>`.
Re-running a workflow replaces the assets on that same release. Builds for separate
pushes are not canceled. A manual run is also available from the Actions page on `main`.

## One-time signing setup

Use the original CardWall release keystore from the separately stored signing-key
backup. Generating a new key would prevent existing installations from updating.

Under **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
| --- | --- |
| `CARDWALL_KEYSTORE_BASE64` | Base64-encoded contents of the original `.jks` file |
| `CARDWALL_KEYSTORE_PASSWORD` | Its `storePassword` |
| `CARDWALL_KEY_ALIAS` | Its `keyAlias` (originally `cardwall`) |
| `CARDWALL_KEY_PASSWORD` | Its `keyPassword` |

Use the values from the private `signing.properties` backup. Do not paste secrets
into issues, commits, release notes, or chat. If you already use GitHub CLI, you can
upload the keystore without displaying its contents:

```bash
base64 < /path/to/cardwall-release.jks | gh secret set CARDWALL_KEYSTORE_BASE64 --repo cosmith/CardWall
gh secret set CARDWALL_KEYSTORE_PASSWORD --repo cosmith/CardWall
gh secret set CARDWALL_KEY_ALIAS --repo cosmith/CardWall
gh secret set CARDWALL_KEY_PASSWORD --repo cosmith/CardWall
```

The last three commands prompt for the secret values. Once configured, re-run the
failed **Release APK** workflow, or push another commit to `main`.

The workflow fails at signing if a required secret is missing. It never publishes
an unsigned APK or substitutes a debug key. The keystore is decoded only into a
temporary runner file and deleted after signing; it is never uploaded as an artifact.
The publishing job receives only the signed APK and checksum, and uses GitHub's
automatic token with `contents: write`. No personal access token is needed.

## Build numbers

CI uses version name `0.1.3-main.<run number>` and version code `1000 + run number`.
The code is higher than the original manually delivered builds and increases with
each workflow run. A re-run keeps its original number. Local builds retain their
normal version unless `-PcardwallVersionCode` or `-PcardwallVersionName` is supplied.

## Screenshots

The **App screenshots** workflow runs on demand and when its capture harness changes.
It runs the real app UI on an API 35 emulator and uploads three PNGs as the
`cardwall-screenshots` artifact. The review screenshots use a synthetic Italian
flashcard; the settings screenshot shows a fresh installation. They do not imply
device-level validation of Anki integration or accessibility gating on Android 17.
