package io.averkhogliad.ai.challenge.week2.application

import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@DisplayName("DialogService")
class DialogServiceTest {

    private lateinit var mockLlmPort: MockLlmPort
    private lateinit var sessionRepository: InMemoryDialogSessionRepository
    private lateinit var memoryService: MemoryService
    private lateinit var promptBuilder: PromptBuilder
    private lateinit var dialogService: DialogService

    @BeforeEach
    fun setUp() {
        mockLlmPort = MockLlmPort()
        sessionRepository = InMemoryDialogSessionRepository()
        memoryService = MemoryService(sessionRepository)
        promptBuilder = PromptBuilder()
        dialogService = DialogService(mockLlmPort, memoryService, promptBuilder)
    }

    @Nested
    @DisplayName("chat")
    inner class Chat {
        @Test
        fun `should return successful result from LLM`() = runBlocking {
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Reply")
            val result = dialogService.chat("Hi", SessionLevel.TASK_LIST)
            assertIs<TaskResult.Success>(result)
            assertEquals("Reply", result.content)
        }

        @Test
        fun `should save user message to STM`() = runBlocking {
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")
            dialogService.chat("Question", SessionLevel.TASK_LIST)
            val msgs = memoryService.getRecentMessages(SessionLevel.TASK_LIST)
            assertTrue(msgs.any { it.content == "Question" && it.role == MessageRole.USER })
        }

        @Test
        fun `should save assistant response to STM`() = runBlocking {
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Answer")
            dialogService.chat("Q", SessionLevel.TASK_LIST)
            val msgs = memoryService.getRecentMessages(SessionLevel.TASK_LIST)
            assertTrue(msgs.any { it.content == "Answer" && it.role == MessageRole.ASSISTANT })
        }

        @Test
        fun `should pass system message to LLM`() = runBlocking {
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")
            dialogService.chat("Q", SessionLevel.TASK_LIST)
            val msgs = mockLlmPort.lastChatMessages
            assertTrue(msgs.isNotEmpty())
            assertEquals(ChatRole.SYSTEM, msgs.first().role)
            assertContains(msgs.first().content, PromptBuilder.SYSTEM_INSTRUCTION)
        }

        @Test
        fun `should handle LLM errors`() = runBlocking {
            mockLlmPort.chatWithMessagesResult = TaskResult.Error("LLM down", RuntimeException("fail"))
            val result = dialogService.chat("Q", SessionLevel.TASK_LIST)
            assertIs<TaskResult.Error>(result)
            assertContains(result.message, "LLM down")
        }

        @Test
        fun `should support TASK_DETAIL level`() = runBlocking {
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Task reply")
            val taskId = TaskId("t1")
            val result = dialogService.chat("What?", SessionLevel.TASK_DETAIL, taskId)
            assertIs<TaskResult.Success>(result)
            assertEquals("Task reply", result.content)
        }
    }

    @Nested
    @DisplayName("planSteps")
    inner class PlanSteps {
        @Test
        fun `should return plan`() = runBlocking {
            mockLlmPort.chatResult = TaskResult.Success("1. Step one\n2. Step two")
            val result = dialogService.planSteps("Task", null, SessionLevel.TASK_LIST)
            assertIs<TaskResult.Success>(result)
            assertContains(result.content, "Step one")
        }

        @Test
        fun `should build prompt with title`() = runBlocking {
            mockLlmPort.chatResult = TaskResult.Success("1. A\n2. B")
            dialogService.planSteps("API", "Desc", SessionLevel.TASK_LIST)
            val p = mockLlmPort.lastChatPrompt
            assertContains(p.value, "API")
            assertContains(p.value, "Desc")
        }

        @Test
        fun `should handle plan errors`() = runBlocking {
            mockLlmPort.chatResult = TaskResult.Error("Plan fail", RuntimeException("x"))
            val result = dialogService.planSteps("T", null, SessionLevel.TASK_LIST)
            assertIs<TaskResult.Error>(result)
            assertContains(result.message, "Plan fail")
        }
    }

    private class MockLlmPort : LlmPort {
        var chatResult: TaskResult = TaskResult.Success("x")
        var chatWithMessagesResult: TaskResult = TaskResult.Success("x")
        var lastChatPrompt: Prompt = Prompt("x")
        var lastChatMessages: List<ChatMessage> = emptyList()

        override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
            lastChatPrompt = prompt
            return chatResult
        }

        override suspend fun chatWithMessages(messages: List<ChatMessage>, config: TaskExecutionConfig): TaskResult {
            lastChatMessages = messages
            return chatWithMessagesResult
        }

        override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week2.domain.ModelId> = emptyList()
    }

    private class InMemoryDialogSessionRepository : DialogSessionRepository {
        private val sessions = mutableMapOf<String, DialogSession>()
        override fun findById(id: SessionId): DialogSession? = sessions[id.value]
        override fun save(session: DialogSession): DialogSession {
            sessions[session.id.value] = session; return session
        }

        override fun findByTaskId(taskId: TaskId): DialogSession? =
            sessions.values.firstOrNull { it.taskId == taskId }

        override fun findActiveSession(): DialogSession? = sessions.values.firstOrNull()
        override fun delete(id: SessionId) {
            sessions.remove(id.value)
        }
    }
}
