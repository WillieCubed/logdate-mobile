# Keep test classes
-keep class app.logdate.client.sync.cloud.** { *; }
-keepclasseswithmembers class app.logdate.client.sync.cloud.** { *; }
-keepnames class app.logdate.client.sync.cloud.**
-keepclassmembernames class app.logdate.client.sync.cloud.** { *; }

# Keep Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.internal.SerialClassDescImpl

# Keep ktor
-keep class io.ktor.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}