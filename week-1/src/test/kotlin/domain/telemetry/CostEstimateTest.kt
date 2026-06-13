package io.averkhogliad.ai.challenge.week1.domain.telemetry

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class CostEstimateTest {

    @Test
    fun `should create valid CostEstimate`() {
        val estimate = CostEstimate(
            inputCostPerToken = 0.0001,
            outputCostPerToken = 0.0002,
            inputCost = 0.01,
            outputCost = 0.01,
            totalCost = 0.02
        )
        assertEquals(0.0001, estimate.inputCostPerToken)
        assertEquals(0.0002, estimate.outputCostPerToken)
        assertEquals(0.01, estimate.inputCost)
        assertEquals(0.01, estimate.outputCost)
        assertEquals(0.02, estimate.totalCost)
    }

    @Test
    fun `should throw when inputCostPerToken is negative`() {
        assertThrows<IllegalArgumentException> {
            CostEstimate(
                inputCostPerToken = -0.1,
                outputCostPerToken = 0.1,
                inputCost = 0.0,
                outputCost = 0.0,
                totalCost = 0.0
            )
        }
    }

    @Test
    fun `should throw when outputCostPerToken is negative`() {
        assertThrows<IllegalArgumentException> {
            CostEstimate(
                inputCostPerToken = 0.1,
                outputCostPerToken = -0.1,
                inputCost = 0.0,
                outputCost = 0.0,
                totalCost = 0.0
            )
        }
    }

    @Test
    fun `should throw when inputCost is negative`() {
        assertThrows<IllegalArgumentException> {
            CostEstimate(
                inputCostPerToken = 0.1,
                outputCostPerToken = 0.1,
                inputCost = -0.1,
                outputCost = 0.0,
                totalCost = 0.0
            )
        }
    }

    @Test
    fun `should throw when outputCost is negative`() {
        assertThrows<IllegalArgumentException> {
            CostEstimate(
                inputCostPerToken = 0.1,
                outputCostPerToken = 0.1,
                inputCost = 0.0,
                outputCost = -0.1,
                totalCost = 0.0
            )
        }
    }

    @Test
    fun `should throw when totalCost is negative`() {
        assertThrows<IllegalArgumentException> {
            CostEstimate(
                inputCostPerToken = 0.1,
                outputCostPerToken = 0.1,
                inputCost = 0.0,
                outputCost = 0.0,
                totalCost = -0.1
            )
        }
    }

    @Test
    fun `calculate should return correct cost for usage`() {
        val usage = TokenUsage(100, 50, 150)
        val estimate = CostEstimate.calculate(
            usage = usage,
            inputCostPerToken = 0.001,
            outputCostPerToken = 0.002
        )
        assertEquals(0.001, estimate.inputCostPerToken)
        assertEquals(0.002, estimate.outputCostPerToken)
        assertEquals(0.1, estimate.inputCost)
        assertEquals(0.1, estimate.outputCost)
        assertEquals(0.2, estimate.totalCost)
    }

    @Test
    fun `calculate should return zero cost for zero usage`() {
        val usage = TokenUsage(0, 0, 0)
        val estimate = CostEstimate.calculate(
            usage = usage,
            inputCostPerToken = 0.001,
            outputCostPerToken = 0.002
        )
        assertEquals(0.0, estimate.inputCost)
        assertEquals(0.0, estimate.outputCost)
        assertEquals(0.0, estimate.totalCost)
    }

    @Test
    fun `calculate should throw when inputCostPerToken is negative`() {
        val usage = TokenUsage(100, 50, 150)
        assertThrows<IllegalArgumentException> {
            CostEstimate.calculate(usage, -0.001, 0.002)
        }
    }

    @Test
    fun `calculate should throw when outputCostPerToken is negative`() {
        val usage = TokenUsage(100, 50, 150)
        assertThrows<IllegalArgumentException> {
            CostEstimate.calculate(usage, 0.001, -0.002)
        }
    }

    @Test
    fun `calculateCumulative should aggregate multiple usages`() {
        val usages = listOf(
            TokenUsage(100, 50, 150),
            TokenUsage(200, 100, 300),
            TokenUsage(300, 150, 450)
        )
        val estimate = CostEstimate.calculateCumulative(
            usages = usages,
            inputCostPerToken = 0.001,
            outputCostPerToken = 0.002
        )
        // prompt: 600 * 0.001 = 0.6
        // completion: 300 * 0.002 = 0.6
        assertEquals(0.6, estimate.inputCost)
        assertEquals(0.6, estimate.outputCost)
        assertEquals(1.2, estimate.totalCost)
    }

    @Test
    fun `calculateCumulative should return zero for empty list`() {
        val estimate = CostEstimate.calculateCumulative(
            usages = emptyList(),
            inputCostPerToken = 0.001,
            outputCostPerToken = 0.002
        )
        assertEquals(0.0, estimate.inputCost)
        assertEquals(0.0, estimate.outputCost)
        assertEquals(0.0, estimate.totalCost)
    }
}
