# Publishing Geo Pin Cam to Google Play

## What is already done in this repo

- **Release signing** wired into `app/build.gradle.kts`, reading from
  `keystore.properties` at the project root (git‑ignored).
- **Upload keystore generated**: `geopincam-upload.jks` (alias `geopincam`,
  RSA 2048, valid to 2054). Password is in `keystore.properties`.
  - SHA‑256: `12:EC:64:25:55:B3:97:3B:0B:0A:70:38:F9:5A:67:23:C6:B9:C8:F1:35:1E:C4:4B:B9:B7:C8:2B:EE:F4:67:C0`
- **`.gitignore`** added (excludes the keystore, `keystore.properties`,
  `local.properties`, build output).
- **Signed release AAB built**: `app/build/outputs/bundle/release/app-release.aab`
  (rebuild any time with `./gradlew bundleRelease`).
- Release build already uses R8 + resource shrinking; `assembleRelease` and
  `bundleRelease` pass clean.
- Store graphics generated in `E:\3. Android App developmment\Geo Pin Cam\images`.
- Draft privacy policy: `play/PRIVACY_POLICY.md`. Draft listing: `play/store-listing.md`.

## ⚠️ BACK UP THE KEYSTORE

Copy `geopincam-upload.jks` **and** `keystore.properties` somewhere safe and
private (password manager, encrypted backup). Keep **Play App Signing** enabled
when you create the app — then Google holds the real signing key and a lost
upload key can be reset from Play Console → Setup → App integrity. Without Play
App Signing, a lost key means you can never update the app.

## Before you build the final AAB

1. **Contact email / privacy URL** — edit `play/PRIVACY_POLICY.md`
   (`your-email@example.com`), host it somewhere public (GitHub Pages, a Google
   Site, Notion public page…), and note the URL.
2. **`versionCode` / `versionName`** in `app/build.gradle.kts` — `1` / `"1.0"` is
   fine for the first upload. Bump `versionCode` for every subsequent upload.
3. **Target API level** — currently `targetSdk = 37`. Google Play requires new
   apps to target a **released** API level within a year of the latest. If API 37
   is not final at submission, set `compileSdk`/`targetSdk` to the highest
   released level (35 or 36) and rebuild.
4. **App icon in the manifest** — the adaptive launcher icon is set. The 512px
   Play Store icon is `images/icon_512.png` (uploaded separately in the Console).
5. Run once more:
   ```
   ./gradlew clean :app:lintRelease :app:bundleRelease
   ```
   Fix any **Error** severity lint issues (current build has only benign
   warnings). Then upload `app/build/outputs/bundle/release/app-release.aab`.

## In the Play Console (play.google.com/console)

1. **Create app** — name "Geo Pin Cam", English (US), App, Free. Accept the
   declarations.
2. **App access** — "All functionality is available without special access".
3. **Ads** — No.
4. **Content rating** — complete the questionnaire (Photography, no ads, no UGC
   sharing → expected Everyone / PEGI 3).
5. **Target audience** — 13 and older. Not appealing to children.
6. **Data safety** — fill from the table in `play/store-listing.md`. Key points:
   collects approximate + precise **Location** (shared with Google geocoder and
   OpenStreetMap as coordinates only, for App functionality), collects **Photos/
   videos** and video **audio** (on‑device only, not shared), encrypted in
   transit, not required to use the app.
7. **Privacy policy** — paste your hosted URL.
8. **Government apps / financial / health** — No.
9. **Store listing** — from `play/store-listing.md`:
   - Short + full description
   - App icon: `images/icon_512.png`
   - Feature graphic: `images/feature_graphic.png`
   - Phone screenshots: `images/screenshot_1.png` … `screenshot_8.png`
     (upload at least 2; 4–8 recommended)
   - Category: Photography
10. **Countries / regions** — pick where to release.
11. **App content → Permissions** — the console will flag `ACCESS_FINE_LOCATION`.
    The app uses **foreground** location only (no `ACCESS_BACKGROUND_LOCATION`),
    for the core "stamp the shot with its location" feature, so the standard
    declaration is enough. Describe: "Foreground location is used only while the
    camera screen is open, to write the location onto the photo or video the user
    captures."
12. **Release → Production** (or start with **Internal testing** / **Closed
    testing** first, recommended) → create release → upload the AAB → add release
    notes → review → roll out.

## Permissions the app declares (for the Console review)

| Permission | Why | Play sensitivity |
|---|---|---|
| `CAMERA` | Viewfinder + capture | Standard for a camera app |
| `RECORD_AUDIO` | Sound track of recorded video (optional) | Declared feature not required |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Stamp the shot with its location (foreground only) | Sensitive — foreground‑only, core feature |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Reverse‑geocode coordinates → address; fetch OSM map tile | Standard |
| `WRITE_EXTERNAL_STORAGE` (maxSdkVersion 28) | Save to shared storage on Android ≤ 9 | Standard, legacy only |

No background location, no `QUERY_ALL_PACKAGES`, no `MANAGE_EXTERNAL_STORAGE`, no
ad SDKs.

## First release notes (suggestion)

```
First release of Geo Pin Cam.
• GPS stamp on photos and videos: address, coordinates, altitude, accuracy, time
• Live stamp preview in the viewfinder
• Fully customizable fields, position and formats
• Works offline; no account, no ads, no uploads
```
