package io.averkhogliad.ai.challenge.week1.domain.telemetry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class TokenTelemetryTest {

    private val sampleUsage = TokenUsage(100, 50, 150)

    @Test
    fun `should create valid TokenTelemetry`() {
        val telemetry = TokenTelemetry(
            stepUsage = sampleUsage,
            cumulativeUsage = sampleUsage,
            dialogHistoryTokens = 200,
            contextWindowLimit = 8192
        )
        assertEquals(sampleUsage, telemetry.stepUsage)
        assertEquals(sampleUsage, telemetry.cumulativeUsage)
        assertEquals(200, telemetry.dialogHistoryTokens)
        assertEquals(8192, telemetry.contextWindowLimit)
    }

    @Test
    fun `should throw when dialogHistoryTokens is negative`() {
        assertThrows<IllegalArgumentException> {
            TokenTelemetry(
                stepUsage = sampleUsage,
                cumulativeUsage = sampleUsage,
                dialogHistoryTokens = -1
            )
        }
    }

    @Test
    fun `isContextOverflow should be false when under limit`() {
        val telemetry = TokenTelemetry(
            stepUsage = sampleUsage,
            cumulativeUsage = sampleUsage,
            dialogHistoryTokens = 0,
            contextWindowLimit = 8192
        )
        assertFalse(telemetry.isContextOverflow)
    }

    @Test
    fun `isContextOverflow should be true when over limit`() {
        val telemetry = TokenTelemetry(
            stepUsage = sampleUsage,
            cumulativeUsage = TokenUsage(10000, 5000, 15000),
            dialogHistoryTokens = 0,
            contextWindowLimit = 8192
        )
        assertTrue(telemetry.isContextOverflow)
    }

    @Test
    fun `isContextOverflow should be false when contextWindowLimit is null`() {
        val telemetry = TokenTelemetry(
            stepUsage = sampleUsage,
            cumulativeUsage = TokenUsage(10000, 5000, 15000),
            dialogHistoryTokens = 0,
            contextWindowLimit = null
        )
        assertFalse(telemetry.isContextOverflow)
    }

    @Test
    fun `contextUtilizationPercent should calculate correctly`() {
        val telemetry = TokenTelemetry(
            stepUsage = sampleUsage,
            cumulativeUsage = TokenUsage(4096, 0, 4096),
            dialogHistoryTokens = 0,
            contextWindowLimit = 8192
        )
        assertNotNull(telemetry.contextUtilizationPercent)
        assertEquals(50.0, telemetry.contextUtilizationPercent!!, 0.01)
    }

    @Test
    fun `contextUtilizationPercent should be null when contextWindowLimit is null`() {
        val telemetry = TokenTelemetry(
            stepUsage = sampleUsage,
            cumulativeUsage = sampleUsage,
            dialogHistoryTokens = 0,
            contextWindowLimit = null
        )
        assertNull(telemetry.contextUtilizationPercent)
    }

    @Test
    fun `contextUtilizationPercent should be null when contextWindowLimit is zero`() {
        val telemetry = TokenTelemetry(
            stepUsage = sampleUsage,
            cumulativeUsage = sampleUsage,
            dialogHistoryTokens = 0,
            contextWindowLimit = 0
        )
        assertNull(telemetry.contextUtilizationPercent)
    }

    @Test
    fun `aggregate should return initial when previous is null`() {
        val result = TokenTelemetry.aggregate(
            previousTelemetry = null,
            currentStepUsage = sampleUsage,
            dialogHistoryTokens = 100
        )
        assertEquals(sampleUsage, result.stepUsage)
        assertEquals(sampleUsage, result.cumulativeUsage)
        assertEquals(100, result.dialogHistoryTokens)
    }

    @Test
    fun `aggregate should accumulate cumulative usage`() {
        val first = TokenTelemetry.aggregate(
            previousTelemetry = null,
            currentStepUsage = TokenUsage(100, 50, 150),
            dialogHistoryTokens = 0
        )
        val second = TokenTelemetry.aggregate(
            previousTelemetry = first,
            currentStepUsage = TokenUsage(50, 25, 75),
            dialogHistoryTokens = 0
        )
        assertEquals(75, second.stepUsage.totalTokens)
        assertEquals(225, second.cumulativeUsage.totalTokens)
        assertEquals(150, second.cumulativeUsage.promptTokens)
        assertEquals(75, second.cumulativeUsage.completionTokens)
    }

    @Test
    fun `aggregate should propagate contextWindowLimit`() {
        val result = TokenTelemetry.aggregate(
            previousTelemetry = null,
            currentStepUsage = sampleUsage,
            dialogHistoryTokens = 0,
            contextWindowLimit = 8192
        )
        assertEquals(8192, result.contextWindowLimit)
    }

    @Test
    fun `aggregate should propagate costEstimate`() {
        val cost = CostEstimate(0.0001, 0.0002, 0.01, 0.01, 0.02)
        val result = TokenTelemetry.aggregate(
            previousTelemetry = null,
            currentStepUsage = sampleUsage,
            dialogHistoryTokens = 0,
            costEstimate = cost
        )
        assertNotNull(result.costEstimate)
        assertEquals(cost, result.costEstimate)
    }

    @Test
    fun `initial should create first-step telemetry`() {
        val result = TokenTelemetry.initial(
            stepUsage = sampleUsage,
            dialogHistoryTokens = 200,
            contextWindowLimit = 8192
        )
        assertEquals(sampleUsage, result.stepUsage)
        assertEquals(sampleUsage, result.cumulativeUsage)
        assertEquals(200, result.dialogHistoryTokens)
        assertEquals(8192, result.contextWindowLimit)
    }
}
