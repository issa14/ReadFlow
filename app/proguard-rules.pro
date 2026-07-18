# ============================================
# InkTone — ProGuard / R8 Rules
# ============================================

# ---- Keep ALL JNI native methods (CRITICAL) ----
-keepclasseswithmembernames class * {
    native <methods>;
}

# ---- ONNX Runtime ----
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# ---- Sherpa-ONNX (TTS engine) ----
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ---- Readium (EPUB parsing) ----
-keep class org.readium.** { *; }
-dontwarn org.readium.**

# ---- Media3 Session (background audio) ----
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ---- Room Database ----
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}
-dontwarn androidx.room.paging.**

# ---- Hilt / Dagger DI ----
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.Module class *
-keep @dagger.hilt.InstallIn class *
-keep @dagger.hilt.android.HiltAndroidApp class *
-keep @dagger.hilt.android.AndroidEntryPoint class *
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }
-keep class com.inktone.di.** { *; }
-keep class com.inktone.InkToneApplication { *; }

# ---- Kotlin Serialization / Data Classes ----
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.inktone.**$$serializer { *; }
-keepclassmembers class com.inktone.** {
    *** Companion;
}
-keepclasseswithmembers class com.inktone.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---- Coroutines ----
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ---- Compose ----
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# ---- AndroidX ----
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ---- Keep data model classes (Room entities + domain) ----
-keep class com.inktone.domain.model.** { *; }
-keep class com.inktone.data.database.entity.** { *; }

# ---- Keep ViewModels (Hilt-injected) ----
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class com.inktone.ui.screen.** { *; }

# ---- Keep Desugaring ----
-keep class j$.time.** { *; }
-dontwarn j$.time.**

# ---- Gson ----
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers class * { @com.google.gson.annotations.SerializedName <fields>; }

# ---- General ----
-keepattributes SourceFile,LineNumberTable
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-renamesourcefileattribute SourceFile

# ---- Remove verbose logging in release ----
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
