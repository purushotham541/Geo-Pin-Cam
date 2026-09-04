# Claude Code Instructions — Geo Pin Cam Android App

**Final App Name: Geo Pin Cam**

Use **Geo Pin Cam** consistently throughout:
- Android application label
- UI branding
- stamp/watermark
- project documentation
- user-facing messages
- Play Store-facing copy
- package/project references where applicable

You are an expert Android developer. Build a production-ready Android application called **Geo Pin Cam**.

The app should allow users to take photos with the phone camera and automatically add a professional GPS/location stamp, date/time, and other configurable information directly onto the captured image.

---

## 1. DEVELOPMENT REQUIREMENTS

Build the application using:

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Architecture:** MVVM / Clean Architecture where appropriate
- **Camera:** CameraX
- **Location:** Android Location Services / Fused Location Provider
- **Minimum SDK:** 26
- **Target SDK:** Latest stable Android SDK available in the environment
- **Build System:** Gradle Kotlin DSL
- **Dependency Management:** Version Catalog (`libs.versions.toml`)
- **Asynchronous operations:** Kotlin Coroutines + Flow
- **State Management:** ViewModel + StateFlow
- **Image Processing:** Android Bitmap APIs / appropriate Android libraries
- **Maps:** Google Maps only if a map preview is required
- **Permissions:** Follow modern Android permission requirements
- **Theme:** Material 3

Do not use deprecated Android APIs unless absolutely necessary.

---

# 2. FIRST STEP — ANALYZE BEFORE CODING

Before creating implementation files:

1. Inspect the existing project structure.
2. Identify the Android Gradle Plugin version.
3. Identify the Kotlin version.
4. Identify the compile SDK and target SDK.
5. Check existing dependencies.
6. Check whether the project already contains an application module.
7. Reuse existing project configuration where possible.
8. Do not unnecessarily replace working configuration.
9. Identify potential compatibility issues before implementation.

Then create a short implementation plan.

Do not start by generating all files blindly.

---

# 3. APP CONCEPT

The main purpose of the app is:

**Take a photograph → obtain current GPS location → create a location stamp → overlay the information on the photograph → save/share the final image.**

Example stamped photograph:

---

\| |
\| PHOTO |
\| |
\| |
\| |

|                                |
| ------------------------------ |
| 📍 Hyderabad, Telangana, India |
| 17.3850° N, 78.4867° E         |
| Altitude: 520 m                |
| Accuracy: ±5 m                 |
| 31 Aug 2026 • 02:30 PM         |
| Geo Pin Cam                  |

---

The design must look like a modern professional camera application rather than a basic demo.

---

# 4. MAIN FEATURES

Implement the following features.

## A. Camera Screen

The camera screen should contain:

- Full-screen camera preview
- Camera shutter button
- Flash control
- Front/rear camera switch
- GPS/location status
- Current date/time
- Settings button
- Gallery/photo preview button

The camera preview should use the correct aspect ratio.

The shutter button should be large and easy to press.

---

# 5. LOCATION INFORMATION

When location permission is granted, obtain:

- Latitude
- Longitude
- Accuracy
- Altitude where available
- Bearing/direction where available
- Speed where available
- Country
- State
- City
- Locality/address

Use reverse geocoding to convert coordinates into a human-readable address.

Example:

**📍 Hyderabad, Telangana, India**

Coordinates:

**17.3850° N, 78.4867° E**

Accuracy:

**±5 meters**

Do not continuously perform expensive reverse-geocoding operations unnecessarily.

Cache location/address information where appropriate.

---

# 6. LOCATION PERMISSION

Handle permissions correctly.

Request:

- ACCESS\_FINE\_LOCATION
- ACCESS\_COARSE\_LOCATION

Only request permissions when the feature actually requires them.

Clearly explain to the user why location permission is required.

Handle:

- Permission granted
- Permission denied
- Permission permanently denied
- Location services disabled
- Location unavailable
- GPS signal weak
- Approximate location instead of precise location

The app must never crash when permission is denied.

---

# 7. CAMERA PERMISSION

Request:

- CAMERA

Handle:

- Permission granted
- Permission denied
- Permission permanently denied

If permission is unavailable, display a useful explanation and provide a way to retry.

Never crash because of missing permissions.

---

# 8. PHOTO CAPTURE

When the user presses the shutter:

1. Capture the photograph using CameraX.
2. Obtain the latest available location.
3. Obtain current date/time.
4. Obtain address information.
5. Generate the GPS stamp.
6. Overlay the stamp onto the captured photograph.
7. Save the final image.
8. Display the resulting image.
9. Allow the user to share it.

Do not block the UI unnecessarily while processing the image.

Show a progress indicator when image processing takes time.

---

# 9. GPS STAMP DESIGN

Create a configurable GPS stamp overlay.

The stamp should support:

### Location

Example:

📍 Hyderabad, Telangana

### Coordinates

Example:

17.3850° N, 78.4867° E

### Date and Time

Example:

31 Aug 2026 • 02:30 PM

### Accuracy

Example:

Accuracy: ±5 m

### Altitude

Example:

Altitude: 520 m

### App Name

Example:

GPS Camera

The user should be able to choose which fields appear.

---

# 10. STAMP POSITION

Allow the user to configure stamp position:

- Bottom Left
- Bottom Center
- Bottom Right
- Top Left
- Top Center
- Top Right

Use a semi-transparent background behind the text so that the information remains readable.

The overlay must look professional.

Avoid excessive text.

Use proper spacing, typography, icons and alignment.

---

# 11. STAMP CUSTOMIZATION

Create a Settings screen where users can configure:

### Information

Enable/disable:

- Address
- Latitude/Longitude
- Date
- Time
- Accuracy
- Altitude
- Speed
- Direction
- App name

### Appearance

Allow configuration of:

- Stamp position
- Font size
- Text alignment
- Background transparency
- Date format
- Time format
- Coordinate format

Coordinate formats:

- Decimal Degrees
- Degrees Minutes Seconds

Example:

Decimal:

17.3850° N, 78.4867° E

DMS:

17°23'06"N, 78°29'12"E

---

# 12. PHOTO QUALITY

Provide settings for:

- High quality
- Medium quality
- Low quality

Do not unnecessarily reduce the original camera resolution.

The final stamped image should retain good visual quality.

Use appropriate JPEG/HEIF encoding depending on the platform and configuration.

---

# 13. GALLERY

Create a Gallery screen showing photographs captured by the application.

Features:

- Grid layout
- Thumbnail preview
- Full-screen image viewer
- Delete photo
- Share photo
- Open photo
- Date captured

Use LazyVerticalGrid or an equivalent efficient Compose component.

Do not load full-resolution images into memory for thumbnails.

Use thumbnail/downsampling techniques.

---

# 14. PHOTO DETAIL SCREEN

When the user opens a photograph:

Display:

- Full image
- Date/time
- Location
- Coordinates
- Accuracy
- Altitude if available

Actions:

- Share
- Delete
- Save/export

---

# 15. SHARE FEATURE

Allow the user to share photographs using Android's standard share mechanism.

Use:

- FileProvider
- content:// URI
- Intent.ACTION\_SEND

Never expose file:// URIs.

Handle sharing failures gracefully.

---

# 16. STORAGE

Use modern Android storage APIs.

Do not request broad storage permissions unnecessarily.

Save photographs into an appropriate media collection such as:

**Pictures/Geo Pin Cam**

Use MediaStore where appropriate.

Make captured images visible in the device gallery.

Handle Android scoped storage correctly.

---

# 17. SETTINGS

Create a professional Settings screen.

Sections:

### Camera

- Photo quality
- Flash preference
- Default camera

### GPS Stamp

- Enable GPS stamp
- Position
- Font size
- Transparency
- Fields to display

### Location

- Coordinate format
- Address display

### Date & Time

- Date format
- Time format
- 12/24-hour format

### Storage

- Save location
- Save original photo
- Save stamped photo

### About

- App name
- Version
- Privacy information
- Open-source licenses if applicable

---

# 18. CAMERA UX

The camera screen should feel similar to a modern camera app.

Suggested layout:

Top:

[Location Status] [Flash] [Switch Camera]

Center:

Camera Preview

Bottom:

[Gallery] [ SHUTTER ] [Settings]

GPS information can appear as a small preview stamp on top of the camera preview.

The user should be able to see approximately how the final stamp will look before capturing.

---

# 19. LIVE GPS PREVIEW

Show the current location information on the camera screen.

Example:

📍 Hyderabad, Telangana
17.3850° N, 78.4867° E
±5 m

If GPS is unavailable:

📍 Waiting for GPS...

If permission is denied:

📍 Location permission required

If accuracy is poor:

📍 GPS accuracy: ±85 m

Use clear visual status indicators.

---

# 20. OFFLINE BEHAVIOR

The camera should continue working even if internet connectivity is unavailable.

Important:

GPS coordinates do not require internet.

Reverse geocoding may require network connectivity.

Therefore:

- Capture coordinates even without internet.
- If address cannot be obtained, show coordinates.
- Do not prevent photo capture because address lookup failed.
- Clearly handle offline mode.

Example:

📍 17.3850° N, 78.4867° E
Address unavailable

---

# 21. LOCATION ACCURACY

Do not display fake or estimated accuracy values.

Only display accuracy supplied by Android location APIs.

If location is stale, indicate that the location is not current.

Implement sensible location freshness checks.

Example:

**GPS: ±6 m**

rather than inventing a value.

---

# 22. IMAGE PROCESSING

Create a dedicated image-stamping component.

For example:

`PhotoStampProcessor`

Responsibilities:

- Receive captured Bitmap/file
- Receive GPS information
- Receive timestamp
- Receive stamp configuration
- Render overlay
- Save final image

Keep image-processing logic outside Activities/Composables.

Use efficient Bitmap handling.

Avoid unnecessary Bitmap copies.

Prevent OutOfMemoryError when processing high-resolution images.

---

# 23. ARCHITECTURE

Use a clean structure similar to:

app/
├── data/
│ ├── location/
│ ├── camera/
│ ├── storage/
│ └── preferences/
│
├── domain/
│ ├── model/
│ ├── repository/
│ └── usecase/
│
├── ui/
│ ├── camera/
│ ├── gallery/
│ ├── photo/
│ ├── settings/
│ └── components/
│
├── utils/
│
└── MainActivity.kt

Do not force excessive abstraction.

Keep the architecture understandable and maintainable.

---

# 24. DATA MODELS

Create appropriate models such as:

`LocationData`

Containing:

- latitude
- longitude
- accuracy
- altitude
- speed
- bearing
- timestamp
- address
- city
- state
- country

Create:

`StampConfiguration`

Containing:

- showAddress
- showCoordinates
- showDate
- showTime
- showAccuracy
- showAltitude
- showSpeed
- showBearing
- position
- fontSize
- transparency
- coordinateFormat

Create additional models where necessary.

---

# 25. PERSISTENT SETTINGS

Use DataStore Preferences rather than SharedPreferences for new code.

Persist:

- Stamp configuration
- Camera preferences
- Photo quality
- Coordinate format
- Date/time format
- Last selected camera

Settings must survive application restart.

---

# 26. ERROR HANDLING

Implement robust error handling.

Possible errors:

- Camera unavailable
- Camera permission denied
- Location permission denied
- GPS disabled
- Location unavailable
- Reverse geocoding failed
- Image capture failed
- Image processing failed
- Storage failure
- Sharing failure

Never allow exceptions to crash the application unnecessarily.

Display user-friendly messages.

Do not expose raw stack traces to users.

---

# 27. UI STATE

Use explicit UI states.

For example:

Camera:

- Initializing
- Ready
- Capturing
- Processing
- Error

Location:

- PermissionRequired
- Searching
- Available
- Unavailable
- Disabled

Use StateFlow in ViewModels where appropriate.

Avoid mutable global state.

---

# 28. DARK MODE

Support:

- Light theme
- Dark theme
- System default

Use Material 3 dynamic theming where appropriate.

Ensure text remains readable over camera preview.

---

# 29. ACCESSIBILITY

Implement:

- Content descriptions
- Minimum touch target sizes
- Accessible buttons
- Good color contrast
- Screen-reader-friendly labels

Do not rely only on icons.

---

# 30. ROTATION / ORIENTATION

Handle portrait and landscape orientation correctly.

The GPS stamp should remain correctly oriented relative to the final photograph.

Camera preview and captured image orientation must be handled correctly.

Test:

- Portrait
- Landscape
- Front camera
- Rear camera

---

# 31. FRONT CAMERA

When using the front camera:

- Handle mirrored preview appropriately.
- Ensure the saved image orientation is correct.
- GPS stamp should not be unintentionally mirrored.

---

# 32. SECURITY AND PRIVACY

Follow privacy-first principles.

The app should:

- Request only necessary permissions.
- Never transmit location data to a server unless explicitly required.
- Never upload photographs automatically.
- Never collect unnecessary personal data.
- Clearly explain location usage.

Keep location/photo processing local whenever possible.

---

# 33. PRIVACY SCREEN

Create a simple Privacy section explaining:

- Why camera permission is required.
- Why location permission is required.
- How photos are stored.
- Whether location data leaves the device.
- Whether analytics are used.

Do not claim that data is never collected unless the implementation actually guarantees it.

---

# 34. TESTING

Create tests for:

### Unit tests

- Coordinate formatting
- Date formatting
- Stamp configuration
- Location model
- Stamp text generation

### Instrumentation/UI tests

Test:

- Permission handling
- Camera screen
- Settings
- Gallery
- Navigation

Test important edge cases.

---

# 35. BUILD VALIDATION

After implementation:

1. Run Gradle sync/build.
2. Fix compilation errors.
3. Run unit tests.
4. Run lint if available.
5. Fix warnings that could cause runtime problems.
6. Verify manifest permissions.
7. Verify ProGuard/R8 configuration if applicable.
8. Verify release build configuration.

Do not stop after writing code.

The project must compile successfully.

---

# 36. CODE QUALITY

Follow these rules:

- Kotlin idioms
- Null safety
- No unnecessary `!!`
- No hard-coded strings in UI
- Use string resources
- Use dimension resources where appropriate
- Avoid magic numbers
- Avoid duplicated code
- Meaningful class/function names
- Small focused functions
- Proper comments only where useful
- No TODO placeholders for core functionality
- No fake implementations
- No mock GPS coordinates in production code

---

# 37. IMPORTANT — DO NOT FAKE FUNCTIONALITY

Do NOT create buttons that only display Toast messages.

Every major feature must actually work.

Do not use:

- Fake camera preview
- Fake GPS coordinates
- Fake gallery
- Fake photo saving
- Fake sharing
- Placeholder settings

Implement real Android functionality.

---

# 38. USER EXPERIENCE

The final application should feel like a real commercial Android application.

Prioritize:

- Simple navigation
- Fast camera startup
- Large shutter button
- Clear GPS status
- Professional stamp design
- Minimal clutter
- Smooth animations
- Good error handling

Avoid making the interface overly complicated.

---

# 39. SUGGESTED NAVIGATION

Use:

Camera
↓
Capture
↓
Photo Preview
├── Save
├── Share
├── Delete
└── Retake

Bottom navigation can contain:

📷 Camera
🖼 Gallery
⚙ Settings

Use Material 3 navigation components.

---

# 40. IMPLEMENTATION ORDER

Implement in this order:

### Phase 1

Project analysis and architecture.

### Phase 2

CameraX camera preview.

### Phase 3

Camera permission handling.

### Phase 4

Location services.

### Phase 5

Location permission handling.

### Phase 6

Live GPS information.

### Phase 7

Photo capture.

### Phase 8

GPS stamp rendering.

### Phase 9

Photo saving using MediaStore.

### Phase 10

Photo preview.

### Phase 11

Gallery.

### Phase 12

Share functionality.

### Phase 13

Settings/DataStore.

### Phase 14

Dark mode and accessibility.

### Phase 15

Testing and error handling.

### Phase 16

Build validation and final cleanup.

After completing each phase, verify that the project still builds.

---

# 41. CLAUDE CODE WORKFLOW

Follow this workflow strictly:

1. Inspect the repository.
2. Explain what you found.
3. Create an implementation plan.
4. Implement one phase at a time.
5. Build after major changes.
6. Fix errors immediately.
7. Continue to the next phase.
8. Do not overwrite unrelated existing code.
9. Reuse existing components where appropriate.
10. At the end, provide a concise summary of:

- Files created
- Files modified
- Features implemented
- Build status
- Tests performed
- Any remaining limitations

---

# 42. IMPORTANT ANDROID COMPATIBILITY

Pay special attention to modern Android versions.

Correctly handle:

- Runtime permissions
- Android scoped storage
- MediaStore
- FileProvider
- Activity lifecycle
- CameraX lifecycle
- Location lifecycle
- Background restrictions
- Android 13+ media behavior
- Android 14+ permission behavior
- Android 15+ behavior
- Latest stable Android behavior available during development

Do not assume old Android behavior.

---

# 43. FINAL ACCEPTANCE CRITERIA

The application is considered complete only when:

[ ] App builds successfully
[ ] Camera opens successfully
[ ] Rear camera works
[ ] Front camera works
[ ] Flash works where supported
[ ] Camera permission works
[ ] Location permission works
[ ] GPS coordinates are real
[ ] Address lookup works when available
[ ] Offline GPS capture works
[ ] Photo capture works
[ ] GPS stamp appears on photo
[ ] Stamp fields are configurable
[ ] Photo is saved to device gallery
[ ] Gallery displays captured photos
[ ] Photo detail works
[ ] Delete works
[ ] Share works
[ ] Settings persist after restart
[ ] Portrait works
[ ] Landscape works
[ ] Dark mode works
[ ] Permission denial does not crash app
[ ] GPS unavailable does not crash app
[ ] Network unavailable does not prevent photo capture
[ ] High-resolution photos do not cause avoidable memory crashes
[ ] Unit tests pass
[ ] Release build succeeds

---

# 44. START NOW

Start by inspecting the existing project.

Do NOT immediately generate all application files.

First report:

1. Current project structure
2. Android/Kotlin/Gradle versions
3. Existing dependencies
4. Existing application architecture
5. What can be reused
6. Potential issues
7. Proposed implementation plan

Then begin Phase 1.

After each major phase, build and verify the application before proceeding.