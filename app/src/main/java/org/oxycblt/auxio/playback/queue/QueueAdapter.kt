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
 
package org.oxycblt.auxio.playback.queue

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.MaterialShapeDrawable
import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.databinding.ItemQueueSongBinding
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.ui.BindingViewHolder
import org.oxycblt.auxio.ui.MonoAdapter
import org.oxycblt.auxio.ui.SongViewHolder
import org.oxycblt.auxio.ui.SyncBackingData
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.disableDropShadowCompat
import org.oxycblt.auxio.util.inflater
import org.oxycblt.auxio.util.stateList
import org.oxycblt.auxio.util.textSafe

class QueueAdapter(listener: QueueItemListener) :
    MonoAdapter<Song, QueueItemListener, QueueSongViewHolder>(listener) {
    override val data = SyncBackingData(this, QueueSongViewHolder.DIFFER)
    override val creator = QueueSongViewHolder.CREATOR
}

interface QueueItemListener {
    fun onPickUp(viewHolder: RecyclerView.ViewHolder)
}

class QueueSongViewHolder
private constructor(
    private val binding: ItemQueueSongBinding,
) : BindingViewHolder<Song, QueueItemListener>(binding.root) {
    val bodyView: View
        get() = binding.body
    val backgroundView: View
        get() = binding.background

    init {
        binding.body.background =
            MaterialShapeDrawable.createWithElevationOverlay(binding.root.context).apply {
                fillColor = (binding.body.background as ColorDrawable).color.stateList
            }

        binding.root.disableDropShadowCompat()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun bind(item: Song, listener: QueueItemListener) {
        binding.songAlbumCover.bind(item)
        binding.songName.textSafe = item.resolveName(binding.context)
        binding.songInfo.textSafe = item.resolveIndividualArtistName(binding.context)

        binding.background.isInvisible = true

        binding.songName.requestLayout()
        binding.songInfo.requestLayout()

        // Roll our own drag handlers as the default ones suck
        binding.songDragHandle.setOnTouchListener { _, motionEvent ->
            binding.songDragHandle.performClick()
            if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                listener.onPickUp(this)
                true
            } else false
        }

        binding.body.setOnLongClickListener {
            listener.onPickUp(this)
            true
        }
    }

    companion object {
        val CREATOR =
            object : Creator<QueueSongViewHolder> {
                override val viewType: Int
                    get() = IntegerTable.ITEM_TYPE_QUEUE_SONG

                override fun create(context: Context): QueueSongViewHolder =
                    QueueSongViewHolder(ItemQueueSongBinding.inflate(context.inflater))
            }

        val DIFFER = SongViewHolder.DIFFER
    }
}
