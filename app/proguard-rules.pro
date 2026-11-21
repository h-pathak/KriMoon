# KriMoon Release R8 / ProGuard Configuration

# Preserve stack trace line numbers while obfuscating source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,*Annotation*

# Keep Firebase Firestore POJO models & @Keep annotated classes (reflection deserialization)
-keep class com.example.cloud.CoupleCloudRoom { *; }
-keep class com.example.cloud.CoupleCloudInvite { *; }
-keep class com.example.cloud.CoupleEncryptedBlock { *; }
-keep @androidx.annotation.Keep class * { *; }
-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# Keep Room database, entities, and generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep class com.example.data.** { *; }

# Keep Jetpack Credential Manager & Google ID token credential classes
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }

# Keep Firebase App Check Play Integrity provider entrypoints
-keep class com.google.firebase.appcheck.playintegrity.** { *; }

# Strip verbose/debug/info logs from release builds to prevent metadata leakage
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
