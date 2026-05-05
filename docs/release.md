# Post HUB Release Checklist

Use this checklist before publishing a new build.

## Version

Update the release numbers in `app/build.gradle.kts`:

```kotlin
versionCode = 1
versionName = "1.0.0"
```

Increase `versionCode` for every Play Store upload.

## Signing

Create an upload keystore and keep it outside git. The build reads signing values from environment variables or a local `keystore.properties` file in the project root.

```properties
POSTHUB_STORE_FILE=C:\\path\\to\\posthub-upload.jks
POSTHUB_STORE_PASSWORD=your-store-password
POSTHUB_KEY_ALIAS=posthub
POSTHUB_KEY_PASSWORD=your-key-password
```

`keystore.properties`, `*.jks`, and `*.keystore` are ignored by git.

## Build

Run these commands from the project root:

```powershell
$env:GRADLE_USER_HOME='C:\Path\To\PostHUB\.gradle'
.\gradlew.bat :app:testDebugUnitTest :app:lintRelease :app:bundleRelease --console=plain
```

Upload the generated bundle:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Manual QA

Before upload, install a fresh build and verify:

- Single request Send works for GET and POST.
- Flow can capture a value, save it, and reuse it in another request.
- MCI, TCI, and IP client presets still populate requests correctly.
- Home-screen widget refresh runs the saved flow and shows the latest response.
- App launch shows the Post HUB splash and launcher icon on the emulator.

## Store Notes

Because Post HUB stores tokens and environment variables locally, Android backup is disabled in the manifest. Keep privacy copy clear that request data and saved variables stay on the device unless users send them through their own requests.
