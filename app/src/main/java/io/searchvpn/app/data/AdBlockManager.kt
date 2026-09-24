package io.searchvpn.app.data

import android.net.Uri
import java.io.ByteArrayInputStream
import android.webkit.WebResourceResponse

object AdBlockManager {

    private val BLOCKED_DOMAINS = hashSetOf(
        "trafficjunky.com",
        "trafficjunky.net",
        "exoclick.com",
        "juicyads.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adspyglass.com",
        "ero-advertising.com",
        "realsrv.com",
        "tsyndicate.com",
        "bongacams.com",
        "chaturbate.com/affiliates",
        "stripchat.com/affiliates",
        "adx.com",
        "doubleclick.net",
        "google-analytics.com",
        "googlesyndication.com",
        "adnxs.com",
        "onclickpredictiv.com",
        "outbrain.com",
        "taboola.com",
        "tsyndicate.com",
        "rtk.io",
        "adform.net"
    )

    fun isAdUrl(url: String?): Boolean {
        if (url == null) return false
        val host = try {
            Uri.parse(url).host?.lowercase() ?: return false
        } catch (_: Exception) {
            return false
        }

        for (domain in BLOCKED_DOMAINS) {
            if (host == domain || host.endsWith(".$domain")) {
                return true
            }
        }
        return false
    }

    fun createEmptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            200,
            "OK",
            mapOf("Access-Control-Allow-Origin" to "*"),
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
