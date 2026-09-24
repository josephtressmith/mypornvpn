package io.searchvpn.app.search

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.recyclerview.widget.RecyclerView
import io.searchvpn.app.data.AdBlockManager
import io.searchvpn.app.data.ConfigStore
import io.searchvpn.app.data.SearchSite
import io.searchvpn.app.data.Sites

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

class SitePageAdapter(
    private var sites: List<SearchSite>,
    private var query: String?,
) : RecyclerView.Adapter<SitePageAdapter.SiteViewHolder>() {

    private val activeWebViews = mutableMapOf<Int, WebView>()
    private val detectedMediaUrls = mutableMapOf<Int, String>()

    class SiteViewHolder(val webView: WebView) : RecyclerView.ViewHolder(webView)

    fun getWebView(position: Int): WebView? = activeWebViews[position]

    fun getDetectedStream(position: Int): String? = detectedMediaUrls[position]

    fun canGoBack(position: Int): Boolean = activeWebViews[position]?.canGoBack() == true

    fun goBack(position: Int) {
        activeWebViews[position]?.goBack()
    }

    fun canGoForward(position: Int): Boolean = activeWebViews[position]?.canGoForward() == true

    fun goForward(position: Int) {
        activeWebViews[position]?.goForward()
    }

    fun reload(position: Int) {
        activeWebViews[position]?.reload()
    }

    fun update(sites: List<SearchSite>, query: String?) {
        this.sites = sites
        this.query = query
        for ((_, wv) in activeWebViews) {
            try {
                wv.stopLoading()
            } catch (_: Throwable) {}
        }
        activeWebViews.clear()
        detectedMediaUrls.clear()
        notifyDataSetChanged()
    }

    private fun currentUrl(position: Int): String? {
        val site = sites.getOrNull(position) ?: return null
        val q = query?.trim().orEmpty()
        return if (q.isEmpty()) {
            site.searchTemplate.substringBefore("?")
        } else {
            Sites.searchUrl(site, q)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SiteViewHolder {
        val webView = WebView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Enable hardware acceleration with safe fallback for virtual/Mesa graphics
            try {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
            } catch (_: Throwable) {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
        }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = false
            blockNetworkImage = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            if (ConfigStore.desktopMode(webView.context)) {
                userAgentString = DESKTOP_UA
            }
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(webView, true)
        }
        webView.webChromeClient = WebChromeClient()
        return SiteViewHolder(webView)
    }

    override fun getItemCount(): Int = if (query.isNullOrEmpty()) 0 else sites.size

    override fun onBindViewHolder(holder: SiteViewHolder, position: Int) {
        activeWebViews[position] = holder.webView
        holder.webView.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                // Prevent crash if Mesa graphics driver or WebView renderer terminates
                val pos = holder.bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    activeWebViews.remove(pos)
                }
                view?.let { wv ->
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    try {
                        wv.destroy()
                    } catch (_: Throwable) {}
                }
                return true
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString()
                if (reqUrl != null) {
                    val ctx = view?.context
                    if (ctx != null && ConfigStore.adBlockEnabled(ctx)) {
                        if (AdBlockManager.isAdUrl(reqUrl)) {
                            return AdBlockManager.createEmptyResponse()
                        }
                    }

                    val lower = reqUrl.lowercase()
                    if (lower.endsWith(".mp4") || lower.contains(".mp4?") ||
                        lower.endsWith(".webm") || lower.contains(".webm?") ||
                        lower.contains("videoplayback") || lower.contains("video_url")
                    ) {
                        detectedMediaUrls[position] = reqUrl
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                val ctx = view?.context
                if (ctx != null && ConfigStore.adBlockEnabled(ctx) && AdBlockManager.isAdUrl(url)) {
                    return true // Block navigation to known ad/popup landing page
                }
                return false
            }
        }

        val url = currentUrl(position) ?: return
        val loaded = holder.webView.tag as? String
        if (loaded != url) {
            holder.webView.tag = url
            holder.webView.loadUrl(url)
        }
    }

    override fun onViewRecycled(holder: SiteViewHolder) {
        super.onViewRecycled(holder)
        val pos = holder.bindingAdapterPosition
        if (pos != RecyclerView.NO_POSITION) {
            activeWebViews.remove(pos)
        }
        try {
            holder.webView.stopLoading()
        } catch (_: Throwable) {}
    }
}
