package io.averkhogliad.ai.challenge.week2.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для enum [CommandStage].
 *
 * Проверяет:
 * - Наличие всех ожидаемых значений (PLANNING, EXECUTION, VALIDATION, DONE, TERMINATED)
 * - Порядок значений (ordinal)
 * - Возможность получения значений по имени
 */
class CommandStageTest {

    @Test
    fun `should have exactly 5 stages`() {
        assertEquals(5, CommandStage.entries.size)
    }

    @Test
    fun `should have PLANNING as first stage`() {
        assertEquals(0, CommandStage.PLANNING.ordinal)
    }

    @Test
    fun `should have EXECUTION as second stage`() {
        assertEquals(1, CommandStage.EXECUTION.ordinal)
    }

    @Test
    fun `should have VALIDATION as third stage`() {
        assertEquals(2, CommandStage.VALIDATION.ordinal)
    }

    @Test
    fun `should have DONE as fourth stage`() {
        assertEquals(3, CommandStage.DONE.ordinal)
    }

    @Test
    fun `should have TERMINATED as fifth stage`() {
        assertEquals(4, CommandStage.TERMINATED.ordinal)
    }

    @Test
    fun `should be able to get stage by name`() {
        assertEquals(CommandStage.PLANNING, CommandStage.valueOf("PLANNING"))
        assertEquals(CommandStage.EXECUTION, CommandStage.valueOf("EXECUTION"))
        assertEquals(CommandStage.VALIDATION, CommandStage.valueOf("VALIDATION"))
        assertEquals(CommandStage.DONE, CommandStage.valueOf("DONE"))
        assertEquals(CommandStage.TERMINATED, CommandStage.valueOf("TERMINATED"))
    }

    @Test
    fun `entries should be in correct order`() {
        val entries = CommandStage.entries
        assertEquals(CommandStage.PLANNING, entries[0])
        assertEquals(CommandStage.EXECUTION, entries[1])
        assertEquals(CommandStage.VALIDATION, entries[2])
        assertEquals(CommandStage.DONE, entries[3])
        assertEquals(CommandStage.TERMINATED, entries[4])
    }

    @Test
    fun `all stages should have non-blank name`() {
        CommandStage.entries.forEach { stage ->
            assertTrue(stage.name.isNotBlank())
        }
    }
}
