package io.searchvpn.app.library

import android.graphics.BitmapFactory
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import io.searchvpn.app.R
import io.searchvpn.app.data.db.DownloadedVideo
import io.searchvpn.app.databinding.ItemDownloadedVideoBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VideoLibraryAdapter(
    private val onPlay: (DownloadedVideo) -> Unit,
    private val onAction: (Action, DownloadedVideo) -> Unit
) : ListAdapter<DownloadedVideo, VideoLibraryAdapter.VideoViewHolder>(DiffCallback) {

    enum class Action {
        PLAY,
        OPEN_EXTERNAL,
        SHARE,
        MOVE,
        COPY,
        CONVERT,
        RENAME,
        DELETE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ItemDownloadedVideoBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VideoViewHolder(private val binding: ItemDownloadedVideoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadedVideo) {
            val context = itemView.context
            binding.videoTitle.text = item.title
            binding.formatBadge.text = "${item.format} • ${item.quality}"

            val sizeStr = if (item.fileSizeBytes > 0) {
                Formatter.formatFileSize(context, item.fileSizeBytes)
            } else {
                "Calculating size..."
            }
            binding.fileSizeText.text = sizeStr

            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(item.createdAt))
            binding.siteSourceText.text = "${item.siteName.ifBlank { "Web" }} • $dateStr"

            // Duration badge
            if (item.durationMs > 0) {
                binding.durationBadge.visibility = View.VISIBLE
                val secs = item.durationMs / 1000
                val mins = secs / 60
                val remSecs = secs % 60
                binding.durationBadge.text = String.format(Locale.US, "%02d:%02d", mins, remSecs)
            } else {
                binding.durationBadge.visibility = View.GONE
            }

            // Thumbnail loading
            if (!item.thumbnailPath.isNullOrBlank() && File(item.thumbnailPath).exists()) {
                val bitmap = BitmapFactory.decodeFile(item.thumbnailPath)
                if (bitmap != null) {
                    binding.thumbnailView.setImageBitmap(bitmap)
                } else {
                    binding.thumbnailView.setImageResource(android.R.drawable.ic_media_play)
                }
            } else {
                binding.thumbnailView.setImageResource(android.R.drawable.ic_media_play)
            }

            // Progress visibility
            if (item.status == "DOWNLOADING") {
                binding.downloadProgressLayout.visibility = View.VISIBLE
                binding.downloadProgressBar.progress = item.progress
                binding.downloadStatusText.text = "Downloading: ${item.progress}%"
                binding.playIconOverlay.visibility = View.GONE
            } else if (item.status == "FAILED") {
                binding.downloadProgressLayout.visibility = View.VISIBLE
                binding.downloadProgressBar.progress = 0
                binding.downloadStatusText.text = "Download Failed (tap to retry)"
                binding.playIconOverlay.visibility = View.GONE
            } else {
                binding.downloadProgressLayout.visibility = View.GONE
                binding.playIconOverlay.visibility = View.VISIBLE
            }

            binding.thumbnailContainer.setOnClickListener {
                if (item.status == "COMPLETED") onPlay(item)
            }

            binding.root.setOnClickListener {
                if (item.status == "COMPLETED") onPlay(item)
            }

            binding.optionsButton.setOnClickListener { view ->
                showPopupMenu(view, item)
            }
        }

        private fun showPopupMenu(anchor: View, item: DownloadedVideo) {
            val popup = PopupMenu(anchor.context, anchor)
            popup.menu.apply {
                add(0, 1, 0, anchor.context.getString(R.string.action_play))
                add(0, 2, 1, anchor.context.getString(R.string.action_open_external))
                add(0, 3, 2, anchor.context.getString(R.string.action_share))
                add(0, 4, 3, anchor.context.getString(R.string.action_move))
                add(0, 5, 4, anchor.context.getString(R.string.action_copy))
                add(0, 6, 5, anchor.context.getString(R.string.action_convert))
                add(0, 7, 6, anchor.context.getString(R.string.action_rename))
                add(0, 8, 7, anchor.context.getString(R.string.action_delete))
            }

            popup.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    1 -> onAction(Action.PLAY, item)
                    2 -> onAction(Action.OPEN_EXTERNAL, item)
                    3 -> onAction(Action.SHARE, item)
                    4 -> onAction(Action.MOVE, item)
                    5 -> onAction(Action.COPY, item)
                    6 -> onAction(Action.CONVERT, item)
                    7 -> onAction(Action.RENAME, item)
                    8 -> onAction(Action.DELETE, item)
                }
                true
            }
            popup.show()
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<DownloadedVideo>() {
        override fun areItemsTheSame(oldItem: DownloadedVideo, newItem: DownloadedVideo): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DownloadedVideo, newItem: DownloadedVideo): Boolean =
            oldItem == newItem
    }
}
