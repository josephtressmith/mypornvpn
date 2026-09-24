package io.searchvpn.app.data

import java.net.URLEncoder

data class SearchSite(
    val key: String,
    val name: String,
    val searchTemplate: String,
    val tagline: String = ""
)

/**
 * Search URL templates for adult video sites. Each template must contain exactly one {q}
 * placeholder, which is replaced with the URL-encoded search query. To add more sites,
 * just append a new entry here following the same format.
 */
object Sites {

    val ALL: List<SearchSite> = listOf(
        SearchSite("ph", "Pornhub", "https://www.pornhub.com/video/search?search={q}"),
        SearchSite("xv", "XVideos", "https://www.xvideos.com/?k={q}"),
        SearchSite("xh", "XHamster", "https://xhamster.com/search?q={q}"),
        SearchSite("xn", "XNXX", "https://www.xnxx.com/search/{q}"),
        SearchSite("yp", "YouPorn", "https://www.youporn.com/search/?query={q}"),
        SearchSite("rt", "Redtube", "https://www.redtube.com/?search={q}"),
        SearchSite("sb", "SpankBang", "https://spankbang.com/s/{q}"),
        SearchSite("ep", "EPorner", "https://www.eporner.com/search/{q}/"),
        SearchSite("tx", "Txxx", "https://txxx.com/search/{q}/"),
        SearchSite("hc", "HClips", "https://hclips.com/search/{q}/"),
        SearchSite("ml", "Motherless", "https://motherless.com/term/{q}"),
        SearchSite("sx", "Sxyprn", "https://sxyprn.com/search/{q}"),
        SearchSite("pp", "PornPics", "https://www.pornpics.com/search/?q={q}"),
    )

    fun searchUrl(site: SearchSite, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
        return site.searchTemplate.replace("{q}", encoded)
    }
}