/*
 * Copyright (c) 2024 Auxio Project
 * MediaSessionInterface.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.playback.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.apache.commons.text.similarity.JaroWinklerSimilarity
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.music.Album
import org.oxycblt.auxio.music.Artist
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Music
import org.oxycblt.auxio.music.MusicParent
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.Playlist
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.music.device.DeviceLibrary
import org.oxycblt.auxio.music.service.MediaSessionUID
import org.oxycblt.auxio.music.user.UserLibrary
import org.oxycblt.auxio.playback.state.PlaybackCommand
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.playback.state.ShuffleMode

class MediaSessionInterface
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackManager: PlaybackStateManager,
    private val commandFactory: PlaybackCommand.Factory,
    private val musicRepository: MusicRepository,
) : MediaSessionCompat.Callback() {
    private val jaroWinkler = JaroWinklerSimilarity()

    override fun onPrepare() {
        super.onPrepare()
        // STUB, we already automatically prepare playback.
    }

    override fun onPrepareFromMediaId(mediaId: String?, extras: Bundle?) {
        super.onPrepareFromMediaId(mediaId, extras)
        // STUB, can't tell when this is called
    }

    override fun onPrepareFromUri(uri: Uri?, extras: Bundle?) {
        super.onPrepareFromUri(uri, extras)
        // STUB, can't tell when this is called
    }

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) {
        super.onPlayFromUri(uri, extras)
        // STUB, can't tell when this is called
    }

    override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
        super.onPlayFromMediaId(mediaId, extras)
        val uid = MediaSessionUID.fromString(mediaId ?: return) ?: return
        val command = expandUidIntoCommand(uid)
        playbackManager.play(requireNotNull(command) { "Invalid playback configuration" })
    }

    override fun onPrepareFromSearch(query: String?, extras: Bundle?) {
        super.onPrepareFromSearch(query, extras)
        // STUB, can't tell when this is called
    }

    override fun onPlayFromSearch(query: String, extras: Bundle) {
        super.onPlayFromSearch(query, extras)
        val deviceLibrary = musicRepository.deviceLibrary ?: return
        val userLibrary = musicRepository.userLibrary ?: return
        val queryBundle =
            QueryBundle(
                (extras.getString(MediaStore.EXTRA_MEDIA_TITLE) ?: query).ifBlank { null },
                extras.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.ifBlank { null },
                extras.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.ifBlank { null },
                extras.getString(MediaStore.EXTRA_MEDIA_GENRE)?.ifBlank { null },
                extras.getString(@Suppress("DEPRECATION") MediaStore.EXTRA_MEDIA_PLAYLIST))
        val command = expandSearchInfoCommand(queryBundle, deviceLibrary, userLibrary)
        playbackManager.play(requireNotNull(command) { "Invalid playback configuration" })
    }

    data class QueryBundle(
        val title: String?,
        val album: String?,
        val artist: String?,
        val genre: String?,
        val playlist: String?
    )

    private fun <T : Music> Collection<T>.fuzzyBest(query: String): T =
        maxByOrNull { jaroWinkler.apply(it.name.resolve(context), query) } ?: first()

    override fun onAddQueueItem(description: MediaDescriptionCompat) {
        super.onAddQueueItem(description)
        val deviceLibrary = musicRepository.deviceLibrary ?: return
        val uid = MediaSessionUID.fromString(description.mediaId ?: return) ?: return
        val song =
            when (uid) {
                is MediaSessionUID.SingleItem -> deviceLibrary.findSong(uid.uid)
                is MediaSessionUID.ChildItem -> deviceLibrary.findSong(uid.childUid)
                else -> null
            }
                ?: return
        playbackManager.addToQueue(song)
    }

    override fun onRemoveQueueItem(description: MediaDescriptionCompat) {
        super.onRemoveQueueItem(description)
        val at = description.extras?.getInt(KEY_QUEUE_POS) ?: return
        playbackManager.removeQueueItem(at)
    }

    override fun onPlay() {
        playbackManager.playing(true)
    }

    override fun onPause() {
        playbackManager.playing(false)
    }

    override fun onSkipToNext() {
        playbackManager.next()
    }

    override fun onSkipToPrevious() {
        playbackManager.prev()
    }

    override fun onSkipToQueueItem(id: Long) {
        playbackManager.goto(id.toInt())
    }

    override fun onSeekTo(position: Long) {
        playbackManager.seekTo(position)
    }

    override fun onFastForward() {
        playbackManager.next()
    }

    override fun onRewind() {
        playbackManager.seekTo(0)
        playbackManager.playing(true)
    }

    override fun onSetRepeatMode(repeatMode: Int) {
        playbackManager.repeatMode(
            when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ALL -> RepeatMode.ALL
                PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.ALL
                PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.TRACK
                else -> RepeatMode.NONE
            })
    }

    override fun onSetShuffleMode(shuffleMode: Int) {
        playbackManager.shuffled(
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP)
    }

    override fun onStop() {
        // Get the service to shut down with the ACTION_EXIT intent
        context.sendBroadcast(Intent(PlaybackActions.ACTION_EXIT))
    }

    override fun onCustomAction(action: String, extras: Bundle?) {
        super.onCustomAction(action, extras)
        // Service already handles intents from the old notification actions, easier to
        // plug into that system.
        context.sendBroadcast(Intent(action))
    }

    private fun expandUidIntoCommand(uid: MediaSessionUID): PlaybackCommand? {
        val music: Music
        var parent: MusicParent? = null
        when (uid) {
            is MediaSessionUID.SingleItem -> {
                music = musicRepository.find(uid.uid) ?: return null
            }
            is MediaSessionUID.ChildItem -> {
                music = musicRepository.find(uid.childUid) ?: return null
                parent = musicRepository.find(uid.parentUid) as? MusicParent ?: return null
            }
            else -> return null
        }

        return when (music) {
            is Song -> inferSongFromParent(music, parent)
            is Album -> commandFactory.album(music, ShuffleMode.OFF)
            is Artist -> commandFactory.artist(music, ShuffleMode.OFF)
            is Genre -> commandFactory.genre(music, ShuffleMode.OFF)
            is Playlist -> commandFactory.playlist(music, ShuffleMode.OFF)
        }
    }

    private fun expandSearchInfoCommand(
        query: QueryBundle,
        deviceLibrary: DeviceLibrary,
        userLibrary: UserLibrary
    ): PlaybackCommand? {
        if (query.album != null) {
            val album = deviceLibrary.albums.fuzzyBest(query.album)
            if (query.title == null) {
                return commandFactory.album(album, ShuffleMode.OFF)
            }
            val song = album.songs.fuzzyBest(query.title)
            return commandFactory.songFromAlbum(song, ShuffleMode.OFF)
        }

        if (query.artist != null) {
            val artist = deviceLibrary.artists.fuzzyBest(query.artist)
            if (query.title == null) {
                return commandFactory.artist(artist, ShuffleMode.OFF)
            }
            val song = artist.songs.fuzzyBest(query.title)
            return commandFactory.songFromArtist(song, artist, ShuffleMode.OFF)
        }

        if (query.genre != null) {
            val genre = deviceLibrary.genres.fuzzyBest(query.genre)
            if (query.title == null) {
                return commandFactory.genre(genre, ShuffleMode.OFF)
            }
            val song = genre.songs.fuzzyBest(query.title)
            return commandFactory.songFromGenre(song, genre, ShuffleMode.OFF)
        }

        if (query.playlist != null) {
            val playlist = userLibrary.playlists.fuzzyBest(query.playlist)
            if (query.title == null) {
                return commandFactory.playlist(playlist, ShuffleMode.OFF)
            }
            val song = playlist.songs.fuzzyBest(query.title)
            return commandFactory.songFromPlaylist(song, playlist, ShuffleMode.OFF)
        }

        if (query.title != null) {
            val song = deviceLibrary.songs.fuzzyBest(query.title)
            return commandFactory.songFromAll(song, ShuffleMode.OFF)
        }

        return commandFactory.all(ShuffleMode.ON)
    }

    private fun inferSongFromParent(music: Song, parent: MusicParent?) =
        when (parent) {
            is Album -> commandFactory.songFromAlbum(music, ShuffleMode.IMPLICIT)
            is Artist -> commandFactory.songFromArtist(music, parent, ShuffleMode.IMPLICIT)
                    ?: commandFactory.songFromArtist(music, music.artists[0], ShuffleMode.IMPLICIT)
            is Genre -> commandFactory.songFromGenre(music, parent, ShuffleMode.IMPLICIT)
                    ?: commandFactory.songFromGenre(music, music.genres[0], ShuffleMode.IMPLICIT)
            is Playlist -> commandFactory.songFromPlaylist(music, parent, ShuffleMode.IMPLICIT)
            null -> commandFactory.songFromAll(music, ShuffleMode.IMPLICIT)
        }

    companion object {
        const val KEY_QUEUE_POS = BuildConfig.APPLICATION_ID + ".metadata.QUEUE_POS"
        const val ACTIONS =
            PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID or
                PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH or
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_REWIND or
                PlaybackStateCompat.ACTION_STOP
    }
}
