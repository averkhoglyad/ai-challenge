package io.averkhogliad.ai.challenge.week1.domain

import io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TaskResultTokenUsageTest {

    @Test
    fun `Success should auto-extract tokenUsage from metadata`() {
        val metadata = mapOf<String, Any>(
            "promptTokens" to 100,
            "completionTokens" to 50,
            "totalTokens" to 150
        )
        val result = TaskResult.Success("test content", metadata)
        assertNotNull(result.tokenUsage)
        assertEquals(100, result.tokenUsage?.promptTokens)
        assertEquals(50, result.tokenUsage?.completionTokens)
        assertEquals(150, result.tokenUsage?.totalTokens)
    }

    @Test
    fun `Success should have null tokenUsage when metadata lacks token info`() {
        val result = TaskResult.Success("test content", emptyMap())
        assertNull(result.tokenUsage)
    }

    @Test
    fun `Success should have null tokenUsage when metadata has incomplete token info`() {
        val metadata = mapOf<String, Any>("promptTokens" to 100)
        val result = TaskResult.Success("test content", metadata)
        assertNull(result.tokenUsage)
    }

    @Test
    fun `Error can have null tokenUsage`() {
        val result = TaskResult.Error("error message")
        assertNull(result.tokenUsage)
    }

    @Test
    fun `Error can have tokenUsage`() {
        val usage = TokenUsage(10, 0, 10)
        val result = TaskResult.Error("error message", tokenUsage = usage)
        assertNotNull(result.tokenUsage)
        assertEquals(10, result.tokenUsage?.totalTokens)
    }

    @Test
    fun `Partial can have null tokenUsage`() {
        val result = TaskResult.Partial("partial content", 0.5)
        assertNull(result.tokenUsage)
    }

    @Test
    fun `Partial can have tokenUsage`() {
        val usage = TokenUsage(20, 10, 30)
        val result = TaskResult.Partial("partial content", 0.5, tokenUsage = usage)
        assertNotNull(result.tokenUsage)
        assertEquals(30, result.tokenUsage?.totalTokens)
    }

    @Test
    fun `Success copy should preserve tokenUsage override`() {
        val metadata = mapOf<String, Any>(
            "promptTokens" to 100,
            "completionTokens" to 50,
            "totalTokens" to 150
        )
        val result = TaskResult.Success("test", metadata)
        val overridden = result.copy(tokenUsage = TokenUsage(1, 2, 3))
        assertEquals(3, overridden.tokenUsage?.totalTokens)
    }
}
