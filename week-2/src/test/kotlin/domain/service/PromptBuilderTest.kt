package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.*
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptBuilderTest {

    private val promptBuilder = PromptBuilder()
    private val now = Instant.now()

    // ========================================================================
    // Тест 1: Если profile == null, секция [PROFILE] отсутствует
    // ========================================================================

    @Test
    fun `profile is null - PROFILE section is absent`() {
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = null
        )
        assertFalse(
            prompt.contains("[PROFILE]"),
            "Если profile == null, секция [PROFILE] должна отсутствовать в промпте"
        )
        assertTrue(
            prompt.contains(PromptBuilder.SYSTEM_INSTRUCTION),
            "Системная инструкция должна присутствовать даже без профиля"
        )
    }

    // ========================================================================
    // Тест 2: Если profile != null, секция [PROFILE] присутствует перед SYSTEM
    // ========================================================================

    @Test
    fun `profile is not null - PROFILE section is present before SYSTEM`() {
        val profile = Profile(
            id = ProfileId("test-profile"),
            name = "Тестовый",
            description = "Описание",
            instructions = "Инструкции",
            createdAt = now,
            updatedAt = now
        )
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = profile
        )
        assertTrue(
            prompt.contains("[PROFILE]"),
            "Если profile != null, секция [PROFILE] должна присутствовать"
        )
        val profileIndex = prompt.indexOf("[PROFILE]")
        val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        assertTrue(
            profileIndex < systemIndex,
            "Секция [PROFILE] (индекс $profileIndex) должна идти перед SYSTEM (индекс $systemIndex)"
        )
    }

    // ========================================================================
    // Тест 3: Секция [PROFILE] содержит имя профиля
    // ========================================================================

    @Test
    fun `PROFILE section contains profile name`() {
        val profileName = "Эксперт по архитектуре"
        val profile = Profile(
            id = ProfileId("name-test"),
            name = profileName,
            description = "Описание",
            instructions = "Инструкции",
            createdAt = now,
            updatedAt = now
        )
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = profile
        )
        assertTrue(
            prompt.contains("Name: $profileName"),
            "Секция [PROFILE] должна содержать имя профиля: 'Name: $profileName'"
        )
    }

    // ========================================================================
    // Тест 4: Секция [PROFILE] содержит описание профиля
    // ========================================================================

    @Test
    fun `PROFILE section contains profile description`() {
        val profileDescription = "Этот профиль предназначен для тестирования"
        val profile = Profile(
            id = ProfileId("desc-test"),
            name = "TestDesc",
            description = profileDescription,
            instructions = "Инструкции",
            createdAt = now,
            updatedAt = now
        )
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = profile
        )
        assertTrue(
            prompt.contains("Description: $profileDescription"),
            "Секция [PROFILE] должна содержать описание профиля: 'Description: $profileDescription'"
        )
    }

    // ========================================================================
    // Тест 5: Секция [PROFILE] содержит инструкции профиля
    // ========================================================================

    @Test
    fun `PROFILE section contains profile instructions`() {
        val profileInstructions = "Отвечай кратко. Используй техническую терминологию."
        val profile = Profile(
            id = ProfileId("instr-test"),
            name = "TestInstr",
            description = "Описание",
            instructions = profileInstructions,
            createdAt = now,
            updatedAt = now
        )
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = profile
        )
        assertTrue(
            prompt.contains("Instructions: $profileInstructions"),
            "Секция [PROFILE] должна содержать инструкции профиля: 'Instructions: $profileInstructions'"
        )
    }

    // ========================================================================
    // Тест 6: Пустые строки в полях профиля корректно обрабатываются
    // ========================================================================

    @Test
    fun `empty description and instructions are handled gracefully`() {
        val profile = Profile(
            id = ProfileId("empty-fields"),
            name = "Minimal",
            description = "",
            instructions = "",
            createdAt = now,
            updatedAt = now
        )
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = profile
        )
        assertTrue(prompt.contains("[PROFILE]"), "[PROFILE] должен присутствовать")
        assertTrue(prompt.contains("Name: Minimal"), "Имя профиля должно быть в промпте")
        assertTrue(prompt.contains("Description: "), "Пустое описание должно присутствовать (как 'Description: ')")
        assertTrue(prompt.contains("Instructions: "), "Пустые инструкции должны присутствовать (как 'Instructions: ')")
        assertTrue(prompt.contains(PromptBuilder.SYSTEM_INSTRUCTION), "Системная инструкция должна присутствовать")
    }

    // ========================================================================
    // Тест 7: buildChatMessages() корректно передаёт профиль
    // ========================================================================

    @Test
    fun `buildChatMessages correctly passes profile to system message`() {
        val profile = Profile(
            id = ProfileId("chat-test"),
            name = "ChatProfile",
            description = "Описание чат-профиля",
            instructions = "Отвечай дружелюбно",
            createdAt = now,
            updatedAt = now
        )
        val sessionId = SessionId("test-session")
        val historyMessages = listOf(
            Message(
                id = "hist-1",
                sessionId = sessionId,
                role = MessageRole.USER,
                content = "Предыдущий вопрос",
                timestamp = now
            ),
            Message(
                id = "hist-2",
                sessionId = sessionId,
                role = MessageRole.ASSISTANT,
                content = "Предыдущий ответ",
                timestamp = now
            )
        )
        val messages = promptBuilder.buildChatMessages(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = historyMessages,
            userInput = "Новый вопрос",
            profile = profile
        )

        val systemMessage = messages.first()
        assertEquals(ChatRole.SYSTEM, systemMessage.role, "Первое сообщение должно быть SYSTEM")
        assertTrue(
            systemMessage.content.contains("[PROFILE]"),
            "Системное сообщение должно содержать секцию [PROFILE]"
        )
        assertTrue(
            systemMessage.content.contains("ChatProfile"),
            "Системное сообщение должно содержать имя профиля"
        )
        assertTrue(
            systemMessage.content.contains("Описание чат-профиля"),
            "Системное сообщение должно содержать описание профиля"
        )
        assertTrue(
            systemMessage.content.contains("Отвечай дружелюбно"),
            "Системное сообщение должно содержать инструкции профиля"
        )

        val userMessage = messages.last()
        assertEquals(ChatRole.USER, userMessage.role, "Последнее сообщение должно быть USER")
        assertEquals("Новый вопрос", userMessage.content, "Содержимое пользовательского сообщения должно совпадать")

        assertEquals(4, messages.size, "Должно быть 4 сообщения: system + 2 history + user")
    }

    // ========================================================================
    // Тест 8: Порядок сообщений: SYSTEM → (история) → USER, [PROFILE] внутри SYSTEM
    // ========================================================================

    @Test
    fun `message order is SYSTEM first then USER last - PROFILE section is inside SYSTEM`() {
        val profile = Profile(
            id = ProfileId("order-test"),
            name = "OrderProfile",
            description = "Тест порядка",
            instructions = "",
            createdAt = now,
            updatedAt = now
        )
        val sessionId = SessionId("order-session")
        val historyMessages = listOf(
            Message(
                id = "hist-msg",
                sessionId = sessionId,
                role = MessageRole.USER,
                content = "История",
                timestamp = now
            )
        )
        val messages = promptBuilder.buildChatMessages(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = historyMessages,
            userInput = "Ввод пользователя",
            profile = profile
        )

        assertEquals(3, messages.size, "Должно быть 3 сообщения: system + history + user")

        assertEquals(ChatRole.SYSTEM, messages[0].role, "Первое сообщение должно быть SYSTEM")
        assertTrue(messages[0].content.contains("[PROFILE]"), "SYSTEM должен содержать [PROFILE]")
        assertTrue(
            messages[0].content.contains(PromptBuilder.SYSTEM_INSTRUCTION),
            "SYSTEM должен содержать системную инструкцию"
        )

        val profileSectionIndex = messages[0].content.indexOf("[PROFILE]")
        val systemInstrIndex = messages[0].content.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        assertTrue(
            profileSectionIndex < systemInstrIndex,
            "[PROFILE] (индекс $profileSectionIndex) должен быть перед системной инструкцией (индекс $systemInstrIndex)"
        )

        assertEquals(ChatRole.USER, messages[1].role, "Второе сообщение должно быть из истории (USER)")
        assertEquals("История", messages[1].content)

        assertEquals(ChatRole.USER, messages[2].role, "Последнее сообщение должно быть USER")
        assertEquals("Ввод пользователя", messages[2].content)
    }
}
