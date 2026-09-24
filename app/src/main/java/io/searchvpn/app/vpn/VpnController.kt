package io.searchvpn.app.vpn

import android.content.Context
import android.net.VpnService
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

/**
 * Thin wrapper around the official WireGuard tunnel library
 * (com.wireguard.android:tunnel) using GoBackend, which brings up a real
 * userspace WireGuard tunnel via Android's VpnService once the user has
 * granted VPN consent.
 *
 * Only a single tunnel can be active per process, matching GoBackend's design.
 */
class VpnController(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    var stateChangeListener: ((Tunnel.State) -> Unit)? = null

    var lastError: String? = null
        private set

    /** GoBackend is only built on first use; failures are reported, not thrown. */
    private val backend: GoBackend? = try {
        GoBackend(appContext)
    } catch (t: Throwable) {
        lastError = "VPN backend unavailable: ${t.message}"
        null
    }

    private var activeTunnel: SimpleTunnel? = null

    private inner class SimpleTunnel(private val tunnelName: String) : Tunnel {
        override fun getName(): String = tunnelName

        override fun onStateChange(newState: Tunnel.State) {
            stateChangeListener?.invoke(newState)
        }
    }

    /** True if the user has not yet granted VPN consent to this app. */
    fun needsConsent(): Boolean = VpnService.prepare(appContext) != null

    fun activeState(): Tunnel.State {
        val backend = this.backend ?: return Tunnel.State.DOWN
        return activeTunnel?.let { backend.getState(it) } ?: Tunnel.State.DOWN
    }

    /**
     * Bring the tunnel up. Blocks while DNS is resolved and the TUN
     * interface is built, so call from a background thread.
     * Returns the resulting state; check [lastError] on failure.
     */
    fun start(configText: String): Tunnel.State {
        val backend = this.backend
        lastError = null
        if (backend == null) {
            lastError = "VPN backend unavailable on this device"
            return Tunnel.State.DOWN
        }
        try {
            val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
            val tunnel = SimpleTunnel(TUNNEL_NAME)
            activeTunnel = tunnel
            backend.setState(tunnel, Tunnel.State.UP, config)
            return Tunnel.State.UP
        } catch (e: BadConfigException) {
            lastError = "Invalid configuration: ${e.reason}"
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
        }
        return Tunnel.State.DOWN
    }

    /** Tear the tunnel down. Safe to call when nothing is running. */
    fun stop(): Tunnel.State {
        val backend = this.backend
        lastError = null
        val tunnel = activeTunnel
        if (tunnel != null && backend != null) {
            try {
                backend.setState(tunnel, Tunnel.State.DOWN, null)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        return Tunnel.State.DOWN
    }

    /**
     * Human-readable summary of the currently stored config.
     */
    fun describe(configText: String): ConfigSummary? {
        return try {
            val config = Config.parse(ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8)))
            val endpoint = config.peers.firstOrNull()?.endpoint?.orElse(null)?.toString() ?: "unknown"
            val addresses = config.getInterface().getAddresses().joinToString(", ") { it.toString() }
            val dns = config.getInterface().getDnsServers().joinToString(", ") { it.hostAddress ?: "-" }
            ConfigSummary(endpoint, addresses, dns)
        } catch (e: Exception) {
            null
        }
    }

    data class ConfigSummary(val endpoint: String, val addresses: String, val dns: String)

    companion object {
        private const val TUNNEL_NAME = "searchvpn"
    }
}