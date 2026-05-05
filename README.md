# Post HUB

Post HUB is an Android request client built with Kotlin and Jetpack Compose. It is designed as a mobile-first Postman-style tool for building requests, running multi-step flows, sharing values between requests, and checking results from the phone home screen.

## Features

- Compose-based Android UI with a clean, sectioned request workspace.
- HTTP request composer with method, URL, query params, headers, auth, body, and save controls.
- Response viewer for status, headers, body, timing, size, and image responses.
- Request history and saved requests.
- Environment variables with `{{variable_name}}` substitution.
- Multi-step flows that can capture values from one response and reuse them in later requests.
- Persistent captured values, useful for tokens and session values.
- Built-in client presets for MCI, TCI, and public IP lookup/IP information.
- Home-screen widget with a refresh button that runs the saved flow and displays the latest response.
- Branded launcher icon, monochrome themed icon, and Android splash screen.
- Release build setup with resource shrinking, minification, lint checks, and optional local signing.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android App Widgets
- Gradle Kotlin DSL
- Minimum SDK: 24
- Target SDK: 36

## Project Structure

```text
app/src/main/java/net/rodakot/posthub/
  MainActivity.kt             Main Compose app and request/flow UI
  FlowWidgetProvider.kt       Home-screen widget flow runner
  ui/theme/                   Compose theme, colors, typography

app/src/main/res/
  drawable/                   Icons, splash, widget backgrounds
  layout/widget_flow.xml      Widget RemoteViews layout
  xml/flow_widget_info.xml    App widget provider metadata
  values/                     Strings, colors, Android themes

docs/release.md               Publishing checklist
```

## Requirements

- Android Studio
- Android SDK with API 36 installed
- JDK compatible with the Android Gradle Plugin
- Android emulator or device for manual testing

## Build

From the repository root:

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Test and Lint

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintRelease --console=plain
```

## Release Bundle

```powershell
.\gradlew.bat :app:bundleRelease --console=plain
```

The release bundle is generated at:

```text
app/build/outputs/bundle/release/app-release.aab
```

## Release Signing

Release signing is configured through environment variables or a local `keystore.properties` file in the repository root.

```properties
POSTHUB_STORE_FILE=C:\\path\\to\\posthub-upload.jks
POSTHUB_STORE_PASSWORD=your-store-password
POSTHUB_KEY_ALIAS=posthub
POSTHUB_KEY_PASSWORD=your-key-password
```

Signing files and `keystore.properties` are ignored by git. See [docs/release.md](docs/release.md) for the full publishing checklist.

## Widget

After installing the app, add the Post HUB widget from the Android home-screen widget picker. The widget refresh button runs the saved flow and shows the latest response summary. If no flow has been saved yet, the widget can still use the built-in IP lookup flow.

## Privacy and Local Data

Post HUB stores saved requests, flows, environment variables, and captured values locally on the device. Android backup is disabled because these values can include tokens or private request data.

## Package

```text
net.rodakot.posthub
```
