# Keep line number and source file information for stack traces and de-obfuscation in Play Console
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# WebKit JavaScriptInterface
# Ensures @JavascriptInterface methods are not removed or renamed (e.g. VoxHostBridge)
-keepattributes JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# MapLibre GL Native & Models
-keep class org.maplibre.android.** { *; }
-keep interface org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**

# OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# JLayer (javazoom MP3 decoding reflection / class loading)
-keep class javazoom.jl.** { *; }
-dontwarn javazoom.jl.**

# Timber & Logging
-dontwarn com.jakewharton.timber.**
