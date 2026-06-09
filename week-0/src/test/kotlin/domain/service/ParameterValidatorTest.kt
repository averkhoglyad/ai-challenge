package io.averkhogliad.ai.challenge.week0.domain.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Unit-тесты для [ParameterValidator].
 *
 * Тестируют чистые функции валидации без зависимостей.
 */
class ParameterValidatorTest {

    // ==================== validateTemperature ====================

    @Test
    fun `validateTemperature returns success for valid values`() {
        assertTrue(ParameterValidator.validateTemperature(0.0).isSuccess)
        assertTrue(ParameterValidator.validateTemperature(0.7).isSuccess)
        assertTrue(ParameterValidator.validateTemperature(1.0).isSuccess)
        assertTrue(ParameterValidator.validateTemperature(2.0).isSuccess)
    }

    @Test
    fun `validateTemperature returns failure for values below range`() {
        val result = ParameterValidator.validateTemperature(-0.1)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
        assertTrue(ex.message!!.contains("0.0..2.0"))
    }

    @Test
    fun `validateTemperature returns failure for values above range`() {
        val result = ParameterValidator.validateTemperature(2.1)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
        assertTrue(ex.message!!.contains("0.0..2.0"))
    }

    @Test
    fun `validateTemperature returns the same value on success`() {
        val result = ParameterValidator.validateTemperature(0.7)
        assertEquals(0.7, result.getOrNull())
    }

    // ==================== validateMaxTokens ====================

    @Test
    fun `validateMaxTokens returns success for valid values`() {
        assertTrue(ParameterValidator.validateMaxTokens(1).isSuccess)
        assertTrue(ParameterValidator.validateMaxTokens(500).isSuccess)
        assertTrue(ParameterValidator.validateMaxTokens(128_000).isSuccess)
    }

    @Test
    fun `validateMaxTokens returns failure for zero`() {
        val result = ParameterValidator.validateMaxTokens(0)
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
        assertTrue(ex.message!!.contains("1..128000"))
    }

    @Test
    fun `validateMaxTokens returns failure for negative values`() {
        val result = ParameterValidator.validateMaxTokens(-1)
        assertTrue(result.isFailure)
    }

    @Test
    fun `validateMaxTokens returns failure for values above range`() {
        val result = ParameterValidator.validateMaxTokens(128_001)
        assertTrue(result.isFailure)
    }

    @Test
    fun `validateMaxTokens returns the same value on success`() {
        val result = ParameterValidator.validateMaxTokens(500)
        assertEquals(500, result.getOrNull())
    }

    // ==================== validateStopSequences ====================

    @Test
    fun `validateStopSequences returns success for empty list`() {
        assertTrue(ParameterValidator.validateStopSequences(emptyList()).isSuccess)
    }

    @Test
    fun `validateStopSequences returns success for up to 4 sequences`() {
        assertTrue(ParameterValidator.validateStopSequences(listOf("END")).isSuccess)
        assertTrue(ParameterValidator.validateStopSequences(listOf("a", "b", "c", "d")).isSuccess)
    }

    @Test
    fun `validateStopSequences returns failure for more than 4 sequences`() {
        val result = ParameterValidator.validateStopSequences(listOf("a", "b", "c", "d", "e"))
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertIs<IllegalArgumentException>(ex)
        assertTrue(ex.message!!.contains("cannot exceed 4"))
    }

    @Test
    fun `validateStopSequences returns the same list on success`() {
        val values = listOf("END", "STOP")
        val result = ParameterValidator.validateStopSequences(values)
        assertEquals(values, result.getOrNull())
    }

    // ==================== validateTemperatures ====================

    @Test
    fun `validateTemperatures returns success for valid list`() {
        val result = ParameterValidator.validateTemperatures(listOf(0.0, 0.7, 2.0))
        assertTrue(result.isSuccess)
        assertEquals(listOf(0.0, 0.7, 2.0), result.getOrNull())
    }

    @Test
    fun `validateTemperatures returns failure for empty list`() {
        val result = ParameterValidator.validateTemperatures(emptyList())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("cannot be empty"))
    }

    @Test
    fun `validateTemperatures returns failure for invalid values`() {
        val result = ParameterValidator.validateTemperatures(listOf(0.7, 3.0))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("invalid"))
    }

    // ==================== validateName ====================

    @Test
    fun `validateName returns success for non-blank name`() {
        assertTrue(ParameterValidator.validateName("Аналитик").isSuccess)
        assertTrue(ParameterValidator.validateName("x").isSuccess)
    }

    @Test
    fun `validateName returns failure for blank name`() {
        assertTrue(ParameterValidator.validateName("  ").isFailure)
        assertTrue(ParameterValidator.validateName("").isFailure)
    }
}
