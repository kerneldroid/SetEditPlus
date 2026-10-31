# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn moe.shizuku.**

# Keep reflection target on Shizuku
-keepclassmembers class rikka.shizuku.Shizuku {
    *** newProcess(...);
}

# Keep serialized model classes
-keep class com.seteditplus.data.ModifiedSetting { *; }
-keep class com.seteditplus.data.SettingsEntry { *; }
