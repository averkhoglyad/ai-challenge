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

    // ========================================================================
    // Тест 9: ИНВАРИАНТЫ — если invariants не пусты, секция [INVARIANTS] присутствует
    // ========================================================================

    @Test
    fun `invariants not empty - INVARIANTS section is present before PROFILE and SYSTEM`() {
        val invariants = listOf(
            Invariant(id = InvariantId(1), rule = "Использовать только PostgreSQL", createdAt = now),
            Invariant(id = InvariantId(2), rule = "Запрещены глобальные переменные", createdAt = now)
        )
        val profile = Profile(
            id = ProfileId("inv-test"),
            name = "TestProfile",
            description = "Описание",
            instructions = "Инструкции",
            createdAt = now,
            updatedAt = now
        )
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = profile,
            invariants = invariants
        )
        assertTrue(
            prompt.contains("[INVARIANTS - DO NOT VIOLATE]"),
            "Если invariants не пусты, секция [INVARIANTS - DO NOT VIOLATE] должна присутствовать"
        )
        assertTrue(
            prompt.contains("1. Использовать только PostgreSQL"),
            "Секция [INVARIANTS] должна содержать первый инвариант"
        )
        assertTrue(
            prompt.contains("2. Запрещены глобальные переменные"),
            "Секция [INVARIANTS] должна содержать второй инвариант"
        )
        val invariantsIndex = prompt.indexOf("[INVARIANTS - DO NOT VIOLATE]")
        val profileIndex = prompt.indexOf("[PROFILE]")
        val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        assertTrue(
            invariantsIndex < profileIndex,
            "Секция [INVARIANTS] (индекс $invariantsIndex) должна идти перед [PROFILE] (индекс $profileIndex)"
        )
        assertTrue(
            profileIndex < systemIndex,
            "Секция [PROFILE] (индекс $profileIndex) должна идти перед SYSTEM (индекс $systemIndex)"
        )
    }

    // ========================================================================
    // Тест 10: ИНВАРИАНТЫ — если invariants пусты, секция НЕ вставляется перед SYSTEM
    // ========================================================================

    @Test
    fun `invariants empty - INVARIANTS section is not inserted before SYSTEM`() {
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = null,
            invariants = emptyList()
        )
        // SYSTEM_INSTRUCTION содержит упоминания [INVARIANTS - DO NOT VIOLATE]
        // в правилах отказов, но сама секция инвариантов не вставляется перед SYSTEM.
        // Проверяем, что промпт начинается с SYSTEM_INSTRUCTION, а не с [INVARIANTS].
        val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        assertTrue(systemIndex >= 0, "SYSTEM_INSTRUCTION должен присутствовать в промпте")
        // "1. " в контексте секции инвариантов (нумерованный список правил)
        // не должно быть перед SYSTEM_INSTRUCTION — значит секция не вставлена.
        val firstNumberedItemBeforeSystem = prompt.substring(0, systemIndex).contains("1. ")
        assertFalse(
            firstNumberedItemBeforeSystem,
            "Если invariants пусты, нумерованный список инвариантов не должен быть перед SYSTEM_INSTRUCTION"
        )
    }

    // ========================================================================
    // Тест 11: ИНВАРИАНТЫ — buildChatMessages передаёт invariants в системный промпт
    // ========================================================================

    @Test
    fun `buildChatMessages includes INVARIANTS in system message`() {
        val invariants = listOf(
            Invariant(id = InvariantId(1), rule = "Только RESTful API", createdAt = now)
        )
        val messages = promptBuilder.buildChatMessages(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            userInput = "Как подключить MySQL?",
            profile = null,
            invariants = invariants
        )
        val systemMessage = messages.first()
        assertEquals(ChatRole.SYSTEM, systemMessage.role, "Первое сообщение должно быть SYSTEM")
        assertTrue(
            systemMessage.content.contains("[INVARIANTS - DO NOT VIOLATE]"),
            "Системное сообщение должно содержать секцию [INVARIANTS]"
        )
        assertTrue(
            systemMessage.content.contains("1. Только RESTful API"),
            "Системное сообщение должно содержать текст инварианта"
        )
        // INVARIANTS должен быть перед SYSTEM_INSTRUCTION
        val invariantsIndex = systemMessage.content.indexOf("[INVARIANTS - DO NOT VIOLATE]")
        val systemIndex = systemMessage.content.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        assertTrue(
            invariantsIndex < systemIndex,
            "[INVARIANTS] (индекс $invariantsIndex) должен быть перед SYSTEM (индекс $systemIndex)"
        )
    }

    // ========================================================================
    // Тест 12: US-LLM-2 — SYSTEM_INSTRUCTION содержит правила обработки инвариантов
    // ========================================================================

    @Test
    fun `SYSTEM_INSTRUCTION contains refusal rules section`() {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        assertTrue(
            instruction.contains("ПРАВИЛА ОБРАБОТКИ ИНВАРИАНТОВ"),
            "SYSTEM_INSTRUCTION должен содержать секцию 'ПРАВИЛА ОБРАБОТКИ ИНВАРИАНТОВ'"
        )
        assertTrue(
            instruction.contains("ЖЁСТКИЕ ПРАВИЛА"),
            "SYSTEM_INSTRUCTION должен подчёркивать жёсткость инвариантов"
        )
        assertTrue(
            instruction.contains("НЕ ИМЕЕШЬ ПРАВА НАРУШАТЬ"),
            "SYSTEM_INSTRUCTION должен явно запрещать нарушение инвариантов"
        )
    }

    // ========================================================================
    // Тест 13: US-LLM-2 — SYSTEM_INSTRUCTION содержит шаги проверки и формат отказа
    // ========================================================================

    @Test
    fun `SYSTEM_INSTRUCTION contains refusal steps and format`() {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        assertTrue(
            instruction.contains("Перед ответом ВСЕГДА проверяй"),
            "SYSTEM_INSTRUCTION должен требовать проверки перед каждым ответом"
        )
        assertTrue(
            instruction.contains("ОТКАЖИ в выполнении запроса"),
            "SYSTEM_INSTRUCTION должен предписывать отказ при конфликте"
        )
        assertTrue(
            instruction.contains("ЯВНО укажи, какой именно инвариант нарушен"),
            "SYSTEM_INSTRUCTION должен требовать указания нарушенного инварианта"
        )
        assertTrue(
            instruction.contains("Предложи АЛЬТЕРНАТИВУ в рамках разрешённых границ"),
            "SYSTEM_INSTRUCTION должен требовать предложения альтернативы"
        )
        assertTrue(
            instruction.contains("Формат отказа:"),
            "SYSTEM_INSTRUCTION должен содержать формат отказа"
        )
        assertTrue(
            instruction.contains("❌ Нарушение инварианта:"),
            "SYSTEM_INSTRUCTION должен содержать шаблон с маркером ❌"
        )
        assertTrue(
            instruction.contains("💡 Альтернатива:"),
            "SYSTEM_INSTRUCTION должен содержать шаблон с маркером 💡"
        )
    }

    // ========================================================================
    // Тест 14: US-LLM-2 — SYSTEM_INSTRUCTION содержит few-shot примеры отказов
    // ========================================================================

    @Test
    fun `SYSTEM_INSTRUCTION contains few-shot refusal examples`() {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        // Пример 1: конфликт с MySQL
        assertTrue(
            instruction.contains("Как подключить базу данных MySQL?"),
            "SYSTEM_INSTRUCTION должен содержать few-shot пример 1 (MySQL)"
        )
        assertTrue(
            instruction.contains("Использовать только PostgreSQL. MySQL запрещён"),
            "SYSTEM_INSTRUCTION должен содержать пример инварианта в примере 1"
        )
        // Пример 2: конфликт с глобальными переменными
        assertTrue(
            instruction.contains("Напиши код с глобальной переменной"),
            "SYSTEM_INSTRUCTION должен содержать few-shot пример 2 (глобальная переменная)"
        )
        assertTrue(
            instruction.contains("Запрещено использование глобальных переменных"),
            "SYSTEM_INSTRUCTION должен содержать пример инварианта в примере 2"
        )
        // Пример 3: без конфликта
        assertTrue(
            instruction.contains("Как оптимизировать запросы к PostgreSQL?"),
            "SYSTEM_INSTRUCTION должен содержать few-shot пример 3 (без конфликта)"
        )
    }

    // ========================================================================
    // Тест 15: US-LLM-2 — SYSTEM_INSTRUCTION содержит оригинальные ограничения
    // ========================================================================

    @Test
    fun `SYSTEM_INSTRUCTION preserves original constraints`() {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        assertTrue(
            instruction.contains("Отвечай только на русском языке"),
            "SYSTEM_INSTRUCTION должен сохранять требование русского языка"
        )
        assertTrue(
            instruction.contains("Будь краток и по существу"),
            "SYSTEM_INSTRUCTION должен сохранять требование краткости"
        )
        assertTrue(
            instruction.contains("Если не знаешь ответа, честно сообщи об этом"),
            "SYSTEM_INSTRUCTION должен сохранять требование честности"
        )
        assertTrue(
            instruction.contains("Не придумывай факты"),
            "SYSTEM_INSTRUCTION должен сохранять запрет на выдумывание фактов"
        )
    }
}
