# Aggressive R8 optimization passes for low-end mobile CPUs
-optimizationpasses 5
-allowaccessmodification
-repackageclasses 'com.animationapp.internal'

# Strip verbose/debug logging from release builds to avoid string allocations
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

