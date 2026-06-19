# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.miruplay.tv.model.**$$serializer { *; }
-keepclassmembers class com.miruplay.tv.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.miruplay.tv.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.miruplay.tv.core.common.**$$serializer { *; }
-keepclassmembers class com.miruplay.tv.core.common.** {
    *** Companion;
}
-keepclasseswithmembers class com.miruplay.tv.core.common.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.miruplay.tv.repository.**$$serializer { *; }
-keepclassmembers class com.miruplay.tv.repository.** {
    *** Companion;
}
-keepclasseswithmembers class com.miruplay.tv.repository.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.miruplay.tv.scraper.core.**$$serializer { *; }
-keepclassmembers class com.miruplay.tv.scraper.core.** {
    *** Companion;
}
-keepclasseswithmembers class com.miruplay.tv.scraper.core.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Hilt
-keepclasseswithmembers class * {
    @dagger.hilt.* <methods>;
}
-keepclasseswithmembers class * {
    @dagger.hilt.* <fields>;
}
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Lucene vectorization touches JDK module APIs that do not exist on Android.
-dontwarn java.lang.Module
-dontwarn java.lang.ModuleLayer
-dontwarn java.lang.Runtime$Version

# SLF4J - jcifs-ng pulls slf4j-api without a binding implementation
-dontwarn org.slf4j.impl.**

# libVLC / VideoLAN
-keep class org.videolan.libvlc.** { *; }
-dontwarn org.videolan.libvlc.**

# ONNX Runtime Android
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Keep annotation classes
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
