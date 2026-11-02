# Add project specific ProGuard rules here.
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep class com.aurora.notes.model.** { *; }
