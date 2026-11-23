# Releasing Kiyo and signing it

This file explains how updates reach people. Kiyo's in-app updater reads GitHub Releases, so a release is an update.

## Two kinds of release

| | Nightly | Stable |
|---|---|---|
| When | every push to `main` | push a tag like `v5.6.1`, or Actions > *Build & Release APK* > *Run workflow* with **stable** ticked |
| Version | `5.6.0.37` (the last number counts up by itself) | `5.6.1` (the version you choose) |
| GitHub label | Pre-release | Latest release |
| Who is offered it | people on the **Nightly** channel in the app | everyone on the **Release** channel, and the website |

Nightlies number themselves on top of whichever is higher: `versionName` in `app/build.gradle.kts` or the newest
`vX.Y.Z` tag. So after a stable release `v5.6.1`, the next nightlies are `5.6.1.1`, `5.6.1.2`, ... on their own, and
you don't have to edit `versionName` (raising it is still fine, it just keeps the file honest).

## How people get told

- The app checks GitHub at launch, and `UpdateCheckWorker` checks again every 6 hours in the background (needs a
  network; skipped when update checks or update notifications are off in settings).
- A new release shows the "Kiyo x.y.z is out" notification, at most three times per release (now, +2 weeks, +3 months).
- A release only counts once its APK is attached, so a half-uploaded release is never announced.
- The Release channel only sees stable releases, the Nightly channel sees both. Installed from a stable build and
  want nightlies? Switch the channel in the app's version sheet.

## Signing key (do this once, before real users)

Android only installs an update over an app if both were signed with the **same key**. Until you add your own key,
CI signs with the public fallback key that ships in `.github/signing`, which anyone can use. Changing the key later
means every user has to uninstall Kiyo first (and loses local data), so set it now.

1. Make a key (keep the file and passwords somewhere safe, losing them means no more updates):
   `keytool -genkeypair -v -keystore kiyo-release.keystore -alias kiyo -keyalg RSA -keysize 4096 -validity 36500`
2. Turn it into text: `base64 -w0 kiyo-release.keystore` (on macOS: `base64 -i kiyo-release.keystore`)
3. In GitHub, open the repo > Settings > Secrets and variables > Actions > New repository secret, and add:
   `KEYSTORE_BASE64` (the text from step 2), `STORE_PASSWORD`, `KEY_ALIAS` (`kiyo` above) and `KEY_PASSWORD`.
4. Run the workflow once. The log says "Using the signing key from repository secrets" instead of the fallback warning.
5. Everyone who installed a build signed with the old fallback key has to uninstall once and install the new one.

## What the app checks before installing

The release carries a `sha256sums.txt`. The app downloads the APK (continuing from where it stopped if the
connection drops), compares its SHA-256 with that file, and only offers to install when they match.
