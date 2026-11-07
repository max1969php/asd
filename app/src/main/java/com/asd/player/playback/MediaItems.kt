package com.asd.player.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.asd.player.domain.model.Song

/** Il mediaId è l'URI del documento: serve a ritrovare il brano (volume, ultimo ascolto). */
fun Song.toMediaItem(): MediaItem {
    val parsed = Uri.parse(uri)
    return MediaItem.Builder()
        .setMediaId(uri)
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
