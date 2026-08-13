# DTO field names are explicitly mapped with @SerializedName, but keep their
# annotations/signatures for Gson's reflective adapter in release builds.
-keepattributes Signature,*Annotation*
-keep class com.minova.cinema.data.remote.** { *; }

