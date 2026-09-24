package io.searchvpn.app.vpn

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.BadConfigException
import com.wireguard.config.Config
import io.searchvpn.app.App
import io.searchvpn.app.R
import io.searchvpn.app.data.ConfigStore
import io.searchvpn.app.data.VpnServerPreset
import io.searchvpn.app.databinding.FragmentVpnBinding
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class VpnFragment : Fragment() {

    private var _binding: FragmentVpnBinding? = null
    private val binding get() = _binding!!

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            connect(silent = false)
        } else {
            toast(R.string.vpn_consent_denied)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) importConfig(uri)
    }

    private val controller: VpnController
        get() = (requireActivity().application as App).vpnController

    private var connecting = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVpnBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Automatically ensure user's WireGuard configuration is loaded
        if (!ConfigStore.hasConfig(requireContext())) {
            ConfigStore.importUserConfig(requireContext())
        }

        binding.importButton.setOnClickListener {
            importLauncher.launch(arrayOf("*/*"))
        }

        binding.pasteConfigButton.setOnClickListener {
            showPasteConfigDialog()
        }

        binding.restoreDefaultButton.setOnClickListener {
            val wasConnected = controller.activeState() == Tunnel.State.UP
            ConfigStore.importUserConfig(requireContext())
            render()
            toast("Reloaded ProtonVPN US-FREE#112 config")
            if (wasConnected) {
                disconnectAndReconnect()
            }
        }

        binding.presetUsButton.setOnClickListener { selectPreset(ConfigStore.SERVER_PRESETS[0]) }
        binding.presetNlButton.setOnClickListener { selectPreset(ConfigStore.SERVER_PRESETS[1]) }
        binding.presetJpButton.setOnClickListener { selectPreset(ConfigStore.SERVER_PRESETS[2]) }

        binding.checkIpButton.setOnClickListener { checkPublicIp() }

        binding.connectButton.setOnClickListener { onConnectClicked() }
        binding.clearButton.setOnClickListener {
            if (controller.activeState() == Tunnel.State.UP) {
                controller.stop()
            }
            ConfigStore.clearConfig(requireContext())
            render()
            toast(R.string.vpn_config_cleared)
        }
        binding.autoSwitch.isChecked = ConfigStore.autoConnect(requireContext())
        binding.autoSwitch.setOnCheckedChangeListener { _, checked ->
            ConfigStore.setAutoConnect(requireContext(), checked)
        }
        render()
        autoConnectIfEnabled()
    }

    override fun onStart() {
        super.onStart()
        controller.stateChangeListener = { state ->
            requireActivity().runOnUiThread { render(state) }
        }
        render()
    }

    override fun onStop() {
        super.onStop()
        controller.stateChangeListener = null
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun selectPreset(preset: VpnServerPreset) {
        val wasConnected = controller.activeState() == Tunnel.State.UP
        ConfigStore.saveConfig(requireContext(), preset.name, preset.configText)
        render()
        toast("${preset.flag} Loaded ${preset.name}")
        if (wasConnected) {
            disconnectAndReconnect()
        }
    }

    private fun disconnectAndReconnect() {
        lifecycleScope.launch {
            controller.stop()
            connect(silent = false)
        }
    }

    private fun checkPublicIp() {
        binding.ipStatusText.setText(R.string.checking_ip)
        binding.checkIpButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchIpDetails()
            }
            binding.checkIpButton.isEnabled = true
            if (result != null) {
                val isConnected = controller.activeState() == Tunnel.State.UP
                val badge = if (isConnected) " [🔒 Protected by VPN]" else " [⚠️ Real IP Visible]"
                binding.ipStatusText.text = "IP: ${result.first} (${result.second})$badge"
            } else {
                binding.ipStatusText.text = "Could not reach IP service. Check internet connection."
            }
        }
    }

    private fun fetchIpDetails(): Pair<String, String>? {
        return try {
            val url = URL("https://ipapi.co/json/")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            conn.setRequestProperty("User-Agent", "SearchVPN/1.0")
            if (conn.responseCode == 200) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(text)
                val ip = json.optString("ip", "Unknown IP")
                val country = json.optString("country_name", json.optString("country", "Unknown Location"))
                Pair(ip, country)
            } else {
                // Fallback to simple ipify
                val fallbackUrl = URL("https://api.ipify.org")
                val fConn = fallbackUrl.openConnection() as HttpURLConnection
                fConn.connectTimeout = 5000
                fConn.readTimeout = 5000
                val ip = fConn.inputStream.bufferedReader().use { it.readText().trim() }
                Pair(ip, "Internet")
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun autoConnectIfEnabled() {
        val context = requireContext()
        if (!ConfigStore.autoConnect(context)) return
        if (!ConfigStore.hasConfig(context)) return
        if (controller.activeState() == Tunnel.State.UP) return
        if (controller.needsConsent()) {
            return
        }
        connect(silent = true)
    }

    private fun onConnectClicked() {
        if (connecting) return
        if (controller.activeState() == Tunnel.State.UP) {
            disconnect()
        } else {
            requestConnect()
        }
    }

    private fun requestConnect() {
        if (!ConfigStore.hasConfig(requireContext())) {
            toast(R.string.vpn_import_first)
            return
        }
        val intent: Intent? = VpnService.prepare(requireContext())
        if (intent != null) {
            consentLauncher.launch(intent)
        } else {
            connect(silent = false)
        }
    }

    private fun connect(silent: Boolean) {
        if (connecting) return
        val text = ConfigStore.getConfig(requireContext()) ?: return
        connecting = true
        binding.progress.visibility = View.VISIBLE
        binding.connectButton.isEnabled = false
        binding.status.setText(R.string.vpn_status_connecting)
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { controller.start(text) }
            connecting = false
            binding.progress.visibility = View.GONE
            binding.connectButton.isEnabled = true
            if (!silent) {
                val err = controller.lastError
                if (err != null) toast(err) else toast(R.string.vpn_connected)
            }
            render(state)
        }
    }

    private fun disconnect() {
        binding.progress.visibility = View.VISIBLE
        binding.connectButton.isEnabled = false
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) { controller.stop() }
            binding.progress.visibility = View.GONE
            binding.connectButton.isEnabled = true
            toast(R.string.vpn_disconnected)
            render(state)
        }
    }

    private fun importConfig(uri: Uri) {
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    requireContext().contentResolver.openInputStream(uri)
                        ?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text.isNullOrBlank()) {
                toast(R.string.vpn_read_failed)
                return@launch
            }
            try {
                val config = Config.parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
                val name = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringBeforeLast('.')
                    ?.ifBlank { "tunnel" }
                    ?: "tunnel"
                ConfigStore.saveConfig(requireContext(), name, config.toWgQuickString())
                toast(getString(R.string.vpn_imported, name))
                render()
            } catch (e: BadConfigException) {
                toast(getString(R.string.vpn_invalid_config, e.reason))
            } catch (e: Exception) {
                toast(R.string.vpn_import_failed)
            }
        }
    }

    private fun showPasteConfigDialog() {
        val context = requireContext()
        val currentText = ConfigStore.getConfig(context) ?: ConfigStore.DEFAULT_VPN_CONFIG
        val input = TextInputEditText(context).apply {
            setText(currentText)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            setPadding(32, 24, 32, 24)
            hint = "[Interface]\nPrivateKey = ...\n\n[Peer]\nPublicKey = ..."
            isSingleLine = false
            minLines = 8
            maxLines = 14
        }
        val container = FrameLayout(context).apply {
            setPadding(32, 16, 32, 8)
            addView(input)
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("Paste WireGuard Config")
            .setMessage("Paste or edit your WireGuard .conf configuration text:")
            .setView(container)
            .setPositiveButton("Import & Save") { _, _ ->
                val rawText = input.text?.toString()?.trim().orEmpty()
                if (rawText.isBlank()) {
                    toast("Configuration cannot be empty")
                    return@setPositiveButton
                }
                try {
                    val config = Config.parse(ByteArrayInputStream(rawText.toByteArray(Charsets.UTF_8)))
                    val name = "ProtonVPN (Imported)"
                    ConfigStore.saveConfig(context, name, config.toWgQuickString())
                    toast("Imported WireGuard configuration successfully")
                    render()
                } catch (e: BadConfigException) {
                    toast(getString(R.string.vpn_invalid_config, e.reason))
                } catch (e: Exception) {
                    toast("Failed to parse config: ${e.message}")
                }
            }
            .setNeutralButton("Load US-FREE#112") { _, _ ->
                input.setText(ConfigStore.DEFAULT_VPN_CONFIG)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun render(state: Tunnel.State = controller.activeState()) {
        if (_binding == null) return
        val connected = state == Tunnel.State.UP
        binding.status.setText(
            if (connected) R.string.vpn_status_connected else R.string.vpn_status_disconnected,
        )
        binding.connectButton.setText(if (connected) R.string.vpn_disconnect else R.string.vpn_connect)
        binding.connectButton.isEnabled = !connecting

        val currentName = ConfigStore.getConfigName(requireContext())
        binding.presetUsButton.isSelected = currentName.contains("US-FREE")
        binding.presetNlButton.isSelected = currentName.contains("NL-FREE")
        binding.presetJpButton.isSelected = currentName.contains("JP-FREE")

        val text = ConfigStore.getConfig(requireContext())
        if (text == null) {
            binding.configSummary.setText(R.string.vpn_no_config)
            binding.connectButton.isEnabled = false
            return
        }
        val summary = controller.describe(text)
        if (summary != null) {
            binding.configSummary.text = getString(
                R.string.vpn_config_summary,
                currentName,
                summary.endpoint,
                summary.addresses,
                summary.dns,
            )
        }
    }

    private fun toast(msg: CharSequence) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
    }

    private fun toast(resId: Int) {
        Toast.makeText(requireContext(), resId, Toast.LENGTH_LONG).show()
    }
}
