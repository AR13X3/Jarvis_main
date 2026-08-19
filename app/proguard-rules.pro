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
