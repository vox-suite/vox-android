import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val voxUiVersion = providers.gradleProperty("voxUiVersion").get()
val voxUiSha256 = providers.gradleProperty("voxUiSha256").get()
val voxUiSiblingDir = rootProject.projectDir.resolve("../vox-ui")
val voxUiMode = localProperties.getProperty("VOX_UI_MODE", "auto")
val useLocalVoxUi = voxUiMode == "local" ||
    (voxUiMode == "auto" && voxUiSiblingDir.resolve("package.json").exists())
val voxUiGeneratedDir = layout.buildDirectory.dir("generated/voxui")

val syncVoxUi by tasks.registering {
    val webDir = voxUiGeneratedDir.map { it.dir("web").asFile }
    val marker = voxUiGeneratedDir.map { it.file("web.version").asFile }
    val wantedMarker = "$voxUiVersion:$voxUiSha256"
    inputs.property("voxUiMode", if (useLocalVoxUi) "local" else "release")
    inputs.property("voxUiVersion", voxUiVersion)
    outputs.dir(voxUiGeneratedDir)
    outputs.upToDateWhen {
        !useLocalVoxUi && marker.get().exists() && marker.get().readText() == wantedMarker
    }
    doLast {
        val target = webDir.get()
        target.deleteRecursively()
        target.mkdirs()
        if (useLocalVoxUi) {
            fun run(vararg command: String) {
                val process = ProcessBuilder(*command)
                    .directory(voxUiSiblingDir)
                    .inheritIO()
                    .start()
                check(process.waitFor() == 0) { "${command.joinToString(" ")} failed in vox-ui" }
            }
            if (!voxUiSiblingDir.resolve("node_modules").exists()) run("npm", "ci")
            run("npm", "run", "build:webview")
            voxUiSiblingDir.resolve("dist-webview").copyRecursively(target, overwrite = true)
            marker.get().delete()
        } else {
            val url = "https://github.com/vox-suite/vox-ui/releases/download/v$voxUiVersion/" +
                "vox-ui-webview-$voxUiVersion.zip"
            val archive = temporaryDir.resolve("vox-ui-webview.zip")
            URI(url).toURL().openStream().use { input ->
                archive.outputStream().use { input.copyTo(it) }
            }
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(archive.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(digest == voxUiSha256) { "vox-ui bundle checksum mismatch: $digest" }
            ZipInputStream(archive.inputStream()).use { zip ->
                generateSequence { zip.nextEntry }.forEach { entry ->
                    val out = target.resolve(entry.name).canonicalFile
                    check(out.path.startsWith(target.canonicalPath)) { "unsafe zip entry" }
                    if (entry.isDirectory) out.mkdirs() else {
                        out.parentFile.mkdirs()
                        out.outputStream().use { zip.copyTo(it) }
                    }
                }
            }
            marker.get().writeText(wantedMarker)
        }
    }
}

tasks.configureEach {
    if (name != "syncVoxUi" && (name.contains("Assets") || name.contains("lint", ignoreCase = true))) {
        dependsOn(syncVoxUi)
    }
}

android {
    namespace = "in.voxagent.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "in.voxagent.mobile"
        minSdk = 26
        targetSdk = 37
        versionCode = 17
        versionName = "0.1.48"

        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"${localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"",
        )
        buildConfigField(
            "String",
            "VOX_API_BASE_URL",
            "\"${localProperties.getProperty("VOX_API_BASE_URL", "https://api.voxagent.in")}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    sourceSets {
        getByName("main").assets.directories.add(voxUiGeneratedDir.get().asFile.absolutePath)
    }

    lint {
        disable += listOf("NewerVersionAvailable", "AndroidGradlePluginVersion")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.webkit:webkit:1.17.1")
    implementation("com.jakewharton.timber:timber:5.0.1")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")

    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")

    implementation("org.maplibre.gl:android-sdk:13.6.1")
    implementation("com.google.android.gms:play-services-location:21.4.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("javazoom:jlayer:1.0.1")
    //noinspection GradleDependency
    implementation("dev.chrisbanes.haze:haze:1.5.3")
}
