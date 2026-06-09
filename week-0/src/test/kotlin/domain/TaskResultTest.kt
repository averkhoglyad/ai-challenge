package io.averkhogliad.ai.challenge.week0.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты для sealed interface [TaskResult] и всех его вариантов:
 * - [TaskResult.Success]
 * - [TaskResult.Error]
 * - [TaskResult.Partial]
 *
 * Проверяет структурное равенство (data class equality),
 * обработку в `when`-выражениях и корректность значений по умолчанию.
 */
class TaskResultTest {

    // ----------------
    //  Success
    // ----------------

    @Test
    fun `success should store content and metadata`() {
        val meta = mapOf("tokens" to 42, "model" to "gpt-4")
        val result = TaskResult.Success(content = "Hello", metadata = meta)

        assertEquals("Hello", result.content)
        assertEquals(meta, result.metadata)
    }

    @Test
    fun `success should default metadata to empty map`() {
        val result = TaskResult.Success(content = "Hello")
        assertEquals(emptyMap(), result.metadata)
    }

    @Test
    fun `success should support structural equality`() {
        val r1 = TaskResult.Success("A", mapOf("x" to 1))
        val r2 = TaskResult.Success("A", mapOf("x" to 1))
        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    // ----------------
    //  Error
    // ----------------

    @Test
    fun `error should store message and cause`() {
        val cause = RuntimeException("boom")
        val result = TaskResult.Error(message = "Failed", cause = cause)

        assertEquals("Failed", result.message)
        assertEquals(cause, result.cause)
    }

    @Test
    fun `error should default cause to null`() {
        val result = TaskResult.Error(message = "Failed")
        assertEquals(null, result.cause)
    }

    @Test
    fun `error should support structural equality`() {
        val cause = RuntimeException("boom")
        val r1 = TaskResult.Error("Err", cause)
        val r2 = TaskResult.Error("Err", cause)

        assertEquals(r1, r2)
        assertEquals(r1.hashCode(), r2.hashCode())
    }

    // ----------------
    //  Partial
    // ----------------

    @Test
    fun `partial should store content and progress`() {
        val result = TaskResult.Partial(content = "chunk", progress = 0.5)

        assertEquals("chunk", result.content)
        assertEquals(0.5, result.progress)
    }

    @Test
    fun `partial should support progress of zero`() {
        val result = TaskResult.Partial("start", 0.0)
        assertEquals(0.0, result.progress)
    }

    @Test
    fun `partial should support progress of one`() {
        val result = TaskResult.Partial("done", 1.0)
        assertEquals(1.0, result.progress)
    }

    // ----------------
    //  Exhaustive when
    // ----------------

    @Test
    fun `when should compile without else for sealed interface`() {
        fun render(result: TaskResult): String = when (result) {
            is TaskResult.Success -> "OK: ${result.content}"
            is TaskResult.Error -> "ERR: ${result.message}"
            is TaskResult.Partial -> "PART: ${result.content} (${result.progress})"
        }

        assertEquals("OK: Hi", render(TaskResult.Success("Hi")))
        assertEquals("ERR: fail", render(TaskResult.Error("fail")))
        assertEquals("PART: mid (0.5)", render(TaskResult.Partial("mid", 0.5)))
    }

    // ----------------
    //  Type checks
    // ----------------

    @Test
    fun `success should be instance of TaskResult`() {
        val result: TaskResult = TaskResult.Success("Hi")
        assertTrue(result is TaskResult)
        assertTrue(result is TaskResult.Success)
    }

    @Test
    fun `error should be instance of TaskResult`() {
        val result: TaskResult = TaskResult.Error("fail")
        assertTrue(result is TaskResult)
        assertTrue(result is TaskResult.Error)
    }

    @Test
    fun `partial should be instance of TaskResult`() {
        val result: TaskResult = TaskResult.Partial("mid", 0.3)
        assertTrue(result is TaskResult)
        assertTrue(result is TaskResult.Partial)
    }
}
