# R8 rules for the shipped Android app.
#
# Library dependencies contribute their own consumer rules automatically; this file covers what
# only the application knows: reflection-driven entry points, JNI boundaries, and the dynamic
# feature's native classes, which R8 processes as part of the base module's run.

# --- Kotlin / kotlinx.serialization -------------------------------------------------------------
# Serializers are looked up reflectively through generated $$serializer classes and Companion
# objects. The library's own rules cover its runtime; these cover this project's model classes.
-keepattributes InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers class app.logdate.**, studio.hypertext.** {
    *** Companion;
}
-keepclasseswithmembers class app.logdate.**, studio.hypertext.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.logdate.**$$serializer { *; }
-keep,includedescriptorclasses class studio.hypertext.**$$serializer { *; }

# Enum entries are resolved by name when deserializing.
-keepclassmembers enum app.logdate.**, studio.hypertext.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- SQLCipher ----------------------------------------------------------------------------------
# The encrypted-database driver crosses a JNI boundary, so its classes cannot be renamed.
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# --- sherpa-onnx (speech recognition dynamic feature) --------------------------------------------
# Native code resolves these by their exact names; R8 runs over the base module and would
# otherwise strip or rename them out from under the dynamic feature.
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class app.logdate.feature.speech.recognition.SpeechRecognitionProvider { *; }

# --- Coroutines ---------------------------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# --- Diagnostics ---------------------------------------------------------------------------------
# Keep line numbers so Crashlytics stack traces stay actionable, and hide the original file name.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
