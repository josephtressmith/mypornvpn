package io.searchvpn.app

import io.searchvpn.app.theme.ThemeManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePresetsTest {

    @Test
    fun testPresetsAreWellFormed() {
        val presets = ThemeManager.PRESETS
        assertTrue(presets.isNotEmpty())

        val keys = mutableSetOf<String>()
        for (preset in presets) {
            assertTrue(preset.key.isNotBlank())
            assertTrue(preset.name.isNotBlank())
            assertTrue(preset.category.isNotBlank())
            assertTrue(preset.description.isNotBlank())
            assertTrue("Hex should start with #: ${preset.primaryColorHex}", preset.primaryColorHex.startsWith("#"))
            assertTrue("Hex should be 7 chars: ${preset.primaryColorHex}", preset.primaryColorHex.length == 7)
            assertTrue("Key must be unique: ${preset.key}", keys.add(preset.key))
        }

        assertNotNull(presets.find { it.key == "MATRIX" })
        assertNotNull(presets.find { it.key == "BLADERUNNER" })
        assertNotNull(presets.find { it.key == "INTERSTELLAR" })
        assertNotNull(presets.find { it.key == "AURORA" })
    }
}
