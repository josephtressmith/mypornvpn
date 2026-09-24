package io.searchvpn.app.download

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.searchvpn.app.R
import io.searchvpn.app.databinding.DialogDownloadOptionsBinding

class DownloadOptionsDialog(
    context: Context,
    private val initialTitle: String,
    private val pageUrl: String,
    private val detectedStreamUrl: String?,
    private val siteName: String
) : BottomSheetDialog(context) {

    private lateinit var binding: DialogDownloadOptionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogDownloadOptionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.videoTitleInput.setText(initialTitle.ifBlank { "Video_${System.currentTimeMillis()}" })
        binding.detectedUrlText.text = pageUrl

        // Format listeners
        binding.formatRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            updateEstimates()
            if (checkedId == R.id.radioAudio) {
                binding.qualityRadioGroup.visibility = android.view.View.GONE
            } else {
                binding.qualityRadioGroup.visibility = android.view.View.VISIBLE
            }
        }

        binding.qualityRadioGroup.setOnCheckedChangeListener { _, _ ->
            updateEstimates()
        }

        updateEstimates()

        binding.startDownloadButton.setOnClickListener {
            val title = binding.videoTitleInput.text?.toString()?.trim()
                ?: "Video_${System.currentTimeMillis()}"

            val format = when (binding.formatRadioGroup.checkedRadioButtonId) {
                R.id.radioWebm -> "WEBM"
                R.id.radioAudio -> "M4A"
                else -> "MP4" // Default MP4
            }

            val quality = when (binding.qualityRadioGroup.checkedRadioButtonId) {
                R.id.radio1080p -> "1080p"
                R.id.radio480p -> "480p"
                R.id.radio360p -> "360p"
                else -> if (format == "M4A") "Audio" else "720p" // Default 720p
            }

            val streamUrl = detectedStreamUrl ?: pageUrl

            io.searchvpn.app.util.KeyboardUtils.hideKeyboard(binding.videoTitleInput)

            VideoDownloadManager.enqueueDownload(
                context = context,
                title = title,
                sourceUrl = pageUrl,
                streamUrl = streamUrl,
                format = format,
                quality = quality,
                siteName = siteName
            )

            dismiss()
        }

        setOnDismissListener {
            io.searchvpn.app.util.KeyboardUtils.hideKeyboard(binding.videoTitleInput)
        }
    }

    private fun updateEstimates() {
        val format = when (binding.formatRadioGroup.checkedRadioButtonId) {
            R.id.radioWebm -> "WEBM"
            R.id.radioAudio -> "M4A"
            else -> "MP4"
        }

        if (format == "M4A") {
            binding.estimatedSizeText.text = "Audio extraction • Estimated size: ~8–15 MB"
            return
        }

        val estimate = when (binding.qualityRadioGroup.checkedRadioButtonId) {
            R.id.radio1080p -> "Estimated size: ~150–220 MB (1080p Full HD)"
            R.id.radio480p -> "Estimated size: ~45–65 MB (480p SD Standard)"
            R.id.radio360p -> "Estimated size: ~20–35 MB (360p Data Saver)"
            else -> "Estimated size: ~80–120 MB (720p HD Default)"
        }
        binding.estimatedSizeText.text = estimate
    }
}
