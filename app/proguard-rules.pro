# CareOS Release ProGuard / R8 Rules

# Keep Room entities and database implementations
-keep class androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public *;
}
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Keep Retrofit and Moshi API models
-keepclassmembers class com.example.data.** { *; }
-keepclassmembers class com.example.api.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.api.** { *; }

# Keep Coroutines
-keepclassmembers class * extends kotlinx.coroutines.CoroutineScope { *; }

# Preserve line numbers for release crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
