# Keep BroadcastReceivers, which are entry points from the Android system.
-keep public class * extends android.content.BroadcastReceiver
-keep class com.comicrack.reader.WifiBootCompletedReceiver { *; }
-keep class com.comicrack.reader.WifiSyncService { *; }

# Keep R classes and their inner classes. Their names and fields are accessed by Android.
# While modern AGP handles this better, we keep these for maximum safety.
-keepclasseswithmembernames class **.R {
    <fields>;
}
-keepclasseswithmembernames class **.R$* {
    <fields>;
}

# Keep native methods, which are entry points from native code.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Allow R8 to perform optimizations across class/package boundaries.
-allowaccessmodification

# Keep coroutine-specific classes that are accessed via reflection.
-keepnames class kotlinx.coroutines.flow.internal.FlowExceptions_commonKt
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler

# R8 missing classes
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.tukaani.xz.**

# R8 reporting: write usage and seeds so we can detect removed classes during a debug-minified build
# Usage file lists classes/members removed by shrinking
-printusage build/outputs/mapping/debug/usage.txt
# Seeds file lists entry points kept by the shrinker
-printseeds build/outputs/mapping/debug/seeds.txt
