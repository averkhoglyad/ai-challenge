package io.averkhogliad.ai.challenge.week0.domain.service

import io.averkhogliad.ai.challenge.week0.domain.Prompt
import io.averkhogliad.ai.challenge.week0.domain.TaskResult
import io.averkhogliad.ai.challenge.week0.domain.config.TaskExecutionConfig
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/**
 * Unit-тесты для [PromptEngineeringService].
 */
class PromptEngineeringServiceTest {

    private val mockLlmPort = MockLlmPort()
    private val service = PromptEngineeringService(mockLlmPort)
    private val defaultConfig = TaskExecutionConfig()

    @Test
    fun `direct mode without modifiers returns success`() = runBlocking {
        mockLlmPort.respondWithSuccess("direct response")
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.DIRECT,
            config = defaultConfig
        )
        assertEquals(PromptEngineeringService.Mode.DIRECT, result.mode)
        assertIs<TaskResult.Success>(result.directResult)
        assertEquals("direct response", (result.directResult as TaskResult.Success).content)
    }

    @Test
    fun `direct mode with role sends system message`() = runBlocking {
        mockLlmPort.respondWithMessages { messages, _ ->
            val systemMsg = messages.find { it.role == ChatRole.SYSTEM }
            assertNotNull(systemMsg)
            assertTrue(systemMsg.content.contains("Эксперт"))
            TaskResult.Success("response with role")
        }
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.DIRECT,
            role = "Ты — Эксперт по Kotlin. Отвечай технически точно.",
            config = defaultConfig
        )
        assertIs<TaskResult.Success>(result.directResult)
        assertEquals(1, mockLlmPort.chatWithMessagesCalls.size)
    }

    @Test
    fun `direct mode with step sends system message with instruction`() = runBlocking {
        mockLlmPort.respondWithMessages { messages, _ ->
            val systemMsg = messages.find { it.role == ChatRole.SYSTEM }
            assertNotNull(systemMsg)
            assertTrue(systemMsg.content.contains("пошагово"))
            TaskResult.Success("step-by-step response")
        }
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.DIRECT,
            step = "Решай пошагово, объясняя каждый шаг.",
            config = defaultConfig
        )
        assertIs<TaskResult.Success>(result.directResult)
    }

    @Test
    fun `direct mode with role and step sends system message with both`() = runBlocking {
        mockLlmPort.respondWithMessages { messages, _ ->
            val systemMsg = messages.find { it.role == ChatRole.SYSTEM }
            assertNotNull(systemMsg)
            assertTrue(systemMsg.content.contains("Эксперт"))
            assertTrue(systemMsg.content.contains("пошагово"))
            TaskResult.Success("combined response")
        }
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.DIRECT,
            role = "Ты — Эксперт.",
            step = "Решай пошагово.",
            config = defaultConfig
        )
        assertIs<TaskResult.Success>(result.directResult)
    }

    @Test
    fun `direct mode without modifiers uses simple chat`() = runBlocking {
        mockLlmPort.respondWithSuccess("simple response")
        service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.DIRECT,
            config = defaultConfig
        )
        assertEquals(1, mockLlmPort.chatCalls.size)
        assertEquals("test prompt", mockLlmPort.chatCalls.first().first.value)
    }

    @Test
    fun `experts mode queries all experts in parallel`() = runBlocking {
        mockLlmPort.respondWithMessagesSuccess("expert response")
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.EXPERTS,
            experts = listOf("Аналитик", "Инженер", "Критик"),
            config = defaultConfig
        )
        assertEquals(PromptEngineeringService.Mode.EXPERTS, result.mode)
        assertEquals(3, result.expertResponses.size)
        assertTrue(result.expertResponses.all { it.result is TaskResult.Success })
        assertEquals(3, mockLlmPort.chatWithMessagesCalls.size)
        assertNull(result.summary)
    }

    @Test
    fun `experts mode with step adds instruction to all experts`() = runBlocking {
        mockLlmPort.respondWithMessages { messages, _ ->
            val systemMsg = messages.find { it.role == ChatRole.SYSTEM }
            assertNotNull(systemMsg)
            assertTrue(systemMsg.content.contains("пошагово"))
            TaskResult.Success("expert step response")
        }
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.EXPERTS,
            step = "Решай пошагово.",
            experts = listOf("Аналитик", "Инженер"),
            config = defaultConfig
        )
        assertEquals(2, result.expertResponses.size)
    }

    @Test
    fun `experts mode with summary generates conclusion`() = runBlocking {
        var summaryCallCount = 0
        mockLlmPort.respondWithMessages { messages, _ ->
            if (messages.any { it.content.contains("На основе мнений экспертов") }) {
                summaryCallCount++
                TaskResult.Success("итоговое заключение")
            } else {
                TaskResult.Success("expert response")
            }
        }
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.EXPERTS,
            experts = listOf("Аналитик", "Инженер", "Критик"),
            summary = true,
            config = defaultConfig
        )
        assertEquals(3, result.expertResponses.size)
        assertNotNull(result.summary)
        assertIs<TaskResult.Success>(result.summary)
        assertEquals(1, summaryCallCount)
    }

    @Test
    fun `experts mode with summary and single expert skips summary`() = runBlocking {
        mockLlmPort.respondWithMessagesSuccess("expert response")
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.EXPERTS,
            experts = listOf("Аналитик"),
            summary = true,
            config = defaultConfig
        )
        assertEquals(1, result.expertResponses.size)
        assertNull(result.summary)
    }

    @Test
    fun `experts mode with summary disabled does not generate conclusion`() = runBlocking {
        mockLlmPort.respondWithMessagesSuccess("expert response")
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.EXPERTS,
            experts = listOf("Аналитик", "Инженер"),
            summary = false,
            config = defaultConfig
        )
        assertEquals(2, result.expertResponses.size)
        assertNull(result.summary)
    }

    @Test
    fun `experts mode handles failed expert queries gracefully`() = runBlocking {
        mockLlmPort.respondWithMessages { messages, _ ->
            // The second expert (Инженер) fails
            if (messages.any { it.content.contains("Инженер") && it.role == ChatRole.USER }) {
                TaskResult.Error("model unavailable")
            } else {
                TaskResult.Success("expert response")
            }
        }
        val result = service.execute(
            prompt = Prompt("test prompt"),
            mode = PromptEngineeringService.Mode.EXPERTS,
            experts = listOf("Аналитик", "Инженер", "Критик"),
            config = defaultConfig
        )
        assertEquals(3, result.expertResponses.size)
        val successCount = result.expertResponses.count { it.result is TaskResult.Success }
        assertTrue(successCount >= 2)
    }

    @Test
    fun `default experts are analyst engineer and critic`() {
        val experts = PromptEngineeringService.DEFAULT_EXPERTS
        assertEquals(3, experts.size)
        assertTrue(experts.contains("Аналитик"))
        assertTrue(experts.contains("Инженер"))
        assertTrue(experts.contains("Критик"))
    }
}
