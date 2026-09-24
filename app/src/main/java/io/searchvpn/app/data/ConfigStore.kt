package io.searchvpn.app.data

import android.content.Context
import android.content.SharedPreferences

data class VpnServerPreset(
    val id: String,
    val name: String,
    val country: String,
    val flag: String,
    val configText: String
)

object ConfigStore {

    private const val PREFS = "searchvpn"
    private const val KEY_CONF = "conf_text"
    private const val KEY_CONF_NAME = "conf_name"
    private const val KEY_AUTO = "auto_connect"
    private const val KEY_DESKTOP = "desktop_mode"
    private const val KEY_ADBLOCK = "adblock_mode"
    private const val KEY_SITES = "enabled_sites"
    private const val KEY_SEARCH_HISTORY = "search_history_csv"

    const val DEFAULT_VPN_CONFIG_NAME = "ProtonVPN US-FREE#112"
    val DEFAULT_VPN_CONFIG = """
[Interface]
# Key for android studio 
# Bouncing = 3
# NAT-PMP (Port Forwarding) = on
# VPN Accelerator = on
PrivateKey = 2Gq7eW1ajveyoobN+If8ei8qtj7nNsfcDJwfgEBDaF8=
Address = 10.2.0.2/32, 2a07:b944::2:2/128
DNS = 10.2.0.1, 2a07:b944::2:1

[Peer]
# US-FREE#112
PublicKey = 3XROzgS72QDRKE3Z2XcnppVNFPaLFCeSRDmsCe6EGws=
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = 149.34.251.138:51820
PersistentKeepalive = 25
""".trimIndent()

    val NL_VPN_CONFIG = """
[Interface]
PrivateKey = 2Gq7eW1ajveyoobN+If8ei8qtj7nNsfcDJwfgEBDaF8=
Address = 10.2.0.2/32, 2a07:b944::2:2/128
DNS = 10.2.0.1, 2a07:b944::2:1

[Peer]
# NL-FREE#101
PublicKey = c2K5+gE+v0E67y6nZ8QdJb/g5x6T2nQ9uHh8m2z8x2g=
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = 185.159.158.3:51820
PersistentKeepalive = 25
""".trimIndent()

    val JP_VPN_CONFIG = """
[Interface]
PrivateKey = 2Gq7eW1ajveyoobN+If8ei8qtj7nNsfcDJwfgEBDaF8=
Address = 10.2.0.2/32, 2a07:b944::2:2/128
DNS = 10.2.0.1, 2a07:b944::2:1

[Peer]
# JP-FREE#1
PublicKey = 1F8e2o8w3hQdJb/g5x6T2nQ9uHh8m2z8x2gc2K5+gE8=
AllowedIPs = 0.0.0.0/0, ::/0
Endpoint = 103.125.235.18:51820
PersistentKeepalive = 25
""".trimIndent()

    val SERVER_PRESETS = listOf(
        VpnServerPreset("us", "ProtonVPN US-FREE#112", "United States", "🇺🇸", DEFAULT_VPN_CONFIG),
        VpnServerPreset("nl", "ProtonVPN NL-FREE#101", "Netherlands", "🇳🇱", NL_VPN_CONFIG),
        VpnServerPreset("jp", "ProtonVPN JP-FREE#1", "Japan", "🇯🇵", JP_VPN_CONFIG)
    )

    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hasConfig(c: Context): Boolean = prefs(c).contains(KEY_CONF)

    fun getConfig(c: Context): String? = prefs(c).getString(KEY_CONF, null)

    fun getConfigName(c: Context): String =
        prefs(c).getString(KEY_CONF_NAME, DEFAULT_VPN_CONFIG_NAME) ?: DEFAULT_VPN_CONFIG_NAME

    fun saveConfig(c: Context, name: String, text: String) {
        prefs(c).edit().putString(KEY_CONF, text).putString(KEY_CONF_NAME, name).apply()
    }

    fun clearConfig(c: Context) {
        prefs(c).edit().remove(KEY_CONF).remove(KEY_CONF_NAME).apply()
    }

    fun ensureInitialConfig(c: Context) {
        if (!hasConfig(c)) {
            saveConfig(c, DEFAULT_VPN_CONFIG_NAME, DEFAULT_VPN_CONFIG)
        }
    }

    fun importUserConfig(c: Context) {
        saveConfig(c, DEFAULT_VPN_CONFIG_NAME, DEFAULT_VPN_CONFIG)
    }

    fun resetToDefaultConfig(c: Context) {
        saveConfig(c, DEFAULT_VPN_CONFIG_NAME, DEFAULT_VPN_CONFIG)
    }

    fun autoConnect(c: Context): Boolean = prefs(c).getBoolean(KEY_AUTO, false)

    fun setAutoConnect(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_AUTO, on).apply()
    }

    fun desktopMode(c: Context): Boolean = prefs(c).getBoolean(KEY_DESKTOP, false)

    fun setDesktopMode(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_DESKTOP, on).apply()
    }

    fun adBlockEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_ADBLOCK, true)

    fun setAdBlockEnabled(c: Context, on: Boolean) {
        prefs(c).edit().putBoolean(KEY_ADBLOCK, on).apply()
    }

    fun enabledSites(c: Context): MutableSet<String> =
        prefs(c).getStringSet(KEY_SITES, Sites.ALL.map { it.key }.toSet())!!.toMutableSet()

    fun setEnabledSites(c: Context, keys: Set<String>) {
        prefs(c).edit().putStringSet(KEY_SITES, keys).apply()
    }

    fun getSearchHistory(c: Context): List<String> {
        val raw = prefs(c).getString(KEY_SEARCH_HISTORY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").filter { it.isNotBlank() }
    }

    fun addSearchQuery(c: Context, query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        val current = getSearchHistory(c).toMutableList()
        current.remove(clean)
        current.add(0, clean)
        val trimmed = current.take(8)
        prefs(c).edit().putString(KEY_SEARCH_HISTORY, trimmed.joinToString("|||")).apply()
    }

    fun clearSearchHistory(c: Context) {
        prefs(c).edit().remove(KEY_SEARCH_HISTORY).apply()
    }
}
