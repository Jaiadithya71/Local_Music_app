# ProGuard rules for VibeStream
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class com.contentagent.vibestream.data.model.** { *; }

-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
