# Keep obfuscation enabled for internal classes/methods while preventing R8 from deleting JNI/reflection targets
-dontshrink
-dontoptimize

# Entry points & BuildConfig
-keep class com.yin.term.YinTermApp { *; }
-keep class com.yin.term.MainActivity { *; }
-keep class yin.term.BuildConfig { *; }
-keep class com.yin.term.BuildConfig { *; }
-keep class yin.term.R$* { *; }

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

# Keep Kotlin Coroutines & AndroidX runtime components
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-keep class androidx.** { *; }
-dontwarn androidx.**

# Preserve essential attributes for Compose & Kotlin metadata
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,Exceptions
-keep class kotlin.** { *; }
-dontwarn kotlin.**