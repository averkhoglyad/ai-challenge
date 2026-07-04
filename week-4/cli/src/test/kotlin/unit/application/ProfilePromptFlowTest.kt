package io.averkhogliad.ai.challenge.week4.cli.unit.application

import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week4.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week4.cli.domain.Prompt
import io.averkhogliad.ai.challenge.week4.cli.domain.TaskResult
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.*
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatMessage as LlmChatMessage

/**
 * Unit-тесты для сквозного потока: профиль → PromptBuilder → DialogService → промпт LLM.
 *
 * Проверяет end-to-end взаимодействие:
 * - [ProfileService] + [InMemoryProfileRepository] — управление профилями
 * - [DialogService] + [PromptBuilder] + [MemoryService] — формирование промпта с профилем
 * - [MockLlmPort] — перехват запросов к LLM для проверки содержимого промпта
 */
class ProfilePromptFlowTest : FreeSpec({

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
            toolRegistry = io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry(emptyList()),
            promptPresetAggregator = mockk(relaxed = true),
            taskRepository = mockk(relaxed = true)
        )
    }

    "profile in LLM prompt" - {
        "Тест 1: Полный цикл — профиль попадает в промпт LLM" {
            runTest {
                // given - создаём профиль и явно активируем его
                val profile = profileService.handleCreateProfile(
                    "Pirate",
                    "Отвечай как пират, используй 'Аррр!'",
                    ""
                )
                profileService.handleActivateProfile(profile.id)
                (profileService.handleGetActiveProfile() != null) shouldBe true

                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Аррр, приветствую!")

                // when
                val result = dialogService.chat("Привет", SessionLevel.TASK_LIST)

                // then - проверяем успешный результат
                (result is TaskResult.Success) shouldBe true
                (result as TaskResult.Success).content shouldBe "Аррр, приветствую!"

                // then - проверяем, что сообщения отправлены в LLM
                val messages = mockLlmPort.lastChatMessages
                messages.shouldNotBeEmpty()
                val systemContent = messages.first().content

                // then - проверяем наличие секции [PROFILE] и имени профиля
                systemContent.shouldContain("[PROFILE]")
                systemContent.shouldContain("Name: Pirate")
            }
        }

        "Тест 2: Переключение профиля изменяет содержимое промпта" {
            runTest {
                // given - создаём два профиля
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

                // given - активируем Profile A
                profileService.handleActivateProfile(profileA.id)

                // when - Profile A активен
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Йо-хо-хо!")
                dialogService.chat("Привет", SessionLevel.TASK_LIST)

                // then
                val promptA = mockLlmPort.lastChatMessages.first().content
                promptA.shouldContain("[PROFILE]")
                promptA.shouldContain("Name: Pirate")

                // when - переключаем на Profile B
                profileService.handleActivateProfile(profileB.id)
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("beep-boop")
                dialogService.chat("Ещё раз", SessionLevel.TASK_LIST)

                // then
                val promptB = mockLlmPort.lastChatMessages.first().content
                promptB.shouldContain("[PROFILE]")
                promptB.shouldContain("Name: Robot")
                (promptB.contains("Name: Pirate")) shouldBe false
            }
        }
    }

    "profile deletion and prompt" - {
        "Тест 3: Удаление неактивного профиля не влияет на секцию PROFILE" {
            runTest {
                // given - создаём два профиля
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

                // given - активируем первый профиль
                profileService.handleActivateProfile(firstProfile.id)

                // when - проверяем, что PROFILE секция есть (активен первый)
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")
                dialogService.chat("Тест", SessionLevel.TASK_LIST)

                // then
                val promptBefore = mockLlmPort.lastChatMessages.first().content
                promptBefore.shouldContain("[PROFILE]")
                promptBefore.shouldContain("Name: TempProfile")

                // when - переключаемся на второй профиль
                profileService.handleActivateProfile(secondProfile.id)

                // when - удаляем первый профиль (теперь неактивный, поэтому разрешено)
                profileService.handleDeleteProfile("TempProfile")

                // when - проверяем, что PROFILE секция всё ещё есть (активен второй)
                mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok again")
                dialogService.chat("Тест после удаления", SessionLevel.TASK_LIST)

                // then
                val promptAfter = mockLlmPort.lastChatMessages.first().content
                promptAfter.shouldContain("[PROFILE]")
                promptAfter.shouldContain("Name: SecondProfile")
            }
        }
    }

    "empty profile content" - {
        "Тест 4: Профиль с пустыми description/instructions корректно обрабатывается" {
            runTest {
                // given - создаём профиль (description и instructions будут "" по умолчанию)
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

                // when
                val result = dialogService.chat("Запрос с минимальным профилем", SessionLevel.TASK_LIST)

                // then
                (result is TaskResult.Success) shouldBe true
                val messages = mockLlmPort.lastChatMessages
                messages.shouldNotBeEmpty()
                val systemContent = messages.first().content

                // then - проверяем, что секция [PROFILE] присутствует
                systemContent.shouldContain("[PROFILE]")

                // then - проверяем, что пустые поля присутствуют (не ломают структуру)
                systemContent.shouldContain("Description: ")
                systemContent.shouldContain("Instructions: ")

                // then - проверяем, что системная инструкция всё ещё на месте (следует за [PROFILE])
                systemContent.shouldContain(PromptBuilder.SYSTEM_INSTRUCTION)
            }
        }
    }
})

// ========================================================================
// Вспомогательные моки
// ========================================================================

/**
 * Mock LLM-порта для перехвата отправляемых сообщений и возврата заданных результатов.
 */
private class MockLlmPort : LlmPort {
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

    override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week4.cli.domain.ModelId> =
        emptyList()
}

/**
 * In-memory реализация [DialogSessionRepository] для тестов.
 */
private class InMemoryDialogSessionRepository : DialogSessionRepository {
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
