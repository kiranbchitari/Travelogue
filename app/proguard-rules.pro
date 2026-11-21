# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.travelogue.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.travelogue.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.travelogue.app.**$$serializer { *; }
