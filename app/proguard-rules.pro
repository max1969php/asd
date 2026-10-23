# Room, Media3 e Compose forniscono già le proprie consumer rules.

# youtubedl-android: Jackson mappa l'output di yt-dlp per riflessione.
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.databind.** { *; }
-dontwarn com.fasterxml.jackson.databind.**
-dontwarn java.beans.**


# youtubedl-android estrae Python con commons-compress, che crea i campi extra dello zip per
# riflessione: R8 ne toglieva i costruttori e il TUBO andava in crash al primo download.
# Teniamo per intero le dipendenze della libreria (sono solo nella full) e le annotazioni di Jackson.
-keep class com.fasterxml.jackson.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class org.apache.commons.io.** { *; }
-dontwarn org.apache.commons.compress.**
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
