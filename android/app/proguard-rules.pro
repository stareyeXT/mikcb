-dontwarn com.umeng.**
-keep class com.umeng.** { *; }
-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

# Flutter Play Store deferred components (not used, suppress R8 warnings)
-dontwarn com.google.android.play.core.**
-dontwarn io.flutter.embedding.android.FlutterPlayStoreSplitApplication
-dontwarn io.flutter.embedding.engine.deferredcomponents.PlayStoreDeferredComponentManager

# Flutter plugin channel keep rules
-keep class io.flutter.plugin.** { *; }
-keep class io.flutter.plugins.** { *; }
