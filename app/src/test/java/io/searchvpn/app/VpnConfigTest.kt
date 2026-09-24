package io.searchvpn.app

import com.wireguard.config.Config
import io.searchvpn.app.data.ConfigStore
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class VpnConfigTest {

    @Test
    fun testUserProtonVpnConfigParsesCorrectly() {
        val configText = ConfigStore.DEFAULT_VPN_CONFIG
        val stream = ByteArrayInputStream(configText.toByteArray(Charsets.UTF_8))
        val config = Config.parse(stream)

        assertNotNull(config)
        assertNotNull(config.`interface`)
        assertEquals(1, config.peers.size)

        val peer = config.peers[0]
        assertNotNull(peer.endpoint.orElse(null))
        assertEquals("149.34.251.138", peer.endpoint.get().host)
        assertEquals(51820, peer.endpoint.get().port)
    }
}
