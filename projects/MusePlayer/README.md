# MUSE Player (v2)

Material 3 Expressive mini-player / headless controller for **Metrolist**
(`com.metrolist.music`). Package: `com.velvet.muse`, versionCode 2.

## What it is

Two things in one APK:

1. **A headless engine.** `MuseReceiver` exposes a broadcast API so the app can be
driven with zero UI — no screen flash, no app launch. Drives audio via the
framework `MediaBrowser`/`MediaController` bound to Metrolist's MediaLibraryService.
2. **A real UI.** Compose + Material 3 Expressive (`MaterialExpressiveTheme`,
spring motion, Material You dynamic colour) with four tabs: Now / Queue / Library / Search.

## Headless API

```
am broadcast -n com.velvet.muse/.MuseReceiver -a com.velvet.muse.CMD \
  --es cmd <probe|play|pause|toggle|next|prev|stop|shuffle|repeat|playfromsearch|browse|skipqueue> \
  [--es query "..."]
```

Read results with `logcat -d -s Muse`.

Note: the broadcast **must** be explicit (`-n com.velvet.muse/.MuseReceiver`).
Implicit broadcasts from shell are silently dropped.

## Build

Gradle 8.14.3 · AGP 8.13.2 · Kotlin 2.1.21 · material3 1.4.0 · compileSdk 36 · minSdk 24.

CI: `.github/workflows/build-muse.yml` (Actions → "Build MUSE APK" → Run workflow).
The workflow emits an **unsigned** release APK.

**Signing is deliberately off-GitHub** — the release keystore never leaves the device:

```
zipalign -f -p 4 app-release-unsigned.apk MUSE-v2-aligned.apk
apksigner sign --ks muse.keystore --out MUSE-v2.apk MUSE-v2-aligned.apk
```

Keeping the same signing key across versions means v2 upgrades over v1 in place.

## Known limitation

Transport control (play/pause/next/prev/seek/shuffle/repeat) is fully headless.
Selecting a **new** track is not: `playFromSearch` / `playFromMediaId` make Metrolist
resolve the item and it briefly surfaces. Search resolution is also fuzzy — it can
land on a cover rather than the original. Use the Queue tab's `skipToQueueItem`
for exact selection.
