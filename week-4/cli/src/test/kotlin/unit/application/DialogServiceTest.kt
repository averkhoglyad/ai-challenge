package io.averkhogliad.ai.challenge.week4.cli.unit.application

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatMessage as LlmChatMessage
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatRole as LlmChatRole

class DialogServiceTest : FreeSpec({

    // ========================================================================
    // Вспомогательные моки
    // ========================================================================

    class StubInvariantService : InvariantService(object : InvariantRepository {
        override suspend fun save(invariant: Invariant): Invariant = invariant
        override suspend fun findById(id: InvariantId): Invariant? = null
        override suspend fun findAll(): List<Invariant> = emptyList()
        override suspend fun delete(id: InvariantId) = false
        override suspend fun count() = 0
    })

    class StubProfileRepository(
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

    class MockLlmPort : LlmPort {
        var chatResult: TaskResult = TaskResult.Success("x")
        var chatWithMessagesResult: TaskResult = TaskResult.Success("x")
        var lastChatPrompt: Prompt = Prompt("x")
        var lastChatMessages: List<LlmChatMessage> = emptyList()

        override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig, tools: List<MCPTool>?): TaskResult {
            lastChatPrompt = prompt
            return chatResult
        }

        override suspend fun chatWithMessages(
            messages: List<LlmChatMessage>,
            config: TaskExecutionConfig,
            tools: List<MCPTool>?
        ): TaskResult {
            lastChatMessages = messages
            return chatWithMessagesResult
        }

        override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week4.cli.domain.ModelId> = emptyList()
    }

    class InMemoryDialogSessionRepository : DialogSessionRepository {
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

    lateinit var mockLlmPort: MockLlmPort
    lateinit var sessionRepository: InMemoryDialogSessionRepository
    lateinit var memoryService: MemoryService
    lateinit var promptBuilder: PromptBuilder
    lateinit var dialogService: DialogService
    lateinit var stubInvariantService: StubInvariantService

    beforeEach {
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
            invariantService = stubInvariantService,
            mcpService = mockk(relaxed = true),
            toolCallRouter = mockk(relaxed = true),
            toolRegistry = io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry(emptyList()),
            promptPresetAggregator = mockk(relaxed = true),
            taskRepository = mockk(relaxed = true)
        )
    }

    "chat" - {
        "should return successful result from LLM" {
            runTest {
                // given
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Reply")

                // when
                val result = dialogService.chat("Hi", SessionLevel.TASK_LIST)

                // then
                result.shouldBeInstanceOf<TaskResult.Success>()
                (result as TaskResult.Success).content shouldBe "Reply"
            }
        }

        "should save user message to STM" {
            runTest {
                // given
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

                // when
                dialogService.chat("Question", SessionLevel.TASK_LIST)

                // then
                val msgs = memoryService.getRecentMessages(SessionLevel.TASK_LIST)
                msgs.any { it.content == "Question" && it.role == MessageRole.USER } shouldBe true
            }
        }

        "should save assistant response to STM" {
            runTest {
                // given
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Answer")

                // when
                dialogService.chat("Q", SessionLevel.TASK_LIST)

                // then
                val msgs = memoryService.getRecentMessages(SessionLevel.TASK_LIST)
                msgs.any { it.content == "Answer" && it.role == MessageRole.ASSISTANT } shouldBe true
            }
        }

        "should pass system message to LLM" {
            runTest {
                // given
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

                // when
                dialogService.chat("Q", SessionLevel.TASK_LIST)

                // then
                val msgs = mockLlmPort.lastChatMessages
                msgs.isNotEmpty() shouldBe true
                msgs.first().role shouldBe LlmChatRole.SYSTEM
                msgs.first().content shouldContain PromptBuilder.SYSTEM_INSTRUCTION
            }
        }

        "should handle LLM errors" {
            runTest {
                // given
                mockLlmPort.chatWithMessagesResult = TaskResult.Error("LLM down", RuntimeException("fail"))

                // when
                val result = dialogService.chat("Q", SessionLevel.TASK_LIST)

                // then
                result.shouldBeInstanceOf<TaskResult.Error>()
                (result as TaskResult.Error).message shouldContain "LLM down"
            }
        }

        "should support TASK_DETAIL level" {
            runTest {
                // given
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Task reply")
                val taskId = TaskId("t1")

                // when
                val result = dialogService.chat("What?", SessionLevel.TASK_DETAIL, taskId)

                // then
                result.shouldBeInstanceOf<TaskResult.Success>()
                (result as TaskResult.Success).content shouldBe "Task reply"
            }
        }
    }

    "planSteps" - {
        "should return plan" {
            runTest {
                // given
                mockLlmPort.chatResult = TaskResult.Success("1. Step one\n2. Step two")

                // when
                val result = dialogService.planSteps("Task", null, SessionLevel.TASK_LIST)

                // then
                result.shouldBeInstanceOf<TaskResult.Success>()
                (result as TaskResult.Success).content shouldContain "Step one"
            }
        }

        "should build prompt with title" {
            runTest {
                // given
                mockLlmPort.chatResult = TaskResult.Success("1. A\n2. B")

                // when
                dialogService.planSteps("API", "Desc", SessionLevel.TASK_LIST)

                // then
                val p = mockLlmPort.lastChatPrompt
                p.value shouldContain "API"
                p.value shouldContain "Desc"
            }
        }

        "should handle plan errors" {
            runTest {
                // given
                mockLlmPort.chatResult = TaskResult.Error("Plan fail", RuntimeException("x"))

                // when
                val result = dialogService.planSteps("T", null, SessionLevel.TASK_LIST)

                // then
                result.shouldBeInstanceOf<TaskResult.Error>()
                (result as TaskResult.Error).message shouldContain "Plan fail"
            }
        }
    }

    "Profile integration" - {
        val now = Instant.now()

        "chat with profileRepository passes active profile to prompt" {
            runTest {
                // given
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
                    invariantService = stubInvariantService,
                    mcpService = mockk(relaxed = true),
                    toolCallRouter = mockk(relaxed = true),
                    toolRegistry = io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry(emptyList()),
                    promptPresetAggregator = mockk(relaxed = true),
                    taskRepository = mockk(relaxed = true)
                )
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

                // when
                val result = service.chat("Ахой!", SessionLevel.TASK_LIST)

                // then
                result.shouldBeInstanceOf<TaskResult.Success>()
                profileRepo.findActiveCalled shouldBe true
                // Проверяем, что системное сообщение содержит профиль
                val msgs = mockLlmPort.lastChatMessages
                msgs.isNotEmpty() shouldBe true
                msgs.first().content shouldContain "[PROFILE]"
                msgs.first().content shouldContain "Отвечай как пират"
            }
        }

        "chat with no active profile works correctly" {
            runTest {
                // given
                val profileRepo = StubProfileRepository(activeProfile = null)

                val service = DialogService(
                    llmPort = mockLlmPort,
                    memoryService = memoryService,
                    promptBuilder = promptBuilder,
                    profileRepository = profileRepo,
                    invariantService = stubInvariantService,
                    mcpService = mockk(relaxed = true),
                    toolCallRouter = mockk(relaxed = true),
                    toolRegistry = io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry(emptyList()),
                    promptPresetAggregator = mockk(relaxed = true),
                    taskRepository = mockk(relaxed = true)
                )
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

                // when
                val result = service.chat("Обычный запрос", SessionLevel.TASK_LIST)

                // then
                result.shouldBeInstanceOf<TaskResult.Success>()
                profileRepo.findActiveCalled shouldBe true
                val msgs = mockLlmPort.lastChatMessages
                msgs.first().content.contains("[PROFILE]") shouldBe false
            }
        }

        "chat calls findActive on every request" {
            runTest {
                // given
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
                    invariantService = stubInvariantService,
                    mcpService = mockk(relaxed = true),
                    toolCallRouter = mockk(relaxed = true),
                    toolRegistry = io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry(emptyList()),
                    promptPresetAggregator = mockk(relaxed = true),
                    taskRepository = mockk(relaxed = true)
                )
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

                // when - первый запрос
                service.chat("Первый запрос", SessionLevel.TASK_LIST)
                val firstCallCount = profileRepo.findActiveCallCount

                // when - второй запрос
                service.chat("Второй запрос", SessionLevel.TASK_LIST)
                val secondCallCount = profileRepo.findActiveCallCount

                // then - findActive() был вызван дважды (по разу на каждый chat)
                secondCallCount shouldBe 2
                (firstCallCount < secondCallCount) shouldBe true
            }
        }
    }

    "Backward compatibility — null profileRepository" - {
        "dialogService works without profileRepository" {
            runTest {
                // given
                val service = DialogService(
                    llmPort = mockLlmPort,
                    memoryService = memoryService,
                    promptBuilder = promptBuilder,
                    profileRepository = StubProfileRepository(),
                    invariantService = stubInvariantService,
                    mcpService = mockk(relaxed = true),
                    toolCallRouter = mockk(relaxed = true),
                    toolRegistry = io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry(emptyList()),
                    promptPresetAggregator = mockk(relaxed = true),
                    taskRepository = mockk(relaxed = true)
                )
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")

                // when
                val result = service.chat("Test", SessionLevel.TASK_LIST)

                // then
                result.shouldBeInstanceOf<TaskResult.Success>()
            }
        }
    }
})
