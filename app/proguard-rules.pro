# Kotlinx serialization keeps generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.k56.player.**$$serializer { *; }
-keepclasseswithmembers class app.k56.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Media3 session service
-keep class app.k56.player.playback.PlaybackService { *; }
