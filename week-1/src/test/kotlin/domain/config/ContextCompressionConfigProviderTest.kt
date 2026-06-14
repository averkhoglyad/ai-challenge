package io.averkhogliad.ai.challenge.week1.domain.config

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextCompressionConfigProviderTest {

    @Test
    fun `get should return initial config`() {
        val initial = ContextCompressionConfig(enabled = true, windowSize = 15, blockSize = 5)
        val provider = ContextCompressionConfigProvider(initial)

        val result = provider.get()

        assertTrue(result.enabled)
        assertEquals(15, result.windowSize)
        assertEquals(5, result.blockSize)
    }

    @Test
    fun `setEnabled should change enabled flag`() {
        val provider = ContextCompressionConfigProvider(ContextCompressionConfig(enabled = false))

        provider.setEnabled(true)
        assertTrue(provider.get().enabled)

        provider.setEnabled(false)
        assertFalse(provider.get().enabled)
    }

    @Test
    fun `setWindowSize should change window size`() {
        val provider = ContextCompressionConfigProvider(
            ContextCompressionConfig(windowSize = 10, blockSize = 5)
        )

        provider.setWindowSize(20)
        assertEquals(20, provider.get().windowSize)
    }

    @Test
    fun `setBlockSize should change block size`() {
        val provider = ContextCompressionConfigProvider(
            ContextCompressionConfig(windowSize = 10, blockSize = 5)
        )

        provider.setBlockSize(8)
        assertEquals(8, provider.get().blockSize)
    }

    @Test
    fun `setWindowSize should throw when size is zero`() {
        val provider = ContextCompressionConfigProvider(ContextCompressionConfig())

        assertThrows<IllegalArgumentException> {
            provider.setWindowSize(0)
        }
    }

    @Test
    fun `setWindowSize should throw when size is negative`() {
        val provider = ContextCompressionConfigProvider(ContextCompressionConfig())

        assertThrows<IllegalArgumentException> {
            provider.setWindowSize(-5)
        }
    }

    @Test
    fun `setBlockSize should throw when size is zero`() {
        val provider = ContextCompressionConfigProvider(ContextCompressionConfig())

        assertThrows<IllegalArgumentException> {
            provider.setBlockSize(0)
        }
    }

    @Test
    fun `setBlockSize should throw when size is negative`() {
        val provider = ContextCompressionConfigProvider(ContextCompressionConfig())

        assertThrows<IllegalArgumentException> {
            provider.setBlockSize(-1)
        }
    }

    @Test
    fun `setBlockSize should throw when size greater than windowSize`() {
        val provider = ContextCompressionConfigProvider(
            ContextCompressionConfig(windowSize = 10, blockSize = 5)
        )

        assertThrows<IllegalArgumentException> {
            provider.setBlockSize(15)
        }
    }

    @Test
    fun `setWindowSize should throw when size less than blockSize`() {
        val provider = ContextCompressionConfigProvider(
            ContextCompressionConfig(windowSize = 10, blockSize = 5)
        )

        assertThrows<IllegalArgumentException> {
            provider.setWindowSize(3)
        }
    }

    @Test
    fun `multiple mutations should preserve other fields`() {
        val provider = ContextCompressionConfigProvider(
            ContextCompressionConfig(enabled = true, windowSize = 10, blockSize = 5, summaryModelId = "gpt-4")
        )

        provider.setEnabled(false)
        provider.setWindowSize(20)
        provider.setBlockSize(10)

        val result = provider.get()
        assertFalse(result.enabled)
        assertEquals(20, result.windowSize)
        assertEquals(10, result.blockSize)
        assertEquals("gpt-4", result.summaryModelId)
    }
}
