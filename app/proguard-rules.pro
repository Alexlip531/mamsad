# Add project specific ProGuard rules here.
-keepattributes Signature
-keepattributes *Annotation*

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass <methods>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }
-keep class kotlin.Metadata { *; }

# Retrofit
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
