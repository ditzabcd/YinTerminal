# Aggressive package flattening & class/method obfuscation for app logic
-repackageclasses 'o'
-allowaccessmodification
-optimizationpasses 5

# Keep only entry points & domain models; allow ui, data, core, and di to be obfuscated
-keep class com.yin.term.YinTermApp { *; }
-keep class com.yin.term.MainActivity { *; }
-keep class com.yin.term.domain.model.** { *; }

# Keep Chaquopy Python 3.13 JNI & platform bindings
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# Keep libsu root shell reflection classes
-keep class com.topjohnwu.superuser.** { *; }
-dontwarn com.topjohnwu.superuser.**

# Keep AndroidX Startup, Emoji2, and SplashScreen initializers
-keep class androidx.startup.** { *; }
-keep class androidx.emoji2.** { *; }
-keep class androidx.core.splashscreen.** { *; }

# Preserve essential annotations & Kotlin metadata for Compose/Coroutines
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations
-keep class kotlin.Metadata { *; }