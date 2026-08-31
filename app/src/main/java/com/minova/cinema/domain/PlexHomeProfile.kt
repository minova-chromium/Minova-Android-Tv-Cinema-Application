package com.minova.cinema.domain

data class PlexHomeProfile(
    val id: Long,
    val uuid: String,
    val title: String,
    val thumbUrl: String?,
    val isProtected: Boolean,
    val isManaged: Boolean,
    val isAdmin: Boolean,
    val isActive: Boolean = false,
)
