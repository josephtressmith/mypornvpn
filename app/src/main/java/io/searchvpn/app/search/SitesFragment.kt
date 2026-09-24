package io.searchvpn.app.search

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.searchvpn.app.MainActivity
import io.searchvpn.app.R
import io.searchvpn.app.data.ConfigStore
import io.searchvpn.app.data.SearchSite
import io.searchvpn.app.data.Sites
import io.searchvpn.app.databinding.FragmentSitesBinding
import io.searchvpn.app.theme.ThemeManager
import io.searchvpn.app.theme.ThemePreset
import io.searchvpn.app.theme.ThemePresetAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class SitesFragment : Fragment() {

    private var _binding: FragmentSitesBinding? = null
    private val binding get() = _binding!!

    private var adapter: SiteListAdapter? = null
    private var themeAdapter: ThemePresetAdapter? = null

    private val pickVisualMediaLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            handleUploadedImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSitesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupThemeControls()
        setupCustomUploadControls()
        setupSiteControls()
    }

    private fun setupThemeControls() {
        // Display mode (Light / Dark / System)
        val currentMode = ThemeManager.getNightMode(requireContext())
        when (currentMode) {
            ThemeManager.MODE_LIGHT -> binding.modeToggleGroup.check(R.id.btnModeLight)
            ThemeManager.MODE_DARK -> binding.modeToggleGroup.check(R.id.btnModeDark)
            else -> binding.modeToggleGroup.check(R.id.btnModeSystem)
        }

        binding.modeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val newMode = when (checkedId) {
                R.id.btnModeLight -> ThemeManager.MODE_LIGHT
                R.id.btnModeDark -> ThemeManager.MODE_DARK
                else -> ThemeManager.MODE_SYSTEM
            }
            if (newMode != ThemeManager.getNightMode(requireContext())) {
                ThemeManager.setNightMode(requireContext(), newMode)
            }
        }

        // Theme presets (Movies & Landscapes)
        val currentPreset = ThemeManager.getThemePreset(requireContext())
        themeAdapter = ThemePresetAdapter(
            presets = ThemeManager.PRESETS,
            selectedKey = currentPreset
        ) { preset ->
            applyPreset(preset)
        }
        binding.themePresetsRecyclerView.adapter = themeAdapter

        renderCustomWallpaperStatus()
    }

    private fun applyPreset(preset: ThemePreset) {
        ThemeManager.setThemePreset(requireContext(), preset.key)
        Toast.makeText(
            requireContext(),
            getString(R.string.theme_applied_toast, preset.name),
            Toast.LENGTH_SHORT
        ).show()

        renderCustomWallpaperStatus()
        (activity as? MainActivity)?.applyThemeAndRecreate()
    }

    private fun setupCustomUploadControls() {
        binding.btnUploadWallpaper.setOnClickListener {
            pickVisualMediaLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }

        binding.btnResetWallpaper.setOnClickListener {
            ThemeManager.clearWallpaper(requireContext())
            renderCustomWallpaperStatus()
            (activity as? MainActivity)?.updateWallpaper()
            Toast.makeText(requireContext(), "Wallpaper reset", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleUploadedImage(uri: Uri) {
        val context = context ?: return
        Toast.makeText(context, "Processing custom artwork…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val destFile = File(context.filesDir, "custom_wallpaper.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    val bmp = BitmapFactory.decodeFile(destFile.absolutePath)
                    val dominantColor = if (bmp != null) {
                        ThemeManager.extractDominantColor(bmp)
                    } else {
                        Color.parseColor("#6C5CE7")
                    }
                    Pair(destFile.absolutePath, dominantColor)
                }.getOrNull()
            }

            if (result != null) {
                val (filePath, dominantColor) = result
                ThemeManager.setWallpaperUri(context, filePath)
                ThemeManager.setCustomColor(context, dominantColor)
                renderCustomWallpaperStatus()
                (activity as? MainActivity)?.updateWallpaper()
                val hex = String.format("#%06X", 0xFFFFFF and dominantColor)
                Toast.makeText(context, "Custom theme active! Extracted accent: $hex", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Failed to load selected image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderCustomWallpaperStatus() {
        val context = context ?: return
        val wallpaperUri = ThemeManager.getWallpaperUri(context)

        if (wallpaperUri.isNullOrBlank()) {
            binding.customWallpaperPreviewLayout.visibility = View.GONE
            binding.btnResetWallpaper.visibility = View.GONE
        } else {
            binding.customWallpaperPreviewLayout.visibility = View.VISIBLE
            binding.btnResetWallpaper.visibility = View.VISIBLE

            val color = ThemeManager.getCustomColor(context)
            val hex = String.format("#%06X", 0xFFFFFF and color)
            binding.customColorStatus.text = "Accent Color: $hex"

            val dotDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
                setStroke(2, Color.WHITE)
            }
            binding.customExtractedColorDot.background = dotDrawable

            if (wallpaperUri.startsWith("res:")) {
                val resId = wallpaperUri.removePrefix("res:").toIntOrNull()
                if (resId != null) {
                    binding.customWallpaperThumb.setImageResource(resId)
                }
            } else {
                val file = File(wallpaperUri)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    binding.customWallpaperThumb.setImageBitmap(bmp)
                }
            }
        }
    }

    private fun setupSiteControls() {
        binding.siteList.layoutManager = LinearLayoutManager(requireContext())
        adapter = SiteListAdapter(
            items = Sites.ALL.map { s -> SiteListAdapter.Item(s, isEnabled(s.key)) },
        ) { key, checked ->
            val keys = ConfigStore.enabledSites(requireContext())
            if (checked) keys.add(key) else keys.remove(key)
            ConfigStore.setEnabledSites(requireContext(), keys)
        }
        binding.siteList.adapter = adapter

        binding.desktopSwitch.isChecked = ConfigStore.desktopMode(requireContext())
        binding.desktopSwitch.setOnCheckedChangeListener { _, checked ->
            ConfigStore.setDesktopMode(requireContext(), checked)
        }

        binding.allButton.setOnClickListener {
            adapter?.setAll(true)
            ConfigStore.setEnabledSites(requireContext(), Sites.ALL.map { it.key }.toSet())
        }
        binding.noneButton.setOnClickListener {
            adapter?.setAll(false)
            ConfigStore.setEnabledSites(requireContext(), emptySet())
        }
    }

    override fun onResume() {
        super.onResume()
        renderCustomWallpaperStatus()
        adapter?.setAllEnabledMap(
            Sites.ALL.map { it.key to isEnabled(it.key) }.toMap()
        )
    }

    override fun onDestroyView() {
        adapter = null
        themeAdapter = null
        _binding = null
        super.onDestroyView()
    }

    private fun isEnabled(key: String) = key in ConfigStore.enabledSites(requireContext())
}

class SiteListAdapter(
    private var items: List<Item>,
    private val onToggle: (key: String, checked: Boolean) -> Unit,
) : androidx.recyclerview.widget.RecyclerView.Adapter<SiteListAdapter.Holder>() {

    data class Item(val site: SearchSite, var enabled: Boolean)

    class Holder(val row: io.searchvpn.app.databinding.ItemSiteBinding) :
        androidx.recyclerview.widget.RecyclerView.ViewHolder(row.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): Holder {
        val row = io.searchvpn.app.databinding.ItemSiteBinding.inflate(
            LayoutInflater.from(parent.context), parent, false,
        )
        return Holder(row)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.row.siteSwitch.text = item.site.name
        holder.row.siteSwitch.isChecked = item.enabled
        holder.row.siteSwitch.setOnCheckedChangeListener { _, checked ->
            item.enabled = checked
            onToggle(item.site.key, checked)
        }
    }

    fun setAll(enabled: Boolean) {
        items = items.map { it.copy(enabled = enabled) }
        items.forEachIndexed { i, _ -> notifyItemChanged(i) }
    }

    fun setAllEnabledMap(map: Map<String, Boolean>) {
        var changed = false
        items = items.map { item ->
            val e = map[item.site.key] ?: item.enabled
            if (e != item.enabled) changed = true
            item.copy(enabled = e)
        }
        if (changed) notifyDataSetChanged()
    }
}
