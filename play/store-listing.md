# Google Play — store listing content for Geo Pin Cam

Copy‑paste these into the Play Console. Adjust wording to taste.

---

## App details

| Field | Value |
|---|---|
| App name | **Geo Pin Cam** (30 char max) |
| Default language | English (United States) |
| App or game | App |
| Category | Photography |
| Tags | GPS camera, geotag, location stamp, timestamp camera |
| Contact email | _your email_ |
| Website (optional) | _your site, or leave blank_ |
| Privacy policy URL | `https://purushotham541.github.io/Geo-Pin-Cam/` |

## Short description (80 characters max)

```
Stamp GPS location, address, altitude & time onto every photo and video.
```

## Full description (4000 characters max)

```
Geo Pin Cam turns your phone into a GPS camera. Every photo — and now every
video — is stamped with where and when it was taken, right on the image.

WHAT GETS STAMPED
• Place headline: city, state and country, with the country flag
• Exact place: building or business name, street and postcode
• Latitude and longitude, in decimal, DMS or a labelled format
• Altitude and GPS accuracy
• Speed and heading (optional)
• Date and time, with the time zone
• A small map thumbnail marking the spot (optional)

BUILT FOR REAL WORK
• Live preview — see exactly what the stamp will look like before you shoot
• The stamp is burned into the saved file, so it travels with the photo or video
• Fast, silent capture — the shutter is ready again immediately
• Full‑resolution photos; 4K, 1080p or 720p video
• Front and rear camera, flash and torch

MAKE IT YOURS
• Turn each field on or off
• Six stamp positions, three text sizes, adjustable background
• Choose your date format, time format and coordinate format
• Optionally keep an unstamped original alongside the stamped copy

WORKS OFFLINE
GPS coordinates never need the internet, so your shots are stamped even with no
signal. When a connection is available, coordinates are turned into a street
address and a map tile — nothing else is ever sent.

PRIVATE BY DESIGN
No account. No servers. No analytics. No ads. Your photos and videos are saved
only to your device and are never uploaded. Only your coordinates (never the
image) are sent, and only for the optional address and map‑tile features, which
you can switch off.

Perfect for field reports, site inspections, insurance and damage evidence,
construction progress, real‑estate, surveying, travel logs and research.
```

---

## Graphics (in `E:\3. Android App developmment\Geo Pin Cam\images`)

| Asset | File | Spec |
|---|---|---|
| App icon (hi‑res) | `icon_512.png` | 512×512, 32‑bit PNG |
| Feature graphic | `feature_graphic.png` | 1024×500, PNG |
| Phone screenshots | `screenshot_1.png` … `screenshot_8.png` | 1080×1920, PNG, 2–8 required |

---

## Data safety form answers

**Does your app collect or share any of the required user data types?**
→ The app processes data on the device and sends only coordinates to third
parties for optional features. Answer as follows:

| Data type | Collected | Shared | Purpose | Notes |
|---|---|---|---|---|
| Location — Approximate location | Yes | Yes | App functionality | Sent to Google (geocoder) and OpenStreetMap (map tiles) only as coordinates, only for the optional address / map features. Not stored by the app off‑device. |
| Location — Precise location | Yes | Yes | App functionality | Same as above. Written into the photo/video the user creates on their device. |
| Photos and videos | Yes | No | App functionality | Created and stored on the device only. Never uploaded. |
| Audio — Voice or sound recordings | Yes | No | App functionality | Only the audio track of a video the user records. On device only. |

- **Is all of the user data encrypted in transit?** Yes (the geocoder and tile
  requests use HTTPS).
- **Do you provide a way for users to request that their data is deleted?** Data
  is only on the device; uninstalling removes app data, and users delete their
  own photos/videos. 
- **Data is not required to use the app:** location can be denied (photos are
  still captured, just without a location stamp).

## Content rating

Run the questionnaire — the app has no violence, no user‑generated content
sharing, no ads. Expected rating: **Everyone / PEGI 3**.

## Ads

Contains ads: **No**.

## In‑app purchases

**No**.

## Target audience

13+ (not designed for or directed at children).
