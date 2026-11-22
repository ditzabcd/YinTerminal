# Entry points & BuildConfig (internal packages ui, data, core, di remain obfuscated)
-keep class com.yin.term.YinTermApp { *; }
-keep class com.yin.term.MainActivity { *; }
-keep class yin.term.BuildConfig { *; }
-keep class com.yin.term.BuildConfig { *; }

# Keep domain & data models for JSON serialization
-keep class com.yin.term.domain.model.** { *; }
-keep class com.yin.term.data.model.** { *; }

# Keep ViewModels instantiated via ViewModelProvider reflection
-keep class * extends androidx.lifecycle.ViewModel {
    public <init>(...);
}

# Keep Chaquopy Python 3.13 JNI & platform bindings
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep libsu root shell reflection classes
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Keep Kotlin Coroutines MainDispatcher & ServiceLoader (prevents startup crash)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class kotlinx.coroutines.android.** { *; }
-dontwarn kotlinx.coroutines.**

# Keep AndroidX Startup, Emoji2, SplashScreen, and Navigation
-keep class androidx.startup.** { *; }
-keep class androidx.emoji2.** { *; }
-keep class androidx.core.splashscreen.** { *; }
-keep class androidx.navigation.** { *; }

# Preserve essential attributes for Compose & Kotlin metadata
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**