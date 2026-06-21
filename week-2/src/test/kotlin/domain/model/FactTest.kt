package io.averkhogliad.ai.challenge.week2.domain.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit-тесты для доменной модели Fact и FactId.
 */
class FactTest {

    // ============ FactId ============

    @Test
    fun `FactId - создание с валидным значением`() {
        val id = FactId("fact-001")
        assertEquals("fact-001", id.value)
    }

    @Test
    fun `FactId - создание с UUID`() {
        val id = FactId("550e8400-e29b-41d4-a716-446655440000")
        assertEquals("550e8400-e29b-41d4-a716-446655440000", id.value)
    }

    @Test
    fun `FactId - пустое значение вызывает исключение`() {
        assertThrows(IllegalArgumentException::class.java) {
            FactId("")
        }
    }

    @Test
    fun `FactId - только пробелы вызывают исключение`() {
        assertThrows(IllegalArgumentException::class.java) {
            FactId("   ")
        }
    }

    @Test
    fun `FactId - equals для одинаковых значений`() {
        assertEquals(FactId("abc"), FactId("abc"))
    }

    @Test
    fun `FactId - toString возвращает значение`() {
        val id = FactId("fact-42")
        assertEquals("fact-42", id.value)
        assertTrue(id.toString().contains("fact-42"))
    }

    // ============ Fact ============

    @Test
    fun `Fact - создание с валидными полями`() {
        val now = Instant.now()
        val fact = Fact(
            id = FactId("fact-001"),
            content = "Сегодня я узнал, что Kotlin поддерживает value classes",
            createdAt = now
        )
        assertEquals(FactId("fact-001"), fact.id)
        assertEquals("Сегодня я узнал, что Kotlin поддерживает value classes", fact.content)
        assertEquals(now, fact.createdAt)
    }

    @Test
    fun `Fact - пустой content вызывает исключение`() {
        assertThrows(IllegalArgumentException::class.java) {
            Fact(
                id = FactId("f-1"),
                content = "",
                createdAt = Instant.now()
            )
        }
    }

    @Test
    fun `Fact - content из пробелов вызывает исключение`() {
        assertThrows(IllegalArgumentException::class.java) {
            Fact(
                id = FactId("f-1"),
                content = "   ",
                createdAt = Instant.now()
            )
        }
    }

    @Test
    fun `Fact - content с пробелами по краям допустим (trim не применяется)`() {
        val content = "  важно  "
        val fact = Fact(
            id = FactId("f-1"),
            content = content,
            createdAt = Instant.now()
        )
        assertEquals(content, fact.content)
    }

    @Test
    fun `Fact - equals для одинаковых фактов`() {
        val now = Instant.now()
        val f1 = Fact(FactId("a"), "content", now)
        val f2 = Fact(FactId("a"), "content", now)
        assertEquals(f1, f2)
    }

    @Test
    fun `Fact - разные id — разные факты`() {
        val now = Instant.now()
        val f1 = Fact(FactId("a"), "content", now)
        val f2 = Fact(FactId("b"), "content", now)
        assertNotEquals(f1, f2)
    }

    @Test
    fun `Fact - разный content — разные факты`() {
        val now = Instant.now()
        val f1 = Fact(FactId("a"), "hello", now)
        val f2 = Fact(FactId("a"), "world", now)
        assertNotEquals(f1, f2)
    }

    @Test
    fun `Fact - copy сохраняет поля`() {
        val now = Instant.now()
        val original = Fact(FactId("f-1"), "оригинал", now)
        val copy = original.copy(content = "копия")
        assertEquals(FactId("f-1"), copy.id)
        assertEquals("копия", copy.content)
        assertEquals(now, copy.createdAt)
    }
}
