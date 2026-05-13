# ProGuard / R8 rules for VoluMax

# ============================================================
# Keep AudioEffect subclasses — R8 would otherwise strip them
# ============================================================
-keep class android.media.audiofx.** { *; }
-keep class android.media.AudioManager { *; }

# (Protobuf rules removed — this project uses DataStore Preferences, not Proto DataStore)

# ============================================================
# Hilt
# ============================================================
# Hilt ViewModels - Ensure they and their constructors are kept
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * {
    <init>(...);
}

# Preserve Hilt generated classes
-keep class * implements dagger.hilt.internal.GeneratedComponent { *; }
-keep class * implements dagger.hilt.internal.GeneratedComponentManager { *; }
-keep class * implements dagger.hilt.internal.UnsafeCasts { *; }

-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }

# ============================================================
# Compose
# ============================================================
-keep class androidx.compose.ui.platform.AndroidComposeView { *; }
-keep class androidx.compose.runtime.Recomposer { *; }
-keep class androidx.compose.ui.platform.AndroidCompositionLocals_androidKt { *; }
-keep class androidx.lifecycle.ViewTreeLifecycleOwner { *; }
-keep class androidx.lifecycle.ViewTreeLifecycleOwnerKt { *; }
-keep class androidx.lifecycle.ViewTreeViewModelStoreOwner { *; }
-keep class androidx.lifecycle.ViewTreeViewModelStoreOwnerKt { *; }
-keep class androidx.savedstate.ViewTreeSavedStateRegistryOwner { *; }
-keep class androidx.savedstate.ViewTreeSavedStateRegistryOwnerKt { *; }

# ============================================================
# DataStore
# ============================================================
-keep class androidx.datastore.** { *; }

# ============================================================
# Kotlin coroutines
# ============================================================
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ============================================================
# Suppress warnings for unused platform code
# ============================================================
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.**
