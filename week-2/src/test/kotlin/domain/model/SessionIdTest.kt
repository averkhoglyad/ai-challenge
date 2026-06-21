package io.averkhogliad.ai.challenge.week2.domain.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Тесты для value object [SessionId].
 */
@DisplayName("SessionId")
class SessionIdTest {

    @Test
    @DisplayName("should create SessionId with valid value")
    fun `should create with valid value`() {
        val id = SessionId("test-session-id")
        assertEquals("test-session-id", id.value)
    }

    @Test
    @DisplayName("should throw exception when value is blank")
    fun `should throw when value is blank`() {
        assertThrows<IllegalArgumentException> {
            SessionId("")
        }
    }

    @Test
    @DisplayName("should throw exception when value is whitespace")
    fun `should throw when value is whitespace`() {
        assertThrows<IllegalArgumentException> {
            SessionId("   ")
        }
    }

    @Test
    @DisplayName("should generate unique SessionId")
    fun `should generate unique id`() {
        val id1 = SessionId.generate()
        val id2 = SessionId.generate()

        assertNotEquals(id1, id2)
        assertTrue(id1.value.isNotBlank())
        assertTrue(id2.value.isNotBlank())
    }

    @Test
    @DisplayName("should be equal when values are equal")
    fun `should be equal when values are equal`() {
        val id1 = SessionId("same-value")
        val id2 = SessionId("same-value")

        assertEquals(id1, id2)
        assertEquals(id1.hashCode(), id2.hashCode())
    }

    @Test
    @DisplayName("should not be equal when values are different")
    fun `should not be equal when values are different`() {
        val id1 = SessionId("value-1")
        val id2 = SessionId("value-2")

        assertNotEquals(id1, id2)
    }

    @Test
    @DisplayName("toString should return value")
    fun `toString should return value`() {
        val id = SessionId("test-value")
        assertEquals("test-value", id.toString())
    }
}
