# Debug 包不混淆。以后若开 minify，保留 WebView 接口即可。
-keepclassmembers class com.example.mapdistance.MainActivity$JsBridge {
    public *;
}
