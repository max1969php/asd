# Room, Media3 e Compose forniscono già le proprie consumer rules.

# youtubedl-android: Jackson mappa l'output di yt-dlp per riflessione.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**

# Dropbox SDK: classi opzionali (OkHttp, Google HTTP client, servlet) che non usiamo.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn com.squareup.okhttp.**
-dontwarn com.google.appengine.**
-dontwarn javax.servlet.**
-dontwarn org.joda.time.**
