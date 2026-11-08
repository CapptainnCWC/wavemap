# kotlinx.serialization: keep generated serializers for the survey model
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class io.github.capptainncwc.wavemap.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class io.github.capptainncwc.wavemap.survey.**$$serializer { *; }
