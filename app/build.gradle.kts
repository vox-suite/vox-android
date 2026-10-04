import java.util.Properties

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

val webUiDir = rootProject.projectDir.resolve("web-ui")
val webUiGeneratedDir = layout.buildDirectory.dir("generated/webui")

val syncWebUi by tasks.registering {
    inputs.files(fileTree(webUiDir) {
        exclude("node_modules/**", "dist-webview/**", "release/**", "*.log")
    })
    outputs.dir(webUiGeneratedDir)
    doLast {
        fun run(vararg command: String) {
            val process = ProcessBuilder(*command)
                .directory(webUiDir)
                .inheritIO()
                .start()
            check(process.waitFor() == 0) { "${command.joinToString(" ")} failed in Android WebView sources" }
        }
        if (!webUiDir.resolve("node_modules").exists()) run("npm", "ci")
        run("npm", "run", "build:webview")
        val target = webUiGeneratedDir.get().dir("web").asFile
        target.deleteRecursively()
        target.mkdirs()
        webUiDir.resolve("dist-webview").copyRecursively(target, overwrite = true)
    }
}

tasks.configureEach {
    if (name != "syncWebUi" && (name.contains("Assets") || name.contains("lint", ignoreCase = true))) {
        dependsOn(syncWebUi)
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
        getByName("main").assets.directories.add(webUiGeneratedDir.get().asFile.absolutePath)
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
