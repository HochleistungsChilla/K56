# Kotlinx serialization keeps generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.resona.player.**$$serializer { *; }
-keepclasseswithmembers class app.resona.player.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Media3 session service
-keep class app.resona.player.playback.PlaybackService { *; }
