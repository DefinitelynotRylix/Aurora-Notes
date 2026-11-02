# Add project specific ProGuard rules here.
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep class com.iris.notes.model.** { *; }
