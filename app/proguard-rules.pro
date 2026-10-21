# Zenith tvOS Launcher ProGuard Rules
# Target: Ultra-minimal APK size (< 2.8MB) & zero runtime overhead

-repackageclasses 'org.zenith.tvos.internal'
-allowaccessmodification
-mergeinterfacesaggressively
-optimizations !code/simplification/arithmetic,!code/allocation/variable

# Keep Android TV Leanback Entry Points
-keep class org.zenith.tvos.ui.MainActivity { *; }

# Keep data models serialized/deserialized
-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# Remove all logging calls in release builds to eliminate string allocations
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
