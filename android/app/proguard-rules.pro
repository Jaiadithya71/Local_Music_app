# ProGuard rules for VibeStream
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.contentagent.vibestream.data.model.** { *; }
