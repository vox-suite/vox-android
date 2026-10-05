# Vox Android

Vox's Android app — talk to your agent, manage tasks, and work with your data, all from your phone. Kotlin, Jetpack Compose.

It's a thin client: SMS/location capture and the ambient UI live here, but the actual agent logic runs on [vox-core](https://github.com/vox-suite/vox-core), reached over `vox-core`'s HTTP API.

## Getting started

Open the project in Android Studio and run it on a device or emulator (`minSdk 26`). You'll need a `local.properties` with the backend URL and any client keys the app expects — see `app/build.gradle.kts` for what's read.
