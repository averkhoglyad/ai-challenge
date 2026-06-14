package io.averkhogliad.ai.challenge.week1.domain.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContextCompressionConfigTest {

    @Test
    fun `should create valid config with default values`() {
        val config = ContextCompressionConfig()
        assertFalse(config.enabled)
        assertEquals(10, config.windowSize)
        assertEquals(5, config.blockSize)
        assertNull(config.summaryModelId)
    }

    @Test
    fun `should create valid config with custom values`() {
        val config = ContextCompressionConfig(
            enabled = true,
            windowSize = 20,
            blockSize = 10,
            summaryModelId = "gpt-4"
        )
        assertTrue(config.enabled)
        assertEquals(20, config.windowSize)
        assertEquals(10, config.blockSize)
        assertEquals("gpt-4", config.summaryModelId)
    }

    @Test
    fun `should throw when windowSize is zero`() {
        assertThrows<IllegalArgumentException> {
            ContextCompressionConfig(windowSize = 0)
        }
    }

    @Test
    fun `should throw when windowSize is negative`() {
        assertThrows<IllegalArgumentException> {
            ContextCompressionConfig(windowSize = -5)
        }
    }

    @Test
    fun `should throw when blockSize is zero`() {
        assertThrows<IllegalArgumentException> {
            ContextCompressionConfig(blockSize = 0)
        }
    }

    @Test
    fun `should throw when blockSize is negative`() {
        assertThrows<IllegalArgumentException> {
            ContextCompressionConfig(blockSize = -1)
        }
    }

    @Test
    fun `should throw when blockSize is greater than windowSize`() {
        assertThrows<IllegalArgumentException> {
            ContextCompressionConfig(windowSize = 5, blockSize = 10)
        }
    }

    @Test
    fun `should allow blockSize equal to windowSize`() {
        val config = ContextCompressionConfig(windowSize = 5, blockSize = 5)
        assertEquals(5, config.windowSize)
        assertEquals(5, config.blockSize)
    }

    @Test
    fun `fromProperties should read all values correctly`() {
        val props = mapOf(
            "context.compression.enabled" to "true",
            "context.compression.window-size" to "15",
            "context.compression.block-size" to "7",
            "context.compression.summary-model-id" to "claude-3"
        )
        val config = ContextCompressionConfig.fromProperties(props)
        assertTrue(config.enabled)
        assertEquals(15, config.windowSize)
        assertEquals(7, config.blockSize)
        assertEquals("claude-3", config.summaryModelId)
    }

    @Test
    fun `fromProperties should use defaults for missing values`() {
        val props = emptyMap<String, String>()
        val config = ContextCompressionConfig.fromProperties(props)
        assertFalse(config.enabled)
        assertEquals(10, config.windowSize)
        assertEquals(5, config.blockSize)
        assertNull(config.summaryModelId)
    }

    @Test
    fun `fromProperties should handle invalid boolean as false`() {
        val props = mapOf("context.compression.enabled" to "invalid")
        val config = ContextCompressionConfig.fromProperties(props)
        assertFalse(config.enabled)
    }

    @Test
    fun `fromProperties should handle invalid int as default`() {
        val props = mapOf(
            "context.compression.window-size" to "invalid",
            "context.compression.block-size" to "invalid"
        )
        val config = ContextCompressionConfig.fromProperties(props)
        assertEquals(10, config.windowSize)
        assertEquals(5, config.blockSize)
    }

    @Test
    fun `fromProperties should throw when blockSize greater than windowSize`() {
        val props = mapOf(
            "context.compression.window-size" to "5",
            "context.compression.block-size" to "10"
        )
        assertThrows<IllegalArgumentException> {
            ContextCompressionConfig.fromProperties(props)
        }
    }
}
