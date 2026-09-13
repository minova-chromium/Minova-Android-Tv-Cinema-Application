package com.minova.cinema.domain

/** Persist a Plex ID only when it identifies the track the viewer actually chose. */
internal fun matchPlexAudio(
    streams: List<AudioStream>, label: String, language: String?, codec: String?, channels: Int?,
): AudioStream? = streams.filter { it.label.equals(label, true) }.singleOrNull()
    ?: streams.filter {
        language != null && sameTrackLanguage(it.language, language) &&
            (codec == null || it.codec.equals(codec, true)) &&
            (channels == null || it.channels == channels)
    }.singleOrNull()

internal fun matchPlexSubtitle(
    streams: List<SubtitleStream>, formatId: String?, label: String, language: String?,
): SubtitleStream? = streams.firstOrNull { formatId == "plex-subtitle:${it.id}" }
    ?: streams.filter { it.label.equals(label, true) }.singleOrNull()
    ?: streams.filter { language != null && sameTrackLanguage(it.language, language) }.singleOrNull()

internal fun sameTrackLanguage(first: String?, second: String): Boolean {
    fun normalize(value: String?) = when (val code = value?.lowercase(java.util.Locale.ROOT)?.substringBefore('-')) {
        "eng" -> "en"; "nld", "dut" -> "nl"; "fra", "fre" -> "fr"; "deu", "ger" -> "de"
        "spa" -> "es"; "ita" -> "it"; "jpn" -> "ja"; else -> code
    }
    return normalize(first) == normalize(second)
}
