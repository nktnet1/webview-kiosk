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
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# https://hivemq.github.io/hivemq-mqtt-client/docs/installation/android/
# https://github.com/hivemq/hivemq-mqtt-client/issues/609#issuecomment-1856645928
-keep class io.netty.** { *; }
-keep class org.jctools.** { *; }
-keep class org.conscrypt.** { *; }
-keep class org.bouncycastle.** { *; }
-keep class org.openjsse.** { *; }
-keep class com.aayushatharva.** { *; }
-keep class com.github.luben.** { *; }
-keep class com.google.protobuf.** { *; }
-keep class com.jcraft.** { *; }
-keep class com.ning.** { *; }
-keep class net.jpountz.** { *; }
-keep class org.apache.log4j.** { *; }
-keep class org.jboss.log4j.** { *; }
-keep class org.slf4j.** { *; }
-keep class sun.security.** { *; }

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.aayushatharva.**
-dontwarn com.github.luben.**
-dontwarn com.google.protobuf.**
-dontwarn com.jcraft.**
-dontwarn com.ning.**
-dontwarn io.netty.**
-dontwarn net.jpountz.**
-dontwarn org.apache.log4j.**
-dontwarn org.jboss.log4j.**
-dontwarn org.slf4j.**
-dontwarn sun.security.**
