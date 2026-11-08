package com.asd.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asd.player.domain.model.Song

/**
 * Il mediaId è l'URI del documento: serve a ritrovare il brano (volume, ultimo ascolto).
 * Con [skipSilence] il pezzo suona solo dal primo all'ultimo suono misurati.
 */
fun Song.toMediaItem(skipSilence: Boolean): MediaItem {
    val parsed = Uri.parse(uri)
    val clipping = MediaItem.ClippingConfiguration.Builder()
    if (skipSilence) {
        soundStartMs?.let { clipping.setStartPositionMs(it) }
        soundEndMs?.let { clipping.setEndPositionMs(it) }
    }
    return MediaItem.Builder()
        .setMediaId(uri)
        .setClippingConfiguration(clipping.build())
        .setUri(parsed)
        .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(parsed).build())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
}
