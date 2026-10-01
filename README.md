# Vox Android

Vox's Android app — talk to your agent, manage tasks, and work with your data, all from your phone. Kotlin, Jetpack Compose.

It's a thin client: SMS/location capture and the ambient UI live here, but the actual agent logic runs on [vox-core](https://github.com/vox-suite/vox-core), reached over `vox-core`'s HTTP API.

## Getting started

Open the project in Android Studio and run it on a device or emulator (`minSdk 26`). You'll need a `local.properties` with the backend URL and any client keys the app expects — see `app/build.gradle.kts` for what's read.

## Shared web UI

Timeline (and later Pulse and Spaces) can be rendered from the shared [vox-ui](https://github.com/vox-suite/vox-ui) bundle inside a `WebView` instead of native Compose screens. Set `USE_WEB_SPANS=true` in `local.properties` to switch the Span tab to it; it defaults to `false`.

The Gradle task `syncVoxUi` puts the bundle in `assets/web`:

- If a `vox-ui` checkout sits next to this repo, it runs `npm run build:webview` there (Node required).
- Otherwise it downloads the release pinned by `voxUiVersion` and `voxUiSha256` in `gradle.properties` and verifies the checksum.
- Set `VOX_UI_MODE=release` or `VOX_UI_MODE=local` in `local.properties` to force one of the two.

The page gets its session from `window.VoxHost.getSession()` (`web/VoxHostBridge.kt`), which mints a 15-minute web token from the native session. Assets load from `https://appassets.androidplatform.net` with a Content-Security-Policy limited to the app origin and the API.
