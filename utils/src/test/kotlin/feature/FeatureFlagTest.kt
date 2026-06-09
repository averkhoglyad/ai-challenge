package io.averkhogliad.ai.challenge.utils.feature

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.config.PropertiesConfig
import java.util.*
import kotlin.test.*

internal class FeatureFlagTest {

    // ── FeatureFlag enum ──────────────────────────────────────────────

    @Test
    fun `FeatureFlag has correct keys`() {
        assertEquals("feature.enable-decompose-gui", FeatureFlag.ENABLE_DECOMPOSE_GUI.key)
    }

    @Test
    fun `FeatureFlag defaults are false for all flags`() {
        for (flag in FeatureFlag.entries) {
            assertFalse(
                flag.defaultValue,
                "Flag ${flag.name} should default to false for safe rollout"
            )
        }
    }

    @Test
    fun `FeatureFlag has human-readable descriptions`() {
        for (flag in FeatureFlag.entries) {
            assertTrue(
                flag.description.isNotBlank(),
                "Flag ${flag.name} must have a non-blank description"
            )
        }
    }

    // ── ConfigFeatureFlagProvider: defaults ───────────────────────────

    @Test
    fun `ConfigFeatureFlagProvider returns default when key is missing`() {
        val config = emptyConfig()
        val provider = ConfigFeatureFlagProvider(config)

        for (flag in FeatureFlag.entries) {
            assertEquals(
                flag.defaultValue,
                provider.isEnabled(flag),
                "Flag ${flag.name} should return default when key '${flag.key}' is not in config"
            )
        }
    }

    @Test
    fun `ConfigFeatureFlagProvider returns default for empty config`() {
        val provider = ConfigFeatureFlagProvider(emptyConfig())

        assertFalse(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertFalse(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    // ── ConfigFeatureFlagProvider: reading from config ─────────────────

    @Test
    fun `ConfigFeatureFlagProvider reads true from config`() {
        val config = propsConfig("feature.enable-decompose-gui" to "true")
        val provider = ConfigFeatureFlagProvider(config)

        assertTrue(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `ConfigFeatureFlagProvider reads false from config`() {
        val config = propsConfig("feature.enable-decompose-gui" to "false")
        val provider = ConfigFeatureFlagProvider(config)

        assertFalse(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `ConfigFeatureFlagProvider reads TRUE case-insensitively`() {
        val config = propsConfig("feature.enable-decompose-gui" to "TRUE")
        val provider = ConfigFeatureFlagProvider(config)

        assertTrue(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `ConfigFeatureFlagProvider reads FALSE case-insensitively`() {
        val config = propsConfig("feature.enable-decompose-gui" to "FALSE")
        val provider = ConfigFeatureFlagProvider(config)

        assertFalse(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `ConfigFeatureFlagProvider reads True mixed case`() {
        val config = propsConfig("feature.enable-decompose-gui" to "True")
        val provider = ConfigFeatureFlagProvider(config)

        assertTrue(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `ConfigFeatureFlagProvider falls back to default for invalid value`() {
        val config = propsConfig("feature.enable-decompose-gui" to "not-a-boolean")
        val provider = ConfigFeatureFlagProvider(config)

        assertEquals(
            FeatureFlag.ENABLE_DECOMPOSE_GUI.defaultValue,
            provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI)
        )
    }

    @Test
    fun `ConfigFeatureFlagProvider falls back to default for empty string`() {
        val config = propsConfig("feature.enable-decompose-gui" to "")
        val provider = ConfigFeatureFlagProvider(config)

        assertFalse(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `ConfigFeatureFlagProvider handles multiple flags independently`() {
        val config = propsConfig(
            "feature.enable-decompose-gui" to "true",
            "feature.enable-optimized-prompts" to "false",
        )
        val provider = ConfigFeatureFlagProvider(config)

        assertTrue(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertFalse(provider.isEnabled(FeatureFlag.ENABLE_OPTIMIZED_PROMPTS))
    }

    @Test
    fun `ConfigFeatureFlagProvider partial config uses defaults for missing flags`() {
        val config = propsConfig("feature.enable-decompose-gui" to "true")
        val provider = ConfigFeatureFlagProvider(config)

        assertTrue(provider.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertFalse(provider.isEnabled(FeatureFlag.ENABLE_OPTIMIZED_PROMPTS))
    }

    // ── FeatureFlagManager: basic delegation ──────────────────────────

    @Test
    fun `FeatureFlagManager delegates to config when no overrides`() {
        val config = propsConfig("feature.enable-decompose-gui" to "true")
        val manager = FeatureFlagManager(config)

        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_OPTIMIZED_PROMPTS))
    }

    @Test
    fun `FeatureFlagManager uses defaults when no config and no overrides`() {
        val manager = FeatureFlagManager(emptyConfig())

        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    // ── FeatureFlagManager: runtime overrides ─────────────────────────

    @Test
    fun `FeatureFlagManager setOverride overrides config value`() {
        val config = propsConfig("feature.enable-decompose-gui" to "false")
        val manager = FeatureFlagManager(config)

        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager setOverride works without config value`() {
        val manager = FeatureFlagManager(emptyConfig())

        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager setOverride can disable enabled flag`() {
        val config = propsConfig("feature.enable-decompose-gui" to "true")
        val manager = FeatureFlagManager(config)

        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, false)
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager clearOverride restores config value`() {
        val config = propsConfig("feature.enable-decompose-gui" to "true")
        val manager = FeatureFlagManager(config)

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, false)
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.clearOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI)
        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager clearOverride restores default when no config`() {
        val manager = FeatureFlagManager(emptyConfig())

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.clearOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI)
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager clearAllOverrides clears everything`() {
        val config = propsConfig(
            "feature.enable-decompose-gui" to "true",
            "feature.enable-optimized-prompts" to "false",
        )
        val manager = FeatureFlagManager(config)

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, false)
        manager.setOverride(FeatureFlag.ENABLE_OPTIMIZED_PROMPTS, true)

        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_OPTIMIZED_PROMPTS))

        manager.clearAllOverrides()

        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_OPTIMIZED_PROMPTS))
    }

    // ── FeatureFlagManager: hasOverride / getOverrides ────────────────

    @Test
    fun `FeatureFlagManager hasOverride returns false when no override`() {
        val manager = FeatureFlagManager(emptyConfig())

        assertFalse(manager.hasOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager hasOverride returns true after setOverride`() {
        val manager = FeatureFlagManager(emptyConfig())

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        assertTrue(manager.hasOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager hasOverride returns false after clearOverride`() {
        val manager = FeatureFlagManager(emptyConfig())

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        manager.clearOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI)
        assertFalse(manager.hasOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager getOverrides returns current overrides`() {
        val manager = FeatureFlagManager(emptyConfig())

        assertTrue(manager.getOverrides().isEmpty())

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        assertEquals(
            mapOf(FeatureFlag.ENABLE_DECOMPOSE_GUI to true),
            manager.getOverrides()
        )

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, false)
        assertEquals(
            mapOf(
                FeatureFlag.ENABLE_DECOMPOSE_GUI to true,
                FeatureFlag.ENABLE_DECOMPOSE_GUI to false,
            ),
            manager.getOverrides()
        )
    }

    @Test
    fun `FeatureFlagManager getOverrides returns defensive copy`() {
        val manager = FeatureFlagManager(emptyConfig())
        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)

        val overrides = manager.getOverrides()
        // toMap() returns an unmodifiable copy — mutations throw UnsupportedOperationException
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (overrides as MutableMap<FeatureFlag, Boolean>)[FeatureFlag.ENABLE_DECOMPOSE_GUI] = false
        }
    }

    // ── FeatureFlagManager: multiple flags ────────────────────────────

    @Test
    fun `FeatureFlagManager overrides for one flag do not affect others`() {
        val config = propsConfig(
            "feature.enable-decompose-gui" to "false",
            "feature.enable-optimized-prompts" to "false",
        )
        val manager = FeatureFlagManager(config)

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)

        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_OPTIMIZED_PROMPTS))
    }

    @Test
    fun `FeatureFlagManager override priority higher than config`() {
        val config = propsConfig("feature.enable-decompose-gui" to "true")
        val manager = FeatureFlagManager(config)

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, false)

        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    @Test
    fun `FeatureFlagManager toggling same flag multiple times`() {
        val manager = FeatureFlagManager(emptyConfig())

        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, false)
        assertFalse(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))

        manager.setOverride(FeatureFlag.ENABLE_DECOMPOSE_GUI, true)
        assertTrue(manager.isEnabled(FeatureFlag.ENABLE_DECOMPOSE_GUI))
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun propsConfig(vararg pairs: Pair<String, String>): Config {
        val props = Properties()
        pairs.forEach { (k, v) -> props.setProperty(k, v) }
        return PropertiesConfig.fromProperties(props)
    }

    private fun emptyConfig(): Config =
        PropertiesConfig.empty()
}
