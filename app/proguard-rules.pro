# VoltageAlert ProGuard Rules

# Keep Room entities
-keep class com.voltagealert.logging.VoltageLogEntity { *; }

# Keep data classes
-keep class com.voltagealert.models.** { *; }

# Keep BLE callbacks
-keep class * extends no.nordicsemi.android.ble.callback.** { *; }

# Keep Nordic BLE library
-keep class no.nordicsemi.android.ble.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# General Android
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
