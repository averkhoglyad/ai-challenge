package io.averkhogliad.ai.challenge.week3.cli.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit-тесты для модели DebugMode.
 *
 * Проверяют:
 * - Инициализацию по умолчанию (isEnabled = false)
 * - Включение/выключение режима
 * - Переключение (toggle)
 * - Установку явного значения
 */
class DebugModeTest {

    private lateinit var debugMode: DebugMode

    @BeforeEach
    fun setUp() {
        debugMode = DebugMode()
    }

    @Test
    fun `should be disabled by default`() {
        assertFalse(debugMode.isEnabled)
    }

    @Test
    fun `should enable debug mode`() {
        debugMode.enable()
        assertTrue(debugMode.isEnabled)
    }

    @Test
    fun `should disable debug mode`() {
        debugMode.enable()
        debugMode.disable()
        assertFalse(debugMode.isEnabled)
    }

    @Test
    fun `should toggle from disabled to enabled`() {
        debugMode.toggle()
        assertTrue(debugMode.isEnabled)
    }

    @Test
    fun `should toggle from enabled to disabled`() {
        debugMode.enable()
        debugMode.toggle()
        assertFalse(debugMode.isEnabled)
    }

    @Test
    fun `should set enabled to true`() {
        debugMode.setEnabled(true)
        assertTrue(debugMode.isEnabled)
    }

    @Test
    fun `should set enabled to false`() {
        debugMode.enable()
        debugMode.setEnabled(false)
        assertFalse(debugMode.isEnabled)
    }

    @Test
    fun `toString should contain isEnabled value`() {
        val result = debugMode.toString()
        assertTrue(result.contains("isEnabled=false"))

        debugMode.enable()
        val resultEnabled = debugMode.toString()
        assertTrue(resultEnabled.contains("isEnabled=true"))
    }
}
