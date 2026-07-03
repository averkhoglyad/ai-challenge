package io.averkhogliad.ai.challenge.week4.cli.unit.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatRole
import io.averkhogliad.ai.challenge.week4.cli.domain.service.PromptBuilder
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.time.Instant

class PromptBuilderTest : FreeSpec({

    val promptBuilder = PromptBuilder()
    val now = Instant.now()

    "buildPrompt" - {

        "PROFILE section is absent when profile is null" {
            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = null
            )

            // then
            prompt.contains("[PROFILE]") shouldBe false
            prompt.contains(PromptBuilder.SYSTEM_INSTRUCTION) shouldBe true
        }

        "PROFILE section is present before SYSTEM when profile is not null" {
            // given
            val profile = Profile(
                id = ProfileId("test-profile"),
                name = "Тестовый",
                description = "Описание",
                instructions = "Инструкции",
                createdAt = now,
                updatedAt = now
            )

            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = profile
            )

            // then
            prompt.contains("[PROFILE]") shouldBe true
            val profileIndex = prompt.indexOf("[PROFILE]")
            val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
            (profileIndex < systemIndex) shouldBe true
        }

        "PROFILE section contains profile name" {
            // given
            val profileName = "Эксперт по архитектуре"
            val profile = Profile(
                id = ProfileId("name-test"),
                name = profileName,
                description = "Описание",
                instructions = "Инструкции",
                createdAt = now,
                updatedAt = now
            )

            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = profile
            )

            // then
            prompt.contains("Name: $profileName") shouldBe true
        }

        "PROFILE section contains profile description" {
            // given
            val profileDescription = "Этот профиль предназначен для тестирования"
            val profile = Profile(
                id = ProfileId("desc-test"),
                name = "TestDesc",
                description = profileDescription,
                instructions = "Инструкции",
                createdAt = now,
                updatedAt = now
            )

            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = profile
            )

            // then
            prompt.contains("Description: $profileDescription") shouldBe true
        }

        "PROFILE section contains profile instructions" {
            // given
            val profileInstructions = "Отвечай кратко. Используй техническую терминологию."
            val profile = Profile(
                id = ProfileId("instr-test"),
                name = "TestInstr",
                description = "Описание",
                instructions = profileInstructions,
                createdAt = now,
                updatedAt = now
            )

            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = profile
            )

            // then
            prompt.contains("Instructions: $profileInstructions") shouldBe true
        }

        "empty description and instructions are handled gracefully" {
            // given
            val profile = Profile(
                id = ProfileId("empty-fields"),
                name = "Minimal",
                description = "",
                instructions = "",
                createdAt = now,
                updatedAt = now
            )

            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = profile
            )

            // then
            prompt.contains("[PROFILE]") shouldBe true
            prompt.contains("Name: Minimal") shouldBe true
            prompt.contains("Description: ") shouldBe true
            prompt.contains("Instructions: ") shouldBe true
            prompt.contains(PromptBuilder.SYSTEM_INSTRUCTION) shouldBe true
        }

        "INVARIANTS section is present before PROFILE and SYSTEM when invariants are not empty" {
            // given
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

            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = profile,
                invariants = invariants
            )

            // then
            prompt.contains("[INVARIANTS - DO NOT VIOLATE]") shouldBe true
            prompt.contains("1. Использовать только PostgreSQL") shouldBe true
            prompt.contains("2. Запрещены глобальные переменные") shouldBe true
            val invariantsIndex = prompt.indexOf("[INVARIANTS - DO NOT VIOLATE]")
            val profileIndex = prompt.indexOf("[PROFILE]")
            val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
            (invariantsIndex < profileIndex) shouldBe true
            (profileIndex < systemIndex) shouldBe true
        }

        "INVARIANTS section is not inserted before SYSTEM when invariants are empty" {
            // when
            val prompt = promptBuilder.buildPrompt(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                profile = null,
                invariants = emptyList()
            )

            // then
            val systemIndex = prompt.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
            (systemIndex >= 0) shouldBe true
            val firstNumberedItemBeforeSystem = prompt.substring(0, systemIndex).contains("1. ")
            firstNumberedItemBeforeSystem shouldBe false
        }
    }

    "buildChatMessages" - {

        "correctly passes profile to system message" {
            // given
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

            // when
            val messages = promptBuilder.buildChatMessages(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = historyMessages,
                userInput = "Новый вопрос",
                profile = profile
            )

            // then
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

        "message order is SYSTEM first then USER last with PROFILE section inside SYSTEM" {
            // given
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

            // when
            val messages = promptBuilder.buildChatMessages(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = historyMessages,
                userInput = "Ввод пользователя",
                profile = profile
            )

            // then
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

        "includes INVARIANTS in system message" {
            // given
            val invariants = listOf(
                Invariant(id = InvariantId(1), rule = "Только RESTful API", createdAt = now)
            )

            // when
            val messages = promptBuilder.buildChatMessages(
                workingMemory = null,
                relevantFacts = emptyList(),
                recentMessages = emptyList(),
                userInput = "Как подключить MySQL?",
                profile = null,
                invariants = invariants
            )

            // then
            val systemMessage = messages.first()
            systemMessage.role shouldBe ChatRole.SYSTEM
            systemMessage.content.contains("[INVARIANTS - DO NOT VIOLATE]") shouldBe true
            systemMessage.content.contains("1. Только RESTful API") shouldBe true
            val invariantsIndex = systemMessage.content.indexOf("[INVARIANTS - DO NOT VIOLATE]")
            val systemIndex = systemMessage.content.indexOf(PromptBuilder.SYSTEM_INSTRUCTION)
            (invariantsIndex < systemIndex) shouldBe true
        }
    }

    "SYSTEM_INSTRUCTION" - {

        "contains refusal rules section" {
            // given
            val instruction = PromptBuilder.SYSTEM_INSTRUCTION

            // then
            instruction.contains("ПРАВИЛА ОБРАБОТКИ ИНВАРИАНТОВ") shouldBe true
            instruction.contains("ЖЁСТКИЕ ПРАВИЛА") shouldBe true
            instruction.contains("НЕ ИМЕЕШЬ ПРАВА НАРУШАТЬ") shouldBe true
        }

        "contains refusal steps and format" {
            // given
            val instruction = PromptBuilder.SYSTEM_INSTRUCTION

            // then
            instruction.contains("ВСЕГДА проверяй") shouldBe true
            instruction.contains("ОТКАЖИ в выполнении запроса") shouldBe true
            instruction.contains("ЯВНО укажи, какой именно инвариант нарушен") shouldBe true
            instruction.contains("Предложи АЛЬТЕРНАТИВУ в рамках разрешённых границ") shouldBe true
            instruction.contains("Формат отказа:") shouldBe true
            instruction.contains("❌ Нарушение инварианта:") shouldBe true
            instruction.contains("💡 Альтернатива:") shouldBe true
        }

        "contains few-shot refusal examples" {
            // given
            val instruction = PromptBuilder.SYSTEM_INSTRUCTION

            // then
            instruction.contains("Как подключить базу данных MySQL?") shouldBe true
            instruction.contains("Использовать только PostgreSQL. MySQL запрещён") shouldBe true
            instruction.contains("Напиши код с глобальной переменной") shouldBe true
            instruction.contains("Запрещено использование глобальных переменных") shouldBe true
            instruction.contains("Как оптимизировать запросы к PostgreSQL?") shouldBe true
        }

        "preserves original constraints" {
            // given
            val instruction = PromptBuilder.SYSTEM_INSTRUCTION

            // then
            instruction.contains("Отвечай только на русском языке") shouldBe true
            instruction.contains("Будь краток и по существу") shouldBe true
            instruction.contains("Если не знаешь ответа, честно сообщи об этом") shouldBe true
            instruction.contains("Не придумывай факты") shouldBe true
        }
    }
})
