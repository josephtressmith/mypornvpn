package io.searchvpn.app.library

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.searchvpn.app.R
import io.searchvpn.app.data.db.AppDatabase
import io.searchvpn.app.data.db.DownloadedVideo
import io.searchvpn.app.data.db.VideoRepository
import io.searchvpn.app.databinding.FragmentLibraryBinding
import io.searchvpn.app.download.VideoFileManager
import io.searchvpn.app.util.KeyboardUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LibraryFragment : Fragment() {

    enum class SortOrder {
        NEWEST,
        OLDEST,
        SIZE_DESC,
        SIZE_ASC
    }

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: VideoRepository
    private lateinit var adapter: VideoLibraryAdapter

    private var allVideosList: List<DownloadedVideo> = emptyList()
    private var currentFilter: String = "ALL"
    private var searchQuery: String = ""
    private var currentSort: SortOrder = SortOrder.NEWEST

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val db = AppDatabase.getInstance(requireContext())
        repository = VideoRepository(db.videoDao())

        adapter = VideoLibraryAdapter(
            onPlay = { video -> playVideo(video) },
            onAction = { action, video -> handleAction(action, video) }
        )

        binding.videoRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.videoRecyclerView.adapter = adapter
        binding.videoRecyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: androidx.recyclerview.widget.RecyclerView, newState: Int) {
                if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                    KeyboardUtils.hideKeyboard(binding.librarySearchInput)
                }
            }
        })

        binding.librarySearchInput.doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString()?.trim().orEmpty()
            applyFilter()
        }

        binding.sortButton.setOnClickListener {
            KeyboardUtils.hideKeyboard(binding.librarySearchInput)
            showSortDialog()
        }

        binding.termuxGuideButton.setOnClickListener {
            KeyboardUtils.hideKeyboard(binding.librarySearchInput)
            showTermuxGuideDialog()
        }

        binding.filterChipGroup.setOnCheckedChangeListener { _, checkedId ->
            KeyboardUtils.hideKeyboard(binding.librarySearchInput)
            currentFilter = when (checkedId) {
                R.id.chipFilterMp4 -> "MP4"
                R.id.chipFilterWebm -> "WEBM"
                R.id.chipFilterAudio -> "AUDIO"
                else -> "ALL"
            }
            applyFilter()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            repository.allVideos.collect { videos ->
                allVideosList = videos
                applyFilter()
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            _binding?.librarySearchInput?.let { KeyboardUtils.hideKeyboard(it) }
        }
    }

    override fun onPause() {
        super.onPause()
        _binding?.librarySearchInput?.let { KeyboardUtils.hideKeyboard(it) }
    }

    override fun onDestroyView() {
        _binding?.librarySearchInput?.let { KeyboardUtils.hideKeyboard(it) }
        super.onDestroyView()
        _binding = null
    }

    private fun showTermuxGuideDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.termux_guide_title)
            .setMessage(R.string.termux_guide_msg)
            .setIcon(R.drawable.ic_terminal)
            .setPositiveButton("Got It", null)
            .setNeutralButton("Ask AI Assistant") { _, _ ->
                (activity as? io.searchvpn.app.MainActivity)?.openAiAssistant()
            }
            .show()
    }

    private fun showSortDialog() {
        val options = arrayOf(
            getString(R.string.sort_newest),
            getString(R.string.sort_oldest),
            getString(R.string.sort_size_desc),
            getString(R.string.sort_size_asc)
        )
        val selectedIndex = when (currentSort) {
            SortOrder.NEWEST -> 0
            SortOrder.OLDEST -> 1
            SortOrder.SIZE_DESC -> 2
            SortOrder.SIZE_ASC -> 3
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sort_by)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                currentSort = when (which) {
                    0 -> SortOrder.NEWEST
                    1 -> SortOrder.OLDEST
                    2 -> SortOrder.SIZE_DESC
                    3 -> SortOrder.SIZE_ASC
                    else -> SortOrder.NEWEST
                }
                applyFilter()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyFilter() {
        var list = when (currentFilter) {
            "MP4" -> allVideosList.filter { it.format.equals("MP4", ignoreCase = true) }
            "WEBM" -> allVideosList.filter { it.format.equals("WEBM", ignoreCase = true) }
            "AUDIO" -> allVideosList.filter { it.format.equals("M4A", ignoreCase = true) || it.quality.equals("Audio", ignoreCase = true) }
            else -> allVideosList
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.siteName.contains(searchQuery, ignoreCase = true)
            }
        }

        list = when (currentSort) {
            SortOrder.NEWEST -> list.sortedByDescending { it.createdAt }
            SortOrder.OLDEST -> list.sortedBy { it.createdAt }
            SortOrder.SIZE_DESC -> list.sortedByDescending { it.fileSizeBytes }
            SortOrder.SIZE_ASC -> list.sortedBy { it.fileSizeBytes }
        }

        adapter.submitList(list)

        val totalSize = allVideosList.sumOf { it.fileSizeBytes }
        val sizeFormatted = Formatter.formatFileSize(requireContext(), totalSize)
        binding.libraryStatsText.text = "${allVideosList.size} videos • $sizeFormatted stored"

        if (list.isEmpty()) {
            binding.emptyLibraryLayout.visibility = View.VISIBLE
            binding.videoRecyclerView.visibility = View.GONE
        } else {
            binding.emptyLibraryLayout.visibility = View.GONE
            binding.videoRecyclerView.visibility = View.VISIBLE
        }
    }

    private fun playVideo(video: DownloadedVideo) {
        VideoPlayerDialog(requireContext(), video).show()
    }

    private fun handleAction(action: VideoLibraryAdapter.Action, video: DownloadedVideo) {
        when (action) {
            VideoLibraryAdapter.Action.PLAY -> playVideo(video)
            VideoLibraryAdapter.Action.OPEN_EXTERNAL -> openExternal(video)
            VideoLibraryAdapter.Action.SHARE -> shareVideo(video)
            VideoLibraryAdapter.Action.MOVE -> moveVideo(video)
            VideoLibraryAdapter.Action.COPY -> copyVideo(video)
            VideoLibraryAdapter.Action.CONVERT -> showConvertDialog(video)
            VideoLibraryAdapter.Action.RENAME -> showRenameDialog(video)
            VideoLibraryAdapter.Action.DELETE -> showDeleteDialog(video)
        }
    }

    private fun openExternal(video: DownloadedVideo) {
        val file = File(video.filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = VideoFileManager.getContentUri(requireContext(), file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            startActivity(Intent.createChooser(intent, "Open with"))
        }.onFailure {
            Toast.makeText(requireContext(), "No video player app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareVideo(video: DownloadedVideo) {
        val file = File(video.filePath)
        if (!file.exists()) {
            Toast.makeText(requireContext(), "File not found", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = VideoFileManager.getContentUri(requireContext(), file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (video.format.equals("M4A", ignoreCase = true)) "audio/*" else "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, video.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Share Video"))
    }

    private fun moveVideo(video: DownloadedVideo) {
        lifecycleScope.launch {
            val newPath = withContext(Dispatchers.IO) {
                VideoFileManager.moveVideoToPublicDownloads(requireContext(), video)
            }
            if (newPath != null) {
                val updated = video.copy(filePath = newPath)
                repository.updateVideo(updated)
                Toast.makeText(requireContext(), "Moved to /Download folder", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to move file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyVideo(video: DownloadedVideo) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                VideoFileManager.copyVideoToPublic(requireContext(), video)
            }
            if (result != null) {
                Toast.makeText(requireContext(), "Saved to Samsung Gallery (Movies/SearchVPN)", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "Failed to export to Gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showConvertDialog(video: DownloadedVideo) {
        val formats = arrayOf("MP4", "WEBM", "M4A (Audio Only)")
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.convert_dialog_title)
            .setItems(formats) { _, which ->
                val targetFormat = when (which) {
                    0 -> "MP4"
                    1 -> "WEBM"
                    else -> "M4A"
                }
                convertVideoFormat(video, targetFormat)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun convertVideoFormat(video: DownloadedVideo, targetFormat: String) {
        if (video.format.equals(targetFormat, ignoreCase = true)) {
            Toast.makeText(requireContext(), "Video is already in $targetFormat format", Toast.LENGTH_SHORT).show()
            return
        }

        Toast.makeText(requireContext(), "Converting to $targetFormat...", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val newFile = withContext(Dispatchers.IO) {
                VideoFileManager.convertVideoFormat(requireContext(), video, targetFormat)
            }
            if (newFile != null) {
                val newVideo = DownloadedVideo(
                    title = "${video.title} ($targetFormat)",
                    sourceUrl = video.sourceUrl,
                    siteName = video.siteName,
                    filePath = newFile.absolutePath,
                    fileName = newFile.name,
                    fileSizeBytes = newFile.length(),
                    format = targetFormat,
                    quality = video.quality,
                    durationMs = video.durationMs,
                    thumbnailPath = video.thumbnailPath
                )
                repository.insertVideo(newVideo)
                Toast.makeText(requireContext(), "Converted to $targetFormat successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Conversion failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showRenameDialog(video: DownloadedVideo) {
        val input = EditText(requireContext()).apply {
            setText(video.title)
            setSelection(video.title.length)
        }
        val container = FrameLayout(requireContext()).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.rename_dialog_title)
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                KeyboardUtils.hideKeyboard(input)
                val newTitle = input.text.toString().trim()
                if (newTitle.isNotEmpty() && newTitle != video.title) {
                    lifecycleScope.launch {
                        val updated = video.copy(title = newTitle)
                        repository.updateVideo(updated)
                        Toast.makeText(requireContext(), "Renamed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                KeyboardUtils.hideKeyboard(input)
            }
            .setOnDismissListener {
                KeyboardUtils.hideKeyboard(input)
            }
            .show()
    }

    private fun showDeleteDialog(video: DownloadedVideo) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.confirm_delete_title)
            .setMessage(getString(R.string.confirm_delete_msg, video.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        VideoFileManager.deleteVideoFiles(video)
                    }
                    repository.deleteVideo(video)
                    Toast.makeText(requireContext(), "Deleted", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
