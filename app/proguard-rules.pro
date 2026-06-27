# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Preserve Room Database Entities, DAOs and models
-keep class com.vvf.smartfilemanager.data.** { *; }
-keep interface com.vvf.smartfilemanager.data.** { *; }

# Preserve security helpers and biometric / keystore bindings
-keep class com.vvf.smartfilemanager.security.** { *; }

# Keep kotlinx serialization details and annotations
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
    @kotlinx.serialization.SerialName *;
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

