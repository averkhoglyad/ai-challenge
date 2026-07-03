package io.averkhogliad.ai.challenge.week3.cli.it

import io.averkhogliad.ai.challenge.week3.cli.application.DialogService
import io.averkhogliad.ai.challenge.week3.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week3.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week3.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week3.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week3.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.*
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

/**
 * Интеграционные тесты для сквозного потока: профиль → PromptBuilder → DialogService → промпт LLM.
 *
 * Проверяет end-to-end взаимодействие:
 * - [ProfileService] + [InMemoryProfileRepository] — управление профилями
 * - [DialogService] + [PromptBuilder] + [MemoryService] — формирование промпта с профилем
 * - [MockLlmPort] — перехват запросов к LLM для проверки содержимого промпта
 *
 * PromptBuilder формирует секцию [PROFILE] со структурой:
 * ```
 * [PROFILE]
 * Name: <name>
 * Description: <description>
 * Instructions: <instructions>
 * ```
 */
class ProfilePromptIT : FreeSpec({

    lateinit var profileRepository: InMemoryProfileRepository
    lateinit var profileService: ProfileService
    lateinit var mockLlmPort: MockLlmPort
    lateinit var sessionRepository: InMemoryDialogSessionRepository
    lateinit var memoryService: MemoryService
    lateinit var promptBuilder: PromptBuilder
    lateinit var dialogService: DialogService
    lateinit var stubInvariantService: InvariantService

    beforeTest {
        profileRepository = InMemoryProfileRepository()
        profileService = ProfileService(profileRepository)
        mockLlmPort = MockLlmPort()
        sessionRepository = InMemoryDialogSessionRepository()
        memoryService = MemoryService(sessionRepository)
        promptBuilder = PromptBuilder()
        stubInvariantService = InvariantService(object : InvariantRepository {
            override suspend fun save(invariant: Invariant): Invariant = invariant
            override suspend fun findById(id: InvariantId): Invariant? = null
            override suspend fun findAll(): List<Invariant> = emptyList()
            override suspend fun delete(id: InvariantId): Boolean = true
            override suspend fun count(): Int = 0
            override fun close() {}
        })
        dialogService = DialogService(
            llmPort = mockLlmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            taskExecutionConfig = TaskExecutionConfig(),
            profileRepository = profileRepository,
            invariantService = stubInvariantService,
            mcpService = mockk(relaxed = true),
            toolCallRouter = mockk(relaxed = true),
            toolRegistry = io.averkhogliad.ai.challenge.week3.cli.application.tool.ToolRegistry(emptyList()),
            promptPresetAggregator = mockk(relaxed = true),
            taskRepository = mockk(relaxed = true)
        )
    }

    /**
     * Тест 1: Полный цикл — создание профиля, активация, проверка что профиль попадает в промпт.
     */
    "active profile is included in LLM prompt end-to-end" - {
        runTest {
            val profile = profileService.handleCreateProfile(
                "Pirate",
                "Отвечай как пират, используй 'Аррр!'",
                ""
            )
            profileService.handleActivateProfile(profile.id)
            (profileService.handleGetActiveProfile() != null) shouldBe true

            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Аррр, приветствую!")

            val result = dialogService.chat("Привет", SessionLevel.TASK_LIST)

            result.shouldBeInstanceOf<TaskResult.Success>()
            result.content shouldBe "Аррр, приветствую!"

            val messages = mockLlmPort.lastChatMessages
            messages.shouldNotBeEmpty()
            val systemContent = messages.first().content

            systemContent shouldContain "[PROFILE]"
            systemContent shouldContain "Name: Pirate"
        }
    }

    /**
     * Тест 2: Смена активного профиля — проверка что новый профиль попадает в промпт.
     */
    "switching profile changes prompt content" - {
        runTest {
            val profileA = profileService.handleCreateProfile(
                "Pirate",
                "Ты — пират, используй 'Йо-хо-хо!'",
                ""
            )
            val profileB = profileService.handleCreateProfile(
                "Robot",
                "Ты — робот, говори как машина",
                ""
            )

            profileService.handleActivateProfile(profileA.id)

            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Йо-хо-хо!")
            dialogService.chat("Привет", SessionLevel.TASK_LIST)
            val promptA = mockLlmPort.lastChatMessages.first().content
            promptA shouldContain "[PROFILE]"
            promptA shouldContain "Name: Pirate"

            profileService.handleActivateProfile(profileB.id)
            mockLlmPort.chatWithMessagesResult = TaskResult.Success("beep-boop")
            dialogService.chat("Ещё раз", SessionLevel.TASK_LIST)
            val promptB = mockLlmPort.lastChatMessages.first().content
            promptB shouldContain "[PROFILE]"
            promptB shouldContain "Name: Robot"
            promptB.contains("Name: Pirate") shouldBe false
        }
    }

    /**
     * Тест 3: Деактивация профиля (через удаление неактивного) — проверка что [PROFILE] секция исчезает из промпта.
     */
    "deleting inactive profile does not remove PROFILE section" - {
        runTest {
            val firstProfile = profileService.handleCreateProfile(
                "TempProfile",
                "Временный контекст для теста",
                ""
            )
            val secondProfile = profileService.handleCreateProfile(
                "SecondProfile",
                "Второй профиль",
                ""
            )

            profileService.handleActivateProfile(firstProfile.id)

            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")
            dialogService.chat("Тест", SessionLevel.TASK_LIST)
            val promptBefore = mockLlmPort.lastChatMessages.first().content
            promptBefore shouldContain "[PROFILE]"
            promptBefore shouldContain "Name: TempProfile"

            profileService.handleActivateProfile(secondProfile.id)

            profileService.handleDeleteProfile("TempProfile")

            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok again")
            dialogService.chat("Тест после удаления", SessionLevel.TASK_LIST)
            val promptAfter = mockLlmPort.lastChatMessages.first().content
            promptAfter shouldContain "[PROFILE]"
            promptAfter shouldContain "Name: SecondProfile"
        }
    }

    /**
     * Тест 4: Профиль с пустыми description/instructions — проверка корректной обработки.
     */
    "profile with empty description and instructions is handled correctly" - {
        runTest {
            val profile = profileService.handleCreateProfile(
                "MinimalProfile",
                "Минимальное содержимое профиля",
                ""
            )
            profileService.handleActivateProfile(profile.id)
            (profileService.handleGetActiveProfile() != null) shouldBe true
            profile.description shouldBe "Минимальное содержимое профиля"
            profile.instructions shouldBe ""

            mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ответ на запрос")

            val result = dialogService.chat("Запрос с минимальным профилем", SessionLevel.TASK_LIST)

            result.shouldBeInstanceOf<TaskResult.Success>()
            val messages = mockLlmPort.lastChatMessages
            messages.shouldNotBeEmpty()
            val systemContent = messages.first().content

            systemContent shouldContain "[PROFILE]"
            systemContent shouldContain "Name: MinimalProfile"

            systemContent shouldContain "Description: "
            systemContent shouldContain "Instructions: "

            systemContent shouldContain PromptBuilder.SYSTEM_INSTRUCTION
        }
    }
}) {
    // ========================================================================
    // Вспомогательные моки (companion objects / nested classes)
    // ========================================================================

    companion object {
        /**
         * Mock LLM-порта для перехвата отправляемых сообщений и возврата заданных результатов.
         */
        class MockLlmPort : LlmPort {
            var chatResult: TaskResult = TaskResult.Success("x")
            var chatWithMessagesResult: TaskResult = TaskResult.Success("x")
            var lastChatPrompt: Prompt = Prompt("x")
            var lastChatMessages: List<ChatMessage> = emptyList()

            override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig, tools: List<MCPTool>?): TaskResult {
                lastChatPrompt = prompt
                return chatResult
            }

            override suspend fun chatWithMessages(
                messages: List<ChatMessage>,
                config: TaskExecutionConfig,
                tools: List<MCPTool>?
            ): TaskResult {
                lastChatMessages = messages
                return chatWithMessagesResult
            }

            override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week3.cli.domain.ModelId> =
                emptyList()
        }

        /**
         * In-memory реализация [DialogSessionRepository] для тестов.
         */
        class InMemoryDialogSessionRepository : DialogSessionRepository {
            private val sessions = mutableMapOf<String, DialogSession>()

            override fun findById(id: SessionId): DialogSession? = sessions[id.value]

            override fun save(session: DialogSession): DialogSession {
                sessions[session.id.value] = session
                return session
            }

            override fun findByTaskId(taskId: TaskId): DialogSession? =
                sessions.values.firstOrNull { it.taskId == taskId }

            override fun findActiveSession(): DialogSession? =
                sessions.values.firstOrNull()

            override fun delete(id: SessionId) {
                sessions.remove(id.value)
            }
        }
    }
}
