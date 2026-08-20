# kotlinx.serialization — keep generated serializers for @Serializable types.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.ar13x.jarvis.** {
    *** Companion;
}
-keepclasseswithmembers class com.ar13x.jarvis.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ar13x.jarvis.**$$serializer { *; }

# --- Retrofit -----------------------------------------------------------------
# Signature is what carries generic return types. Without it Retrofit cannot tell
# `suspend fun x(): PagedTasks` from `Object`, and every call fails at set-up
# rather than at the network — a class of breakage that only appears in release.
-keepattributes Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# The API interface is only ever reached reflectively.
-keep,allowobfuscation interface com.ar13x.jarvis.core.network.JarvisApi { *; }
