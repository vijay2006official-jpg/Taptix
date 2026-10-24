# Taptix ProGuard / R8 Rules

# --- Keep core service and receiver classes ---
-keep class com.example.taptix.service.AutoClickService { *; }
-keep class com.example.taptix.service.OverlayService { *; }
-keep class com.example.taptix.service.TaptixTileService { *; }
-keep class com.example.taptix.service.MotionSafetyDetector { *; }
-keep class com.example.taptix.widget.TaptixWidgetProvider { *; }

# --- Keep all model/data classes (JSON serialization) ---
-keep class com.example.taptix.model.** { *; }
-keep class com.example.taptix.data.AppSettings { *; }
-keep class com.example.taptix.data.ScheduleConfig { *; }

# --- Keep enums ---
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# --- Keep Parcelable ---
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# --- org.json used at runtime ---
-keep class org.json.** { *; }

# --- Jetpack Compose ---
-dontwarn androidx.compose.**

# --- AndroidX Core ---
-dontwarn androidx.core.**

# --- Keep R class ---
-keepclassmembers class **.R$* {
    public static <fields>;
}

# --- Suppress warnings for unused androidx internals ---
-dontwarn android.support.**
-dontwarn com.google.android.material.**
