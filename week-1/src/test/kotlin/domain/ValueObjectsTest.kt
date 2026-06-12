package io.averkhogliad.ai.challenge.week1.domain

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

/**
 * Тесты валидации value objects:
 * - [TaskId] — positive constraint
 * - [Prompt] — non-blank constraint
 * - [ModelId] — non-blank constraint
 */
class ValueObjectsTest {

    // ----------------
    //  TaskId
    // ----------------

    @Test
    fun `taskId should accept positive values`() {
        val id = TaskId(1)
        assertEquals(1, id.value)
    }

    @Test
    fun `taskId should accept large positive values`() {
        val id = TaskId(Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, id.value)
    }

    @Test
    fun `taskId should reject zero`() {
        assertThrows<IllegalArgumentException> {
            TaskId(0)
        }
    }

    @Test
    fun `taskId should reject negative values`() {
        assertThrows<IllegalArgumentException> {
            TaskId(-1)
        }
    }

    @Test
    fun `taskId should reject Int MIN_VALUE`() {
        assertThrows<IllegalArgumentException> {
            TaskId(Int.MIN_VALUE)
        }
    }

    // ----------------
    //  Prompt
    // ----------------

    @Test
    fun `prompt should accept non-blank text`() {
        val p = Prompt("Hello, world!")
        assertEquals("Hello, world!", p.value)
    }

    @Test
    fun `prompt should accept single character`() {
        val p = Prompt("x")
        assertEquals("x", p.value)
    }

    @Test
    fun `prompt should reject blank string`() {
        assertThrows<IllegalArgumentException> {
            Prompt("   ")
        }
    }

    @Test
    fun `prompt should reject empty string`() {
        assertThrows<IllegalArgumentException> {
            Prompt("")
        }
    }

    @Test
    fun `prompt should reject string with only whitespace characters`() {
        assertThrows<IllegalArgumentException> {
            Prompt("\t\n\r")
        }
    }

    // ----------------
    //  ModelId
    // ----------------

    @Test
    fun `modelId should accept valid identifier`() {
        val mid = ModelId("gpt-4")
        assertEquals("gpt-4", mid.value)
    }

    @Test
    fun `modelId should accept short identifier`() {
        val mid = ModelId("x")
        assertEquals("x", mid.value)
    }

    @Test
    fun `modelId should reject blank string`() {
        assertThrows<IllegalArgumentException> {
            ModelId("   ")
        }
    }

    @Test
    fun `modelId should reject empty string`() {
        assertThrows<IllegalArgumentException> {
            ModelId("")
        }
    }
}
