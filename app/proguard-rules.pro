# R8 rules for the release build. Retrofit, kotlinx.serialization, Coil and Media3 ship their
# own consumer rules; these cover what is specific to SamSonic.

# The USB DAC's JNI methods are looked up by name from usb_jni.cpp.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.example.samsonic.playback.usb.NativeUsb { *; }

# Stack traces from crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
