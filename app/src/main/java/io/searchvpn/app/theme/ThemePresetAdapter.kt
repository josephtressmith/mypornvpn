package io.searchvpn.app.theme

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.searchvpn.app.databinding.ItemThemePresetBinding

class ThemePresetAdapter(
    private val presets: List<ThemePreset>,
    private var selectedKey: String,
    private val onThemeSelected: (ThemePreset) -> Unit
) : RecyclerView.Adapter<ThemePresetAdapter.Holder>() {

    class Holder(val binding: ItemThemePresetBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemThemePresetBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return Holder(binding)
    }

    override fun getItemCount(): Int = presets.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val preset = presets[position]
        val isSelected = preset.key == selectedKey

        holder.binding.themeName.text = preset.name
        holder.binding.themeDesc.text = preset.description
        holder.binding.categoryBadge.text = preset.category.uppercase()

        // Swatch background color
        val swatchColor = try {
            Color.parseColor(preset.primaryColorHex)
        } catch (_: Exception) {
            Color.BLUE
        }
        val circleDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(swatchColor)
            setStroke(2, Color.WHITE)
        }
        holder.binding.colorSwatch.background = circleDrawable

        // Selected state styling
        holder.binding.checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.binding.themeCard.strokeWidth = if (isSelected) 4 else 1
        holder.binding.themeCard.setStrokeColor(swatchColor)

        holder.binding.themeCard.setOnClickListener {
            val prevKey = selectedKey
            selectedKey = preset.key
            notifyItemChanged(presets.indexOfFirst { it.key == prevKey })
            notifyItemChanged(position)
            onThemeSelected(preset)
        }
    }

    fun setSelected(key: String) {
        val prevKey = selectedKey
        selectedKey = key
        notifyItemChanged(presets.indexOfFirst { it.key == prevKey })
        notifyItemChanged(presets.indexOfFirst { it.key == key })
    }
}
