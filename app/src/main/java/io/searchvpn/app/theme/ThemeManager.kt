package io.searchvpn.app.theme

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
import io.searchvpn.app.R

data class ThemePreset(
    val key: String,
    val name: String,
    val category: String, // "Default", "Movie", "Landscape", "Scene"
    val description: String,
    val primaryColorHex: String,
    val styleRes: Int,
    val wallpaperRes: Int? = null
)

object ThemeManager {

    private const val PREFS = "searchvpn_theme"
    private const val KEY_NIGHT_MODE = "night_mode"
    private const val KEY_THEME_PRESET = "theme_preset"
    private const val KEY_WALLPAPER_URI = "wallpaper_uri"
    private const val KEY_CUSTOM_COLOR = "custom_primary_color"

    const val MODE_SYSTEM = "SYSTEM"
    const val MODE_LIGHT = "LIGHT"
    const val MODE_DARK = "DARK"

    val PRESETS = listOf(
        ThemePreset(
            key = "DEFAULT",
            name = "Default Violet",
            category = "Standard",
            description = "Material 3 standard electric violet",
            primaryColorHex = "#6C5CE7",
            styleRes = R.style.Theme_SearchVpn
        ),
        ThemePreset(
            key = "MATRIX",
            name = "The Matrix",
            category = "Movie",
            description = "Iconic neon digital rain & cyber green",
            primaryColorHex = "#00E676",
            styleRes = R.style.Theme_SearchVpn_Matrix
        ),
        ThemePreset(
            key = "BLADERUNNER",
            name = "Blade Runner 2049",
            category = "Movie",
            description = "Vibrant synthwave neon & cyberpunk dusk",
            primaryColorHex = "#FF007F",
            styleRes = R.style.Theme_SearchVpn_BladeRunner,
            wallpaperRes = R.drawable.img_theme_cyberpunk
        ),
        ThemePreset(
            key = "INTERSTELLAR",
            name = "Interstellar",
            category = "Movie",
            description = "Deep space starlight & celestial gold",
            primaryColorHex = "#FFB300",
            styleRes = R.style.Theme_SearchVpn_Interstellar
        ),
        ThemePreset(
            key = "AURORA",
            name = "Nordic Aurora",
            category = "Landscape",
            description = "Arctic glacial teal & celestial green ribbons",
            primaryColorHex = "#00F5D4",
            styleRes = R.style.Theme_SearchVpn_Aurora,
            wallpaperRes = R.drawable.img_theme_aurora
        ),
        ThemePreset(
            key = "TOKYO",
            name = "Tokyo Neon Night",
            category = "Scene",
            description = "Shinjuku electric purple & rain reflections",
            primaryColorHex = "#A855F7",
            styleRes = R.style.Theme_SearchVpn_Tokyo
        ),
        ThemePreset(
            key = "SUNSET",
            name = "Sedona Sunset",
            category = "Landscape",
            description = "Warm golden hour canyon & terra cotta amber",
            primaryColorHex = "#F97316",
            styleRes = R.style.Theme_SearchVpn_Sunset
        ),
        ThemePreset(
            key = "CRIMSON",
            name = "Action Cinema",
            category = "Movie",
            description = "High adrenaline cinematic ruby red",
            primaryColorHex = "#EF4444",
            styleRes = R.style.Theme_SearchVpn_Crimson
        )
    )

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun getNightMode(context: Context): String =
        prefs(context).getString(KEY_NIGHT_MODE, MODE_SYSTEM) ?: MODE_SYSTEM

    fun setNightMode(context: Context, mode: String) {
        prefs(context).edit().putString(KEY_NIGHT_MODE, mode).apply()
        applyNightMode(context)
    }

    fun applyNightMode(context: Context) {
        val mode = when (getNightMode(context)) {
            MODE_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun getThemePreset(context: Context): String =
        prefs(context).getString(KEY_THEME_PRESET, "DEFAULT") ?: "DEFAULT"

    fun setThemePreset(context: Context, key: String) {
        prefs(context).edit().putString(KEY_THEME_PRESET, key).apply()
        // If preset has a companion wallpaper, set it automatically
        val preset = PRESETS.find { it.key == key }
        if (preset?.wallpaperRes != null) {
            setWallpaperUri(context, "res:${preset.wallpaperRes}")
        }
    }

    fun getThemeRes(context: Context): Int {
        val key = getThemePreset(context)
        return PRESETS.find { it.key == key }?.styleRes ?: R.style.Theme_SearchVpn
    }

    fun applyTheme(activity: Activity) {
        activity.setTheme(getThemeRes(activity))
    }

    fun getWallpaperUri(context: Context): String? =
        prefs(context).getString(KEY_WALLPAPER_URI, null)

    fun setWallpaperUri(context: Context, uri: String?) {
        prefs(context).edit().putString(KEY_WALLPAPER_URI, uri).apply()
    }

    fun clearWallpaper(context: Context) {
        prefs(context).edit().remove(KEY_WALLPAPER_URI).apply()
    }

    fun getCustomColor(context: Context): Int =
        prefs(context).getInt(KEY_CUSTOM_COLOR, Color.parseColor("#6C5CE7"))

    fun setCustomColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_CUSTOM_COLOR, color).apply()
    }

    /**
     * Extracts a vibrant, colorful pixel color from any user uploaded image.
     */
    fun extractDominantColor(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val stepX = maxOf(1, width / 20)
        val stepY = maxOf(1, height / 20)

        var bestColor = Color.parseColor("#6C5CE7")
        var maxSaturation = 0f
        val hsv = FloatArray(3)

        for (x in 0 until width step stepX) {
            for (y in 0 until height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val saturation = hsv[1]
                val brightness = hsv[2]
                // Look for vibrant, non-black, non-white colors
                if (saturation > 0.35f && brightness > 0.3f && brightness < 0.95f) {
                    if (saturation > maxSaturation) {
                        maxSaturation = saturation
                        bestColor = pixel
                    }
                }
            }
        }
        return bestColor
    }
}
