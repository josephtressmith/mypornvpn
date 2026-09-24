package io.searchvpn.app

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.searchvpn.app.ai.AiAssistantBottomSheetDialog
import io.searchvpn.app.library.LibraryFragment
import io.searchvpn.app.search.SearchFragment
import io.searchvpn.app.search.SitesFragment
import io.searchvpn.app.theme.ThemeManager
import io.searchvpn.app.vpn.VpnFragment
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var nav: BottomNavigationView
    private val fragments = mutableMapOf<Int, Fragment>()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyNightMode(this)

        super.onCreate(savedInstanceState)
        App.instance.appendDiag("activity.txt", "MainActivity.onCreate start")
        setContentView(R.layout.activity_main)
        App.instance.appendDiag("activity.txt", "MainActivity.onCreate content set")
        nav = findViewById(R.id.bottomNav)

        updateWallpaper()

        findViewById<View>(R.id.fabAiAssistant)?.setOnClickListener {
            openAiAssistant()
        }

        if (savedInstanceState == null) {
            val search = SearchFragment()
            fragments[R.id.navSearch] = search
            supportFragmentManager.commitNow {
                add(R.id.fragmentContainer, search, TAG_SEARCH)
            }
        } else {
            listOf(
                R.id.navSearch to TAG_SEARCH,
                R.id.navSites to TAG_SITES,
                R.id.navLibrary to TAG_LIBRARY,
                R.id.navVpn to TAG_VPN,
            ).forEach { (id, tag) ->
                supportFragmentManager.findFragmentByTag(tag)?.let {
                    fragments[id] = it
                }
            }
        }

        nav.setOnItemSelectedListener { item ->
            switchTo(item.itemId)
            true
        }

        val initial = savedInstanceState?.getInt(STATE_SELECTED) ?: R.id.navSearch
        if (nav.selectedItemId != initial) {
            nav.selectedItemId = initial
        }
        switchTo(initial)
    }

    override fun onResume() {
        super.onResume()
        updateWallpaper()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_SELECTED, nav.selectedItemId)
    }

    private fun switchTo(id: Int) {
        val current = fragments[id]
        val target = current ?: when (id) {
            R.id.navSearch -> SearchFragment()
            R.id.navSites -> SitesFragment()
            R.id.navLibrary -> LibraryFragment()
            else -> VpnFragment()
        }
        fragments[id] = target
        supportFragmentManager.commitNow {
            if (current == null) {
                add(R.id.fragmentContainer, target, when (id) {
                    R.id.navSearch -> TAG_SEARCH
                    R.id.navSites -> TAG_SITES
                    R.id.navLibrary -> TAG_LIBRARY
                    else -> TAG_VPN
                })
            } else {
                show(target)
            }
            fragments.values.filter { it !== target }.forEach { hide(it) }
        }
    }

    fun openAiAssistant() {
        val existing = supportFragmentManager.findFragmentByTag(AiAssistantBottomSheetDialog.TAG)
        if (existing == null) {
            val dialog = AiAssistantBottomSheetDialog()
            dialog.show(supportFragmentManager, AiAssistantBottomSheetDialog.TAG)
        }
    }

    fun applyThemeAndRecreate() {
        recreate()
    }

    fun updateWallpaper() {
        val wallpaperImageView = findViewById<ImageView>(R.id.wallpaperImageView) ?: return
        val wallpaperScrim = findViewById<View>(R.id.wallpaperScrim) ?: return
        val wallpaperUri = ThemeManager.getWallpaperUri(this)

        if (wallpaperUri.isNullOrBlank()) {
            wallpaperImageView.visibility = View.GONE
            wallpaperScrim.visibility = View.GONE
            wallpaperImageView.setImageDrawable(null)
        } else {
            if (wallpaperUri.startsWith("res:")) {
                val resId = wallpaperUri.removePrefix("res:").toIntOrNull()
                if (resId != null) {
                    wallpaperImageView.setImageResource(resId)
                    wallpaperImageView.visibility = View.VISIBLE
                    wallpaperScrim.visibility = View.VISIBLE
                } else {
                    wallpaperImageView.visibility = View.GONE
                    wallpaperScrim.visibility = View.GONE
                }
            } else {
                val file = File(wallpaperUri)
                if (file.exists()) {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        wallpaperImageView.setImageBitmap(bmp)
                        wallpaperImageView.visibility = View.VISIBLE
                        wallpaperScrim.visibility = View.VISIBLE
                    } else {
                        wallpaperImageView.visibility = View.GONE
                        wallpaperScrim.visibility = View.GONE
                    }
                } else {
                    wallpaperImageView.visibility = View.GONE
                    wallpaperScrim.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        private const val TAG_SEARCH = "search"
        private const val TAG_SITES = "sites"
        private const val TAG_LIBRARY = "library"
        private const val TAG_VPN = "vpn"
        private const val STATE_SELECTED = "selected_tab"
    }
}