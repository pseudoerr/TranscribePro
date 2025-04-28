# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep JNI method implementations
-keep class com.whispercpp.whisper.WhisperLib$Companion {
    native <methods>;
}

# Keep whisper.cpp native code
-keepclasseswithmembers class com.whispercpp.whisper.** {
    native <methods>;
}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable 