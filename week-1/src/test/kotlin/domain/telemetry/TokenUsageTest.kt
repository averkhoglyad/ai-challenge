package io.averkhogliad.ai.challenge.week1.domain.telemetry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TokenUsageTest {

    @Test
    fun `should create valid TokenUsage`() {
        val usage = TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 150)
        assertEquals(100, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(150, usage.totalTokens)
    }

    @Test
    fun `should throw when promptTokens is negative`() {
        assertThrows<IllegalArgumentException> {
            TokenUsage(promptTokens = -1, completionTokens = 0, totalTokens = -1)
        }
    }

    @Test
    fun `should throw when completionTokens is negative`() {
        assertThrows<IllegalArgumentException> {
            TokenUsage(promptTokens = 0, completionTokens = -1, totalTokens = -1)
        }
    }

    @Test
    fun `should throw when totalTokens does not equal sum of prompt and completion`() {
        assertThrows<IllegalArgumentException> {
            TokenUsage(promptTokens = 100, completionTokens = 50, totalTokens = 200)
        }
    }

    @Test
    fun `should create from metadata successfully`() {
        val metadata = mapOf(
            "promptTokens" to 100,
            "completionTokens" to 50,
            "totalTokens" to 150
        )
        val usage = TokenUsage.fromMetadata(metadata)
        assertNotNull(usage)
        assertEquals(100, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(150, usage.totalTokens)
    }

    @Test
    fun `should return null when metadata is missing promptTokens`() {
        val metadata = mapOf(
            "completionTokens" to 50,
            "totalTokens" to 150
        )
        val usage = TokenUsage.fromMetadata(metadata)
        assertNull(usage)
    }

    @Test
    fun `should return null when metadata is missing completionTokens`() {
        val metadata = mapOf(
            "promptTokens" to 100,
            "totalTokens" to 150
        )
        val usage = TokenUsage.fromMetadata(metadata)
        assertNull(usage)
    }

    @Test
    fun `should return null when metadata is empty`() {
        val usage = TokenUsage.fromMetadata(emptyMap())
        assertNull(usage)
    }

    @Test
    fun `should create promptOnly with correct values`() {
        val usage = TokenUsage.promptOnly(100)
        assertEquals(100, usage.promptTokens)
        assertEquals(0, usage.completionTokens)
        assertEquals(100, usage.totalTokens)
    }

    @Test
    fun `should create promptOnly with zero tokens`() {
        val usage = TokenUsage.promptOnly(0)
        assertEquals(0, usage.promptTokens)
        assertEquals(0, usage.completionTokens)
        assertEquals(0, usage.totalTokens)
    }

    @Test
    fun `should create completionOnly with correct values`() {
        val usage = TokenUsage.completionOnly(50)
        assertEquals(0, usage.promptTokens)
        assertEquals(50, usage.completionTokens)
        assertEquals(50, usage.totalTokens)
    }
}
