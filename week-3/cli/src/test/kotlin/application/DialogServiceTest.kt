package io.averkhogliad.ai.challenge.week3.cli.application

import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

@DisplayName("DialogService")
class DialogServiceTest {

    private lateinit var mockLlmPort: MockLlmPort
    private lateinit var sessionRepository: InMemoryDialogSessionRepository
    private lateinit var memoryService: MemoryService
    private lateinit var promptBuilder: PromptBuilder
    private lateinit var dialogService: DialogService
    private lateinit var stubInvariantService: StubInvariantService

    @BeforeEach
    fun setUp() {
        mockLlmPort = MockLlmPort()
        sessionRepository = InMemoryDialogSessionRepository()
        memoryService = MemoryService(sessionRepository)
        promptBuilder = PromptBuilder()
        stubInvariantService = StubInvariantService()
        dialogService = DialogService(
            mockLlmPort,
            memoryService,
            promptBuilder,
            profileRepository = StubProfileRepository(),
            invariantService = stubInvariantService
        )
    }

    // ========================================================================
    // Существующие тесты Chat
    // ========================================================================

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

    // ========================================================================
    // Существующие тесты PlanSteps
    // ========================================================================

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

    // ========================================================================
    // НОВЫЕ тесты: интеграция с профилем
    // ========================================================================

    @Nested
    @DisplayName("Profile integration")
    inner class ProfileIntegration {

        private val now = Instant.now()

        @Test
        fun `chat with profileRepository passes active profile to prompt`() = runBlocking {
            val activeProfile = Profile(
                id = ProfileId("active-1"),
                name = "ActiveProfile",
                description = "Пиратский стиль общения",
                instructions = "Отвечай как пират",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            val profileRepo = StubProfileRepository(activeProfile = activeProfile)

            val service = DialogService(
                llmPort = mockLlmPort,
                memoryService = memoryService,
                promptBuilder = promptBuilder,
                profileRepository = profileRepo,
                invariantService = stubInvariantService
            )
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

            val result = service.chat("Ахой!", SessionLevel.TASK_LIST)

            assertIs<TaskResult.Success>(result)
            assertTrue(profileRepo.findActiveCalled)
            // Проверяем, что системное сообщение содержит профиль
            val msgs = mockLlmPort.lastChatMessages
            assertTrue(msgs.isNotEmpty())
            assertContains(msgs.first().content, "[PROFILE]")
            assertContains(msgs.first().content, "Отвечай как пират")
        }

        @Test
        fun `chat with no active profile works correctly`() = runBlocking {
            val profileRepo = StubProfileRepository(activeProfile = null)

            val service = DialogService(
                llmPort = mockLlmPort,
                memoryService = memoryService,
                promptBuilder = promptBuilder,
                profileRepository = profileRepo,
                invariantService = stubInvariantService
            )
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

            val result = service.chat("Обычный запрос", SessionLevel.TASK_LIST)

            assertIs<TaskResult.Success>(result)
            assertTrue(profileRepo.findActiveCalled)
            val msgs = mockLlmPort.lastChatMessages
            assertFalse(msgs.first().content.contains("[PROFILE]"))
        }

        @Test
        fun `chat calls findActive on every request`() = runBlocking {
            val activeProfile = Profile(
                id = ProfileId("active-2"),
                name = "RepeatedProfile",
                description = "Test",
                instructions = "Отвечай кратко",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            val profileRepo = StubProfileRepository(activeProfile = activeProfile)

            val service = DialogService(
                llmPort = mockLlmPort,
                memoryService = memoryService,
                promptBuilder = promptBuilder,
                profileRepository = profileRepo,
                invariantService = stubInvariantService
            )
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

            // Первый запрос
            service.chat("Первый запрос", SessionLevel.TASK_LIST)
            val firstCallCount = profileRepo.findActiveCallCount

            // Второй запрос
            service.chat("Второй запрос", SessionLevel.TASK_LIST)
            val secondCallCount = profileRepo.findActiveCallCount

            // Проверяем, что findActive() был вызван дважды (по разу на каждый chat)
            assertEquals(2, secondCallCount)
            assertTrue(firstCallCount < secondCallCount)
        }
    }

    @Nested
    @DisplayName("Backward compatibility — null profileRepository")
    inner class NullProfileRepository {

        @Test
        fun `dialogService works without profileRepository`(): Unit = runBlocking {
            val service = DialogService(
                llmPort = mockLlmPort,
                memoryService = memoryService,
                promptBuilder = promptBuilder,
                profileRepository = StubProfileRepository(),
                invariantService = stubInvariantService
            )
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")
            val result = service.chat("Test", SessionLevel.TASK_LIST)
            assertIs<TaskResult.Success>(result)
        }
    }

    // ========================================================================
    // Вспомогательные моки
    // ========================================================================

    private class StubInvariantService : InvariantService(object : InvariantRepository {
        override suspend fun save(invariant: Invariant): Invariant = invariant
        override suspend fun findById(id: InvariantId): Invariant? = null
        override suspend fun findAll(): List<Invariant> = emptyList()
        override suspend fun delete(id: InvariantId) = false
        override suspend fun count() = 0
    })

    private class StubProfileRepository(
        private val activeProfile: Profile? = null
    ) : ProfileRepository {
        var findActiveCalled = false
        var findActiveCallCount = 0

        override suspend fun findActive(): Profile? {
            findActiveCalled = true
            findActiveCallCount++
            return activeProfile
        }

        override suspend fun save(profile: Profile): Profile = profile
        override suspend fun findById(id: ProfileId): Profile? = null
        override suspend fun findByName(name: String): Profile? = null
        override suspend fun findAll(): List<Profile> = emptyList()
        override suspend fun delete(id: ProfileId) {}
        override suspend fun existsByName(name: String): Boolean = false
        override suspend fun clearActive() {}
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

        override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week3.cli.domain.ModelId> = emptyList()
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
