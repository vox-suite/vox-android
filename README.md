# Vox Android

Vox's Android app — talk to your agent, manage tasks, and work with your data, all from your phone. Kotlin, Jetpack Compose.

It's a thin client: SMS/location capture and the ambient UI live here, but the actual agent logic runs on [vox-core](https://github.com/vox-suite/vox-core), reached over `vox-core`'s HTTP API.

## Getting started

Open the project in Android Studio and run it on a device or emulator (`minSdk 26`). You'll need a `local.properties` with the backend URL and any client keys the app expects — see `app/build.gradle.kts` for what's read.

## WebView screens

Timeline, Connected Apps, Pulse and Spaces live in this repository's `web-ui` folder and run in the bundled WebView. Desktop owns its own UI sources.

The Gradle task `syncWebUi` builds these sources with Node.js and packages them into `assets/web`. A clean build requires Node.js/npm; dependencies install from `web-ui/package-lock.json`. No sibling UI checkout or remote UI release is used.

The page gets its session from `window.VoxHost.getSession()` (`web/VoxHostBridge.kt`), which mints a 15-minute web token from the native session. Assets load from `https://appassets.androidplatform.net` with a Content-Security-Policy limited to the app origin and the API.
