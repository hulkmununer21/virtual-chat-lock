# This file contains ProGuard rules for the app.
# ProGuard is used to minify/obfuscate code in release builds.

# Keep the main activity
-keep class com.xnigma.xnigma.MainActivity { *; }

# Keep all classes with @Keep annotation
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}

# Keep Compose classes
-keep class androidx.compose.** { *; }

# Keep Room database classes
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Keep Kotlin metadata
-keepattributes *Annotation*, InnerClasses
-keep class kotlin.Metadata { *; }

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
