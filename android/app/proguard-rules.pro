-dontwarn com.umeng.**
-keep class com.umeng.** { *; }
-keepclassmembers class * {
    public <init>(org.json.JSONObject);
}

# Hyper OS focus notification SDK (com.xzakota.hyper.notification) may be
# loaded reflectively; keep the whole package.
-keep class com.xzakota.hyper.** { *; }

-keepattributes Signature,InnerClasses,EnclosingMethod,Annotation
