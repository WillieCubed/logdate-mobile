# Keep sync API and cloud classes referenced by tests or Koin wiring
-keep class app.logdate.client.sync.** { *; }
-keepclasseswithmembers class app.logdate.client.sync.** { *; }
-keepnames class app.logdate.client.sync.**
-keepclassmembernames class app.logdate.client.sync.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.internal.SerialClassDescImpl

# Keep ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
