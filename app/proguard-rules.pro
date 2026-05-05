# ProGuard / R8 rules for VoluMax

# ============================================================
# Keep AudioEffect subclasses — R8 would otherwise strip them
# ============================================================
-keep class android.media.audiofx.** { *; }
-keep class android.media.AudioManager { *; }

# ============================================================
# Protobuf Lite
# ============================================================
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}
-keep class com.google.protobuf.** { *; }

# ============================================================
# Hilt
# ============================================================
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# ============================================================
# Kotlin coroutines
# ============================================================
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ============================================================
# Suppress warnings for unused platform code
# ============================================================
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**
