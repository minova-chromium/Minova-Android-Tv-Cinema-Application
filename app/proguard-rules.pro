# DTO field names are explicitly mapped with @SerializedName, but keep their
# annotations/signatures for Gson's reflective adapter in release builds.
-keepattributes Signature,*Annotation*
-keep class com.minova.cinema.data.remote.** { *; }

# Loaded reflectively only when the developer-only Google Home SDK is enabled.
-keep class com.minova.cinema.home.GoogleHomeCinemaLightingController { *; }
