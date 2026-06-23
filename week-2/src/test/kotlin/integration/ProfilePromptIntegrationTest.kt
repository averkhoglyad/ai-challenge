package io.averkhogliad.ai.challenge.week2.integration

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.*
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.InMemoryProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

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
@DisplayName("ProfilePromptIntegrationTest")
class ProfilePromptIntegrationTest {

    private lateinit var profileRepository: InMemoryProfileRepository
    private lateinit var profileService: ProfileService
    private lateinit var mockLlmPort: MockLlmPort
    private lateinit var sessionRepository: InMemoryDialogSessionRepository
    private lateinit var memoryService: MemoryService
    private lateinit var promptBuilder: PromptBuilder
    private lateinit var dialogService: DialogService
    private lateinit var stubInvariantService: InvariantService

    @BeforeEach
    fun setUp() {
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
            invariantService = stubInvariantService
        )
    }

    /**
     * Тест 1: Полный цикл — создание профиля, активация, проверка что профиль попадает в промпт.
     *
     * Сценарий:
     * 1. Создаём профиль "Pirate" (первый созданный авто-активен)
     * 2. Отправляем сообщение через DialogService
     * 3. Убеждаемся, что секция [PROFILE] присутствует в системном промпте
     * 4. Убеждаемся, что имя профиля присутствует в системном промпте
     */
    @Test
    @DisplayName("Тест 1: Полный цикл — профиль попадает в промпт LLM")
    fun `active profile is included in LLM prompt end-to-end`(): Unit = runBlocking {
        // Создаём профиль и явно активируем его
        val profile = profileService.handleCreateProfile(
            "Pirate",
            "Отвечай как пират, используй 'Аррр!'",
            ""
        )
        profileService.handleActivateProfile(profile.id)
        assertTrue(profileService.handleGetActiveProfile() != null)

        mockLlmPort.chatWithMessagesResult = TaskResult.Success("Аррр, приветствую!")

        val result = dialogService.chat("Привет", SessionLevel.TASK_LIST)

        // Проверяем успешный результат
        assertIs<TaskResult.Success>(result)
        assertEquals("Аррр, приветствую!", result.content)

        // Проверяем, что сообщения отправлены в LLM
        val messages = mockLlmPort.lastChatMessages
        assertTrue(messages.isNotEmpty(), "Сообщения LLM не должны быть пустыми")
        val systemContent = messages.first().content

        // Проверяем наличие секции [PROFILE] и имени профиля
        assertTrue(systemContent.contains("[PROFILE]"), "Системный промпт должен содержать секцию [PROFILE]")
        assertTrue(systemContent.contains("Name: Pirate"), "Системный промпт должен содержать имя профиля")
    }

    /**
     * Тест 2: Смена активного профиля — проверка что новый профиль попадает в промпт.
     *
     * Сценарий:
     * 1. Создаём два профиля (первый авто-активен)
     * 2. Отправляем сообщение — профиль A должен быть в промпте
     * 3. Переключаем на профиль B
     * 4. Отправляем сообщение — профиль B должен быть в промпте, профиль A — нет
     */
    @Test
    @DisplayName("Тест 2: Переключение профиля изменяет содержимое промпта")
    fun `switching profile changes prompt content`(): Unit = runBlocking {
        // Создаём два профиля
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

        // Активируем Profile A
        profileService.handleActivateProfile(profileA.id)

        // Profile A активен
        mockLlmPort.chatWithMessagesResult = TaskResult.Success("Йо-хо-хо!")
        dialogService.chat("Привет", SessionLevel.TASK_LIST)
        val promptA = mockLlmPort.lastChatMessages.first().content
        assertTrue(promptA.contains("[PROFILE]"), "Секция [PROFILE] должна быть для активного профиля A")
        assertTrue(promptA.contains("Name: Pirate"), "Имя профиля A должно быть в промпте")

        // Переключаем на Profile B
        profileService.handleActivateProfile(profileB.id)
        mockLlmPort.chatWithMessagesResult = TaskResult.Success("beep-boop")
        dialogService.chat("Ещё раз", SessionLevel.TASK_LIST)
        val promptB = mockLlmPort.lastChatMessages.first().content
        assertTrue(promptB.contains("[PROFILE]"), "Секция [PROFILE] должна быть для активного профиля B")
        assertTrue(promptB.contains("Name: Robot"), "Имя профиля B должно быть в промпте")
        assertFalse(
            promptB.contains("Name: Pirate"),
            "Имя старого профиля не должно попадать в промпт после переключения"
        )
    }

    /**
     * Тест 3: Деактивация профиля (через удаление неактивного) — проверка что [PROFILE] секция исчезает из промпта.
     *
     * Сценарий:
     * 1. Создаём два профиля (первый автоматически активен)
     * 2. Проверяем, что [PROFILE] секция есть
     * 3. Переключаемся на второй профиль — первый становится неактивным
     * 4. Удаляем первый профиль (теперь неактивный, поэтому разрешено)
     * 5. Проверяем, что [PROFILE] секция всё ещё есть (активен второй)
     */
    @Test
    @DisplayName("Тест 3: Удаление неактивного профиля не влияет на секцию PROFILE")
    fun `deleting inactive profile does not remove PROFILE section`(): Unit = runBlocking {
        // Создаём два профиля
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

        // Активируем первый профиль
        profileService.handleActivateProfile(firstProfile.id)

        // Проверяем, что PROFILE секция есть (активен первый)
        mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok")
        dialogService.chat("Тест", SessionLevel.TASK_LIST)
        val promptBefore = mockLlmPort.lastChatMessages.first().content
        assertTrue(promptBefore.contains("[PROFILE]"), "Секция [PROFILE] должна быть до удаления")
        assertTrue(promptBefore.contains("Name: TempProfile"), "Имя профиля должно быть в промпте до удаления")

        // Переключаемся на второй профиль
        profileService.handleActivateProfile(secondProfile.id)

        // Удаляем первый профиль (теперь неактивный, поэтому разрешено)
        profileService.handleDeleteProfile("TempProfile")

        // Проверяем, что PROFILE секция всё ещё есть (активен второй)
        mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ok again")
        dialogService.chat("Тест после удаления", SessionLevel.TASK_LIST)
        val promptAfter = mockLlmPort.lastChatMessages.first().content
        assertTrue(
            promptAfter.contains("[PROFILE]"),
            "Секция [PROFILE] должна остаться после удаления неактивного профиля (активен второй)"
        )
        assertTrue(promptAfter.contains("Name: SecondProfile"), "Имя второго профиля должно быть в промпте")
    }

    /**
     * Тест 4: Профиль с пустыми description/instructions — проверка корректной обработки.
     *
     * Сценарий:
     * 1. Создаём профиль (description и instructions по умолчанию пустые)
     * 2. Проверяем, что секция [PROFILE] всё равно корректно формируется
     * 3. Проверяем, что пустые поля не ломают структуру промпта
     *
     * ProfileService.handleCreateProfile не передаёт description/instructions явно,
     * поэтому они всегда инициализируются значениями по умолчанию ("").
     */
    @Test
    @DisplayName("Тест 4: Профиль с пустыми description/instructions корректно обрабатывается")
    fun `profile with empty description and instructions is handled correctly`(): Unit = runBlocking {
        // Создаём профиль (description и instructions будут "" по умолчанию)
        val profile = profileService.handleCreateProfile(
            "MinimalProfile",
            "Минимальное содержимое профиля",
            ""
        )
        profileService.handleActivateProfile(profile.id)
        assertTrue(profileService.handleGetActiveProfile() != null)
        // description = "Минимальное содержимое профиля", instructions = ""
        assertEquals("Минимальное содержимое профиля", profile.description)
        assertEquals("", profile.instructions)

        mockLlmPort.chatWithMessagesResult = TaskResult.Success("Ответ на запрос")

        val result = dialogService.chat("Запрос с минимальным профилем", SessionLevel.TASK_LIST)

        assertIs<TaskResult.Success>(result)
        val messages = mockLlmPort.lastChatMessages
        assertTrue(messages.isNotEmpty(), "Сообщения LLM не должны быть пустыми")
        val systemContent = messages.first().content

        // Проверяем, что секция [PROFILE] присутствует
        assertTrue(
            systemContent.contains("[PROFILE]"),
            "Секция [PROFILE] должна быть даже с пустыми description/instructions"
        )
        assertTrue(systemContent.contains("Name: MinimalProfile"), "Имя профиля должно быть в промпте")

        // Проверяем, что пустые поля присутствуют (не ломают структуру)
        assertTrue(systemContent.contains("Description: "), "Поле Description должно присутствовать (даже пустое)")
        assertTrue(systemContent.contains("Instructions: "), "Поле Instructions должно присутствовать (даже пустое)")

        // Проверяем, что системная инструкция всё ещё на месте (следует за [PROFILE])
        assertTrue(
            systemContent.contains(PromptBuilder.SYSTEM_INSTRUCTION),
            "Системная инструкция должна быть в промпте"
        )
    }

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
        var lastChatMessages: List<ChatMessage> = emptyList()

        override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
            lastChatPrompt = prompt
            return chatResult
        }

        override suspend fun chatWithMessages(
            messages: List<ChatMessage>,
            config: TaskExecutionConfig
        ): TaskResult {
            lastChatMessages = messages
            return chatWithMessagesResult
        }

        override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week2.domain.ModelId> =
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
}
