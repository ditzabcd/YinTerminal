# Keep entry points & BuildConfig; internal logic (ui, data, core, di) remains obfuscated
-keep class com.yin.term.YinTermApp { *; }
-keep class com.yin.term.MainActivity { *; }
-keep class yin.term.BuildConfig { *; }
-keep class com.yin.term.BuildConfig { *; }

# Keep domain & data models for JSON/local storage serialization
-keep class com.yin.term.domain.model.** { *; }
-keep class com.yin.term.data.model.** { *; }

# Keep Chaquopy Python 3.13 JNI & platform bindings
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep libsu root shell reflection classes
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Keep AndroidX Startup, Emoji2, SplashScreen, and Coroutines internals
-keep class androidx.startup.** { *; }
-keep class androidx.emoji2.** { *; }
-keep class androidx.core.splashscreen.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Preserve annotations & Kotlin metadata for Compose
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }