/*
 * Copyright (c) 2021 Auxio Project
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
 
package org.oxycblt.auxio.playback.system

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.media.session.MediaButtonReceiver
import com.google.android.exoplayer2.Player
import org.oxycblt.auxio.R
import org.oxycblt.auxio.image.BitmapProvider
import org.oxycblt.auxio.music.MusicParent
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.playback.state.PlaybackStateManager
import org.oxycblt.auxio.playback.state.RepeatMode
import org.oxycblt.auxio.settings.Settings
import org.oxycblt.auxio.util.logD

/**
 * The component managing the [MediaSessionCompat] instance.
 *
 * I really don't like how I have to do this, but until I can work with the ExoPlayer queue system
 * using something like MediaSessionConnector is more or less impossible.
 *
 * @author OxygenCobalt
 */
class MediaSessionComponent(private val context: Context, private val player: Player) :
    Player.Listener,
    MediaSessionCompat.Callback(),
    PlaybackStateManager.Callback,
    Settings.Callback {
    private val playbackManager = PlaybackStateManager.getInstance()
    private val settings = Settings(context, this)
    private val mediaSession =
        MediaSessionCompat(context, context.packageName).apply { isActive = true }
    private val provider = BitmapProvider(context)

    val token: MediaSessionCompat.Token
        get() = mediaSession.sessionToken

    init {
        player.addListener(this)
        playbackManager.addCallback(this)
        mediaSession.setCallback(this)
    }

    fun handleMediaButtonIntent(intent: Intent) {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
    }

    fun release() {
        provider.release()
        settings.release()
        player.removeListener(this)
        playbackManager.removeCallback(this)

        mediaSession.apply {
            isActive = false
            release()
        }
    }

    // --- PLAYBACKSTATEMANAGER CALLBACKS ---

    override fun onIndexMoved(index: Int) {
        updateMediaMetadata(playbackManager.song)
    }

    override fun onNewPlayback(index: Int, queue: List<Song>, parent: MusicParent?) {
        updateMediaMetadata(playbackManager.song)
    }

    private fun updateMediaMetadata(song: Song?) {
        if (song == null) {
            mediaSession.setMetadata(emptyMetadata)
            return
        }

        val title = song.resolveName(context)
        val artist = song.resolveIndividualArtistName(context)
        val metadata =
            MediaMetadataCompat.Builder()
                .putText(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putText(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album.resolveName(context))
                .putText(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putText(
                    MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST,
                    song.album.artist.resolveName(context))
                .putText(MediaMetadataCompat.METADATA_KEY_AUTHOR, artist)
                .putText(MediaMetadataCompat.METADATA_KEY_COMPOSER, artist)
                .putText(MediaMetadataCompat.METADATA_KEY_WRITER, artist)
                .putText(MediaMetadataCompat.METADATA_KEY_GENRE, song.genre.resolveName(context))
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.durationMs)

        if (song.track != null) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, song.track.toLong())
        }

        if (song.disc != null) {
            metadata.putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, song.disc.toLong())
        }

        if (song.album.year != null) {
            metadata.putString(MediaMetadataCompat.METADATA_KEY_DATE, song.album.year.toString())
        }

        // Normally, android expects one to provide a URI to the metadata instance instead of
        // a full blown bitmap. In practice, this is not ideal in the slightest, as we cannot
        // provide any user customization or quality of life improvements with a flat URI.
        // Instead, we load a full size bitmap and use it within it's respective fields.
        provider.load(
            song,
            object : BitmapProvider.Target {
                override fun onCompleted(bitmap: Bitmap?) {
                    metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                    metadata.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                    mediaSession.setMetadata(metadata.build())
                }
            })
    }

    override fun onPlayingChanged(isPlaying: Boolean) {
        invalidateSessionState()
    }

    override fun onRepeatChanged(repeatMode: RepeatMode) {
        // TODO: Add the custom actions for Android 13
        mediaSession.setRepeatMode(
            when (repeatMode) {
                RepeatMode.NONE -> PlaybackStateCompat.REPEAT_MODE_NONE
                RepeatMode.TRACK -> PlaybackStateCompat.REPEAT_MODE_ONE
                RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
            })
    }

    override fun onShuffledChanged(isShuffled: Boolean) {
        mediaSession.setShuffleMode(
            if (isShuffled) {
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            } else {
                PlaybackStateCompat.SHUFFLE_MODE_NONE
            })
    }

    // --- SETTINGSMANAGER CALLBACKS ---

    override fun onSettingChanged(key: String) {
        if (key == context.getString(R.string.set_key_show_covers) ||
            key == context.getString(R.string.set_key_quality_covers)) {
            updateMediaMetadata(playbackManager.song)
        }
    }

    // --- EXOPLAYER CALLBACKS ---

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        invalidateSessionState()
    }

    // --- MEDIASESSION CALLBACKS ---

    override fun onPlay() {
        playbackManager.isPlaying = true
    }

    override fun onPause() {
        playbackManager.isPlaying = false
    }

    override fun onSkipToNext() {
        playbackManager.next()
    }

    override fun onSkipToPrevious() {
        playbackManager.prev()
    }

    override fun onSeekTo(position: Long) {
        playbackManager.seekTo(position)
    }

    override fun onRewind() {
        playbackManager.rewind()
        playbackManager.isPlaying = true
    }

    override fun onSetRepeatMode(repeatMode: Int) {
        playbackManager.repeatMode =
            when (repeatMode) {
                PlaybackStateCompat.REPEAT_MODE_ALL -> RepeatMode.ALL
                PlaybackStateCompat.REPEAT_MODE_GROUP -> RepeatMode.ALL
                PlaybackStateCompat.REPEAT_MODE_ONE -> RepeatMode.TRACK
                else -> RepeatMode.NONE
            }
    }

    override fun onSetShuffleMode(shuffleMode: Int) {
        playbackManager.reshuffle(
            shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP,
            settings)
    }

    override fun onStop() {
        // Get the service to shut down with the ACTION_EXIT intent
        context.sendBroadcast(Intent(PlaybackService.ACTION_EXIT))
    }

    // --- MISC ---

    private fun invalidateSessionState() {
        logD("Updating media session playback state")

        // Position updates arrive faster when you upload a state that is different, as it
        // forces the system to re-poll the position.
        // FIXME: For some reason however, positions just DON'T UPDATE AT ALL when you
        //  change from FROM THE APP ONLY WHEN THE PLAYER IS PAUSED.
        val state =
            PlaybackStateCompat.Builder()
                .setActions(ACTIONS)
                .addCustomAction(
                    PlaybackStateCompat.CustomAction.Builder(
                            PlaybackService.ACTION_INC_REPEAT_MODE,
                            context.getString(R.string.desc_change_repeat),
                            R.drawable.ic_remote_repeat_off)
                        .build())
                .setBufferedPosition(player.bufferedPosition)

        state.setState(PlaybackStateCompat.STATE_NONE, player.bufferedPosition, 1.0f)

        mediaSession.setPlaybackState(state.build())

        val playerState =
            if (playbackManager.isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }

        state.setState(playerState, player.currentPosition, 1.0f, SystemClock.elapsedRealtime())

        mediaSession.setPlaybackState(state.build())
    }

    companion object {
        private val emptyMetadata = MediaMetadataCompat.Builder().build()

        private const val ACTIONS =
            PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SET_REPEAT_MODE or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP
    }
}
