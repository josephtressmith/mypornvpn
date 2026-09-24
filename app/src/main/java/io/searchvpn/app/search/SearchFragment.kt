package io.searchvpn.app.search

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import io.searchvpn.app.data.ConfigStore
import io.searchvpn.app.data.SearchSite
import io.searchvpn.app.data.Sites
import io.searchvpn.app.R
import io.searchvpn.app.databinding.FragmentSearchBinding

class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!

    private var query: String? = null
    private var adapter: SitePageAdapter? = null
    private var mediator: TabLayoutMediator? = null
    private var backCallback: OnBackPressedCallback? = null
    private var fingerprint = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.searchButton.setOnClickListener { doSearch() }
        binding.openButton.setOnClickListener { openInBrowser() }
        binding.queryInput.setOnEditorActionListener { _, _, _ ->
            doSearch()
            true
        }
        binding.queryInput.doOnTextChanged { text, _, _, _ ->
            binding.searchButton.isEnabled = text.isNullOrBlank().not()
        }
        binding.searchButton.isEnabled = binding.queryInput.text.isNullOrBlank().not()

        backCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                val pager = binding.pager
                val webView = pagerCurrentWebView()
                when {
                    webView?.canGoBack() == true -> webView.goBack()
                    pager.currentItem > 0 -> pager.setCurrentItem(pager.currentItem - 1, true)
                    else -> {
                        isEnabled = false
                        requireActivity().onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback!!)
        updateBackEnabled()

        rebuildIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateBackEnabled()
        rebuildIfNeeded()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        updateBackEnabled()
    }

    override fun onDestroyView() {
        _binding = null
        mediator?.detach()
        mediator = null
        super.onDestroyView()
    }

    private fun updateBackEnabled() {
        backCallback?.isEnabled = isVisible && isAdded
    }

    private fun currentSites(): List<SearchSite> {
        val keys = ConfigStore.enabledSites(requireContext())
        return Sites.ALL.filter { it.key in keys }
    }

    private fun fingerprint(): String {
        val keys = ConfigStore.enabledSites(requireContext()).toSortedSet()
        val desktop = ConfigStore.desktopMode(requireContext())
        return "$keys|$desktop|$query"
    }

    private fun rebuildIfNeeded() {
        val fp = fingerprint()
        if (fp != fingerprint) {
            fingerprint = fp
            rebuild()
        }
    }

    private fun forceRebuild() {
        fingerprint = fingerprint()
        rebuild()
    }

    private fun rebuild() {
        val sites = currentSites()
        binding.emptyHint.visibility =
            if (query.isNullOrBlank() || sites.isEmpty()) View.VISIBLE else View.GONE

        adapter?.let {
            it.update(sites, query)
            binding.pager.adapter = it
        } ?: run {
            val newAdapter = SitePageAdapter(sites, query)
            adapter = newAdapter
            binding.pager.adapter = newAdapter
        }
        binding.pager.offscreenPageLimit = 3

        mediator?.detach()
        mediator = TabLayoutMediator(binding.tabLayout, binding.pager) { tab, position ->
            tab.text = sites.getOrNull(position)?.name ?: ""
        }
        mediator?.attach()
    }

    private fun doSearch() {
        val q = binding.queryInput.text?.toString()?.trim().orEmpty()
        if (q.isNotEmpty()) {
            query = q
            forceRebuild()
        }
    }

    private fun pagerCurrentWebView(): WebView? {
        val pager = binding.pager
        if (pager.adapter == null) return null
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return null
        return recycler.getChildAt(pager.currentItem) as? WebView
    }

    private fun openInBrowser() {
        val url = pagerCurrentWebView()?.url?.takeIf { it.startsWith("http") }
            ?: currentSites().firstOrNull()?.let { site ->
                query?.let { Sites.searchUrl(site, it) }
            }
        if (url == null) {
            Toast.makeText(requireContext(), R.string.search_nothing_open, Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}