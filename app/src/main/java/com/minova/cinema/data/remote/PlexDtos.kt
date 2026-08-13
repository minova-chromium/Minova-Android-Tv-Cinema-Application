package com.minova.cinema.data.remote

import com.google.gson.annotations.SerializedName

data class PlexLibraryResponse(
    @SerializedName("MediaContainer")
    val mediaContainer: MediaContainer = MediaContainer(),
)

data class MediaContainer(
    @SerializedName("size") val size: Int = 0,
    @SerializedName("title1") val title: String? = null,
    @SerializedName("friendlyName") val friendlyName: String? = null,
    @SerializedName("Directory") val directories: List<Directory> = emptyList(),
    @SerializedName("Hub") val hubs: List<Hub> = emptyList(),
    @SerializedName("Metadata") val metadata: List<Metadata> = emptyList(),
)

data class Hub(
    @SerializedName("key") val key: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("Metadata") val metadata: List<Metadata> = emptyList(),
)

data class Directory(
    @SerializedName("key") val key: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("type") val type: String = "",
    @SerializedName("agent") val agent: String? = null,
    @SerializedName("scanner") val scanner: String? = null,
)

data class Metadata(
    @SerializedName("ratingKey") val ratingKey: String = "",
    @SerializedName("guid") val guid: String? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("subtype") val subtype: String? = null,
    @SerializedName("extraType") val extraType: Int? = null,
    @SerializedName("title") val title: String = "Untitled",
    @SerializedName("titleSort") val titleSort: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("tagline") val tagline: String? = null,
    @SerializedName("year") val year: Int? = null,
    @SerializedName("thumb") val thumb: String? = null,
    @SerializedName("art") val art: String? = null,
    @SerializedName("parentThumb") val parentThumb: String? = null,
    @SerializedName("grandparentThumb") val grandparentThumb: String? = null,
    @SerializedName("parentArt") val parentArt: String? = null,
    @SerializedName("grandparentArt") val grandparentArt: String? = null,
    @SerializedName("parentTitle") val parentTitle: String? = null,
    @SerializedName("grandparentTitle") val grandparentTitle: String? = null,
    @SerializedName("parentRatingKey") val parentRatingKey: String? = null,
    @SerializedName("grandparentRatingKey") val grandparentRatingKey: String? = null,
    @SerializedName("contentRating") val contentRating: String? = null,
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("viewOffset") val viewOffset: Long? = null,
    @SerializedName("viewCount") val viewCount: Int? = null,
    @SerializedName("index") val index: Int? = null,
    @SerializedName("parentIndex") val parentIndex: Int? = null,
    @SerializedName("childCount") val childCount: Int? = null,
    @SerializedName("leafCount") val leafCount: Int? = null,
    @SerializedName("viewedLeafCount") val viewedLeafCount: Int? = null,
    @SerializedName("OnDeck") val onDeck: Metadata? = null,
    @SerializedName("Genre") val genres: List<Tag> = emptyList(),
    @SerializedName("Role") val roles: List<PersonTag> = emptyList(),
    @SerializedName("Director") val directors: List<PersonTag> = emptyList(),
    @SerializedName("Writer") val writers: List<PersonTag> = emptyList(),
    @SerializedName("Producer") val producers: List<PersonTag> = emptyList(),
    @SerializedName("Media") val media: List<Media> = emptyList(),
)

data class Tag(
    @SerializedName("tag") val tag: String = "",
)

data class PersonTag(
    @SerializedName("tag") val tag: String = "",
    @SerializedName("role") val role: String? = null,
    @SerializedName("thumb") val thumb: String? = null,
)

data class Media(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("bitrate") val bitrate: Int? = null,
    @SerializedName("width") val width: Int? = null,
    @SerializedName("height") val height: Int? = null,
    @SerializedName("videoResolution") val videoResolution: String? = null,
    @SerializedName("videoCodec") val videoCodec: String? = null,
    @SerializedName("audioCodec") val audioCodec: String? = null,
    @SerializedName("container") val container: String? = null,
    @SerializedName("Part") val parts: List<Part> = emptyList(),
)

data class Part(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("key") val key: String = "",
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("file") val file: String? = null,
    @SerializedName("size") val size: Long? = null,
    @SerializedName("container") val container: String? = null,
    @SerializedName("Stream") val streams: List<Stream> = emptyList(),
)

data class Stream(
    @SerializedName("id") val id: Long? = null,
    @SerializedName("streamType") val streamType: Int? = null,
    @SerializedName("index") val index: Int? = null,
    @SerializedName("key") val key: String? = null,
    @SerializedName("codec") val codec: String? = null,
    @SerializedName("channels") val channels: Int? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("languageCode") val languageCode: String? = null,
    @SerializedName("displayTitle") val displayTitle: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("selected") val selected: Boolean? = null,
    @SerializedName("forced") val forced: Boolean? = null,
)
