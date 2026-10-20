# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# CoreViewModel discovers and invokes these methods through Kotlin reflection.
# Suspend functions also require their generic signature and Continuation type to
# remain recognizable to kotlin-reflect after R8 full-mode optimization.
-keepattributes RuntimeVisibleAnnotations,Signature
-keep class kotlin.coroutines.Continuation
-keep,allowoptimization,allowobfuscation class * extends me.kafuuneko.rpclient.libs.core.CoreViewModel
-keep @interface me.kafuuneko.rpclient.libs.core.UiIntentObserver
-keepclassmembers class * {
    @me.kafuuneko.rpclient.libs.core.UiIntentObserver <methods>;
}

# CoreViewModel also uses each intent's exact runtime KClass as the dispatch key.
# Keep distinct UiIntent classes so R8 cannot horizontally merge structurally
# identical data objects and make one event invoke multiple observers.
-keep,allowobfuscation class me.kafuuneko.rpclient.feature.**UiIntent*

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# feat/security (P-14): strip every Logcat write and stack trace print from release builds,
# including those inside libraries, so no prompt, response or key can reach Logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
    public static boolean isLoggable(java.lang.String, int);
}
-assumenosideeffects class java.lang.Throwable {
    public void printStackTrace();
}

# SQLCipher logs through its own Logger; Himitsu installs NoopTarget at startup, and these
# rules drop the calls in release builds as a second barrier.
-assumenosideeffects class net.zetetic.database.Logger {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
    public static void e(...);
    public static void wtf(...);
}
