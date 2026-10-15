# ─── Proguard Rules para Life Dashboard ────────────────────────────────────────

# Room Database
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.coroutines.** {
    volatile <fields>;
}

# Dagger Hilt
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class * extends dagger.hilt.internal.ComponentEntryPoint { *; }
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Health Connect & Jetpack Glance
-keep class androidx.health.connect.client.** { *; }
-keep class androidx.glance.** { *; }
-keep class com.lifedashboard.presentation.widget.** { *; }

# Google Generative AI (Gemini)
-keep class com.google.ai.client.generativeai.** { *; }
