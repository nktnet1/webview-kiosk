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

# android-retrostreams ThreadLocalRandom extends java.util.Random. Random's
# constructor invokes its virtual setSeed() before initialized becomes true.
# Without this keep rule, R8 can remove the guard and crash app startup.
-keep class java9.util.concurrent.ThreadLocalRandom { *; }

# https://hivemq.github.io/hivemq-mqtt-client/docs/installation/android/
-keepclassmembernames class io.netty.** { *; }
-keepclassmembers class org.jctools.** { *; }
-keep class io.netty.handler.codec.** { *; }  # Without this, MQTTS via websocket will hang

-dontwarn io.netty.**
-dontwarn org.slf4j.**
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
