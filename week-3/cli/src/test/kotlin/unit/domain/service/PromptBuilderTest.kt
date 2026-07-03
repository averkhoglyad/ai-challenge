package io.averkhogliad.ai.challenge.week3.cli.unit.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.service.*

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PromptBuilderTest : FreeSpec({

    val promptBuilder = PromptBuilder()
    val now = Instant.now()

    // ========================================================================
    // Тест 1: Если profile == null, секция [PROFILE] отсутствует
    // ========================================================================

    "profile is null - PROFILE section is absent" {
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = null
        )
        prompt.contains("[PROFILE]") shouldBe false
        prompt.contains(PromptBuilder.SYSTEM_INSTRUCTION) shouldBe true
    }

    // ========================================================================
    // Тест 2: Если profile != null, секция [PROFILE] присутствует перед SYSTEM
    // ========================================================================

    "profile is not null - PROFILE section is present before SYSTEM" {
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
        prompt.contains("[PROFILE]") shouldBe true
        val profileIndex = prompt.indexOf("[PROFILE]")
        val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        (profileIndex < systemIndex) shouldBe true
    }

    // ========================================================================
    // Тест 3: Секция [PROFILE] содержит имя профиля
    // ========================================================================

    "PROFILE section contains profile name" {
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
        prompt.contains("Name: $profileName") shouldBe true
    }

    // ========================================================================
    // Тест 4: Секция [PROFILE] содержит описание профиля
    // ========================================================================

    "PROFILE section contains profile description" {
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
        prompt.contains("Description: $profileDescription") shouldBe true
    }

    // ========================================================================
    // Тест 5: Секция [PROFILE] содержит инструкции профиля
    // ========================================================================

    "PROFILE section contains profile instructions" {
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
        prompt.contains("Instructions: $profileInstructions") shouldBe true
    }

    // ========================================================================
    // Тест 6: Пустые строки в полях профиля корректно обрабатываются
    // ========================================================================

    "empty description and instructions are handled gracefully" {
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
        prompt.contains("[PROFILE]") shouldBe true
        prompt.contains("Name: Minimal") shouldBe true
        prompt.contains("Description: ") shouldBe true
        prompt.contains("Instructions: ") shouldBe true
        prompt.contains(PromptBuilder.SYSTEM_INSTRUCTION) shouldBe true
    }

    // ========================================================================
    // Тест 7: buildChatMessages() корректно передаёт профиль
    // ========================================================================

    "buildChatMessages correctly passes profile to system message" {
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
        systemMessage.role shouldBe ChatRole.SYSTEM
        systemMessage.content.contains("[PROFILE]") shouldBe true
        systemMessage.content.contains("ChatProfile") shouldBe true
        systemMessage.content.contains("Описание чат-профиля") shouldBe true
        systemMessage.content.contains("Отвечай дружелюбно") shouldBe true

        val userMessage = messages.last()
        userMessage.role shouldBe ChatRole.USER
        userMessage.content shouldBe "Новый вопрос"

        messages.size shouldBe 4
    }

    // ========================================================================
    // Тест 8: Порядок сообщений: SYSTEM → (история) → USER, [PROFILE] внутри SYSTEM
    // ========================================================================

    "message order is SYSTEM first then USER last - PROFILE section is inside SYSTEM" {
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

        messages.size shouldBe 3

        messages[0].role shouldBe ChatRole.SYSTEM
        messages[0].content.contains("[PROFILE]") shouldBe true
        messages[0].content.contains(PromptBuilder.SYSTEM_INSTRUCTION) shouldBe true

        val profileSectionIndex = messages[0].content.indexOf("[PROFILE]")
        val systemInstrIndex = messages[0].content.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        (profileSectionIndex < systemInstrIndex) shouldBe true

        messages[1].role shouldBe ChatRole.USER
        messages[1].content shouldBe "История"

        messages[2].role shouldBe ChatRole.USER
        messages[2].content shouldBe "Ввод пользователя"
    }

    // ========================================================================
    // Тест 9: ИНВАРИАНТЫ — если invariants не пусты, секция [INVARIANTS] присутствует
    // ========================================================================

    "invariants not empty - INVARIANTS section is present before PROFILE and SYSTEM" {
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
        prompt.contains("[INVARIANTS - DO NOT VIOLATE]") shouldBe true
        prompt.contains("1. Использовать только PostgreSQL") shouldBe true
        prompt.contains("2. Запрещены глобальные переменные") shouldBe true
        val invariantsIndex = prompt.indexOf("[INVARIANTS - DO NOT VIOLATE]")
        val profileIndex = prompt.indexOf("[PROFILE]")
        val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        (invariantsIndex < profileIndex) shouldBe true
        (profileIndex < systemIndex) shouldBe true
    }

    // ========================================================================
    // Тест 10: ИНВАРИАНТЫ — если invariants пусты, секция НЕ вставляется перед SYSTEM
    // ========================================================================

    "invariants empty - INVARIANTS section is not inserted before SYSTEM" {
        val prompt = promptBuilder.buildPrompt(
            workingMemory = null,
            relevantFacts = emptyList(),
            recentMessages = emptyList(),
            profile = null,
            invariants = emptyList()
        )
        val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        (systemIndex >= 0) shouldBe true
        val firstNumberedItemBeforeSystem = prompt.substring(0, systemIndex).contains("1. ")
        firstNumberedItemBeforeSystem shouldBe false
    }

    // ========================================================================
    // Тест 11: ИНВАРИАНТЫ — buildChatMessages передаёт invariants в системный промпт
    // ========================================================================

    "buildChatMessages includes INVARIANTS in system message" {
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
        systemMessage.role shouldBe ChatRole.SYSTEM
        systemMessage.content.contains("[INVARIANTS - DO NOT VIOLATE]") shouldBe true
        systemMessage.content.contains("1. Только RESTful API") shouldBe true
        val invariantsIndex = systemMessage.content.indexOf("[INVARIANTS - DO NOT VIOLATE]")
        val systemIndex = systemMessage.content.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
        (invariantsIndex < systemIndex) shouldBe true
    }

    // ========================================================================
    // Тест 12: US-LLM-2 — SYSTEM_INSTRUCTION содержит правила обработки инвариантов
    // ========================================================================

    "SYSTEM_INSTRUCTION contains refusal rules section" {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        instruction.contains("ПРАВИЛА ОБРАБОТКИ ИНВАРИАНТОВ") shouldBe true
        instruction.contains("ЖЁСТКИЕ ПРАВИЛА") shouldBe true
        instruction.contains("НЕ ИМЕЕШЬ ПРАВА НАРУШАТЬ") shouldBe true
    }

    // ========================================================================
    // Тест 13: US-LLM-2 — SYSTEM_INSTRUCTION содержит шаги проверки и формат отказа
    // ========================================================================

    "SYSTEM_INSTRUCTION contains refusal steps and format" {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        instruction.contains("ВСЕГДА проверяй") shouldBe true
        instruction.contains("ОТКАЖИ в выполнении запроса") shouldBe true
        instruction.contains("ЯВНО укажи, какой именно инвариант нарушен") shouldBe true
        instruction.contains("Предложи АЛЬТЕРНАТИВУ в рамках разрешённых границ") shouldBe true
        instruction.contains("Формат отказа:") shouldBe true
        instruction.contains("❌ Нарушение инварианта:") shouldBe true
        instruction.contains("💡 Альтернатива:") shouldBe true
    }

    // ========================================================================
    // Тест 14: US-LLM-2 — SYSTEM_INSTRUCTION содержит few-shot примеры отказов
    // ========================================================================

    "SYSTEM_INSTRUCTION contains few-shot refusal examples" {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        instruction.contains("Как подключить базу данных MySQL?") shouldBe true
        instruction.contains("Использовать только PostgreSQL. MySQL запрещён") shouldBe true
        instruction.contains("Напиши код с глобальной переменной") shouldBe true
        instruction.contains("Запрещено использование глобальных переменных") shouldBe true
        instruction.contains("Как оптимизировать запросы к PostgreSQL?") shouldBe true
    }

    // ========================================================================
    // Тест 15: US-LLM-2 — SYSTEM_INSTRUCTION содержит оригинальные ограничения
    // ========================================================================

    "SYSTEM_INSTRUCTION preserves original constraints" {
        val instruction = PromptBuilder.SYSTEM_INSTRUCTION
        instruction.contains("Отвечай только на русском языке") shouldBe true
        instruction.contains("Будь краток и по существу") shouldBe true
        instruction.contains("Если не знаешь ответа, честно сообщи об этом") shouldBe true
        instruction.contains("Не придумывай факты") shouldBe true
    }
})
