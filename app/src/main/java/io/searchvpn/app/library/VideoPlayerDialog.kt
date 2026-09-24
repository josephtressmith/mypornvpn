package io.searchvpn.app.library

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.Toast
import io.searchvpn.app.R
import io.searchvpn.app.data.db.DownloadedVideo
import io.searchvpn.app.databinding.DialogVideoPlayerBinding
import io.searchvpn.app.download.VideoFileManager
import java.io.File
import java.util.Locale

class VideoPlayerDialog(
    context: Context,
    private val video: DownloadedVideo
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen) {

    private lateinit var binding: DialogVideoPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var isUserSeeking = false

    private val progressUpdater = object : Runnable {
        override fun run() {
            if (!isUserSeeking && binding.playerVideoView.isPlaying) {
                val current = binding.playerVideoView.currentPosition
                val total = binding.playerVideoView.duration
                binding.playerSeekBar.progress = if (total > 0) (current * 100) / total else 0
                binding.playerCurrentTimeText.text = formatTime(current)
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        binding.playerTitleText.text = "${video.title} (${video.quality} ${video.format})"

        binding.playerCloseButton.setOnClickListener {
            binding.playerVideoView.stopPlayback()
            dismiss()
        }

        binding.playerExternalButton.setOnClickListener {
            openExternalPlayer()
        }

        val videoFile = File(video.filePath)
        if (!videoFile.exists()) {
            Toast.makeText(context, "Video file not found at: ${video.filePath}", Toast.LENGTH_LONG).show()
            dismiss()
            return
        }

        val contentUri: Uri = try {
            VideoFileManager.getContentUri(context, videoFile)
        } catch (e: Exception) {
            Uri.fromFile(videoFile)
        }

        binding.playerLoadingProgress.visibility = View.VISIBLE
        binding.playerVideoView.setVideoURI(contentUri)

        binding.playerVideoView.setOnPreparedListener { mp ->
            binding.playerLoadingProgress.visibility = View.GONE
            val duration = mp.duration
            binding.playerTotalTimeText.text = formatTime(duration)
            binding.playerVideoView.start()
            binding.playerPlayPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            handler.post(progressUpdater)
        }

        binding.playerVideoView.setOnCompletionListener {
            binding.playerPlayPauseButton.setImageResource(android.R.drawable.ic_media_play)
            binding.playerSeekBar.progress = 100
        }

        binding.playerVideoView.setOnErrorListener { _, _, _ ->
            binding.playerLoadingProgress.visibility = View.GONE
            Toast.makeText(context, "Cannot play video. Try opening in external player.", Toast.LENGTH_LONG).show()
            true
        }

        binding.playerPlayPauseButton.setOnClickListener {
            if (binding.playerVideoView.isPlaying) {
                binding.playerVideoView.pause()
                binding.playerPlayPauseButton.setImageResource(android.R.drawable.ic_media_play)
            } else {
                binding.playerVideoView.start()
                binding.playerPlayPauseButton.setImageResource(android.R.drawable.ic_media_pause)
            }
        }

        binding.playerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val total = binding.playerVideoView.duration
                    val target = (progress * total) / 100
                    binding.playerCurrentTimeText.text = formatTime(target)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val progress = seekBar?.progress ?: 0
                val total = binding.playerVideoView.duration
                val target = (progress * total) / 100
                binding.playerVideoView.seekTo(target)
                isUserSeeking = false
            }
        })
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(progressUpdater)
        binding.playerVideoView.stopPlayback()
    }

    private fun openExternalPlayer() {
        val file = File(video.filePath)
        if (!file.exists()) return
        val uri = VideoFileManager.getContentUri(context, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (video.format == "M4A") "audio/*" else "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(intent, "Open video with"))
        } catch (e: Exception) {
            Toast.makeText(context, "No external video player found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatTime(millis: Int): String {
        val totalSecs = millis / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        val hours = mins / 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins % 60, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
