package com.minova.cinema.ui.player

/**
 * Small Activity bridge used by the phone player to publish whether native
 * Picture-in-Picture is currently safe to enter. Keeping the ExoPlayer inside
 * the player Composable avoids a second playback owner or duplicate stream.
 */
interface PictureInPictureHost {
    fun updatePictureInPicturePlayback(
        active: Boolean,
        videoWidth: Int = 16,
        videoHeight: Int = 9,
    )
}
