# Room, Media3 e Compose forniscono già le proprie consumer rules.

# youtubedl-android: Jackson mappa l'output di yt-dlp per riflessione.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**

