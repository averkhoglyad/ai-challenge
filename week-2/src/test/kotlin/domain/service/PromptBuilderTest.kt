package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("PromptBuilder")
class PromptBuilderTest {

    private val promptBuilder = PromptBuilder()

    // ═══════════════════════════════════════════════════════════════
    // buildPrompt
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildPrompt")
    inner class BuildPrompt {

        @Test
        @DisplayName("должен содержать системную инструкцию")
        fun `should contain system instruction`() {
            val result = promptBuilder.buildPrompt(null)

            assertContains(result, PromptBuilder.SYSTEM_INSTRUCTION)
        }

        @Test
        @DisplayName("должен включать контекст рабочей памяти")
        fun `should include working memory context`() {
            val sessionId = SessionId("test-session")
            val wm = WorkingMemory.create(sessionId)
            val message = Message.create(sessionId, MessageRole.USER, "Тестовое сообщение")
            val wmWithMsg = wm.addMessage(message)

            val result = promptBuilder.buildPrompt(wmWithMsg)

            assertContains(result, "Recent Messages:")
            assertContains(result, "Тестовое сообщение")
        }

        @Test
        @DisplayName("не должен включать WM-секцию при null")
        fun `should not include WM section when null`() {
            val result = promptBuilder.buildPrompt(null)

            assertFalse(result.contains("Recent Messages:"))
        }

        @Test
        @DisplayName("должен включать релевантные факты")
        fun `should include relevant facts`() {
            val facts = listOf(
                Fact(FactId("f1"), "Факт номер один", Instant.now()),
                Fact(FactId("f2"), "Факт номер два", Instant.now())
            )

            val result = promptBuilder.buildPrompt(null, relevantFacts = facts)

            assertContains(result, "=== Релевантные факты из базы знаний (LTM) ===")
            assertContains(result, "Факт номер один")
            assertContains(result, "Факт номер два")
        }

        @Test
        @DisplayName("не должен включать LTM-секцию при пустом списке")
        fun `should not include LTM section when empty`() {
            val result = promptBuilder.buildPrompt(null, relevantFacts = emptyList())

            assertFalse(result.contains("=== Релевантные факты из базы знаний (LTM) ==="))
        }

        @Test
        @DisplayName("должен включать историю диалога")
        fun `should include dialog history`() {
            val sessionId = SessionId("session-1")
            val messages = listOf(
                Message.create(sessionId, MessageRole.USER, "Привет"),
                Message.create(sessionId, MessageRole.ASSISTANT, "Здравствуйте")
            )

            val result = promptBuilder.buildPrompt(null, recentMessages = messages)

            assertContains(result, "=== История диалога (STM) ===")
            assertContains(result, "[Пользователь] Привет")
            assertContains(result, "[Ассистент] Здравствуйте")
        }

        @Test
        @DisplayName("не должен включать STM-секцию при пустом списке")
        fun `should not include STM section when empty`() {
            val result = promptBuilder.buildPrompt(null, recentMessages = emptyList())

            assertFalse(result.contains("=== История диалога (STM) ==="))
        }

        @Test
        @DisplayName("должен формировать полный промпт со всеми компонентами")
        fun `should build full prompt with all components`() {
            val sessionId = SessionId("s1")
            val wm = WorkingMemory.create(sessionId)
            val facts = listOf(Fact(FactId("f1"), "Важный факт", Instant.now()))
            val messages = listOf(Message.create(sessionId, MessageRole.USER, "Вопрос"))

            val result = promptBuilder.buildPrompt(wm, facts, messages)

            assertContains(result, PromptBuilder.SYSTEM_INSTRUCTION)
            assertContains(result, "=== Релевантные факты из базы знаний (LTM) ===")
            assertContains(result, "=== История диалога (STM) ===")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // buildPlanPrompt
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildPlanPrompt")
    inner class BuildPlanPrompt {

        @Test
        @DisplayName("должен содержать название задачи")
        fun `should contain task title`() {
            val result = promptBuilder.buildPlanPrompt("Создать REST API")

            assertContains(result, "Задача: Создать REST API")
        }

        @Test
        @DisplayName("должен содержать описание при наличии")
        fun `should contain description when provided`() {
            val result = promptBuilder.buildPlanPrompt(
                taskTitle = "Рефакторинг",
                taskDescription = "Улучшить читаемость кода"
            )

            assertContains(result, "Описание: Улучшить читаемость кода")
        }

        @Test
        @DisplayName("не должен содержать секцию описания при отсутствии")
        fun `should not contain description section when absent`() {
            val result = promptBuilder.buildPlanPrompt("Задача без описания")

            assertFalse(result.contains("Описание:"))
        }

        @Test
        @DisplayName("должен содержать инструкцию по формату ответа")
        fun `should contain response format instruction`() {
            val result = promptBuilder.buildPlanPrompt("Тест")

            assertContains(result, "Формат ответа")
            assertContains(result, "1. Шаг 1")
        }

        @Test
        @DisplayName("должен включать контекст рабочей памяти")
        fun `should include working memory context`() {
            val sessionId = SessionId("s1")
            val wm = WorkingMemory.create(sessionId).addMessage(
                Message.create(sessionId, MessageRole.USER, "Детали задачи")
            )

            val result = promptBuilder.buildPlanPrompt("Задача", workingMemory = wm)

            assertContains(result, "Контекст:")
        }

        @Test
        @DisplayName("должен включать релевантные факты")
        fun `should include relevant facts`() {
            val facts = listOf(Fact(FactId("f1"), "Полезный факт", Instant.now()))

            val result = promptBuilder.buildPlanPrompt("Задача", relevantFacts = facts)

            assertContains(result, "Релевантные факты:")
            assertContains(result, "Полезный факт")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // buildChatMessages
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildChatMessages")
    inner class BuildChatMessages {

        @Test
        @DisplayName("первое сообщение должно быть системным")
        fun `first message should be system`() {
            val messages = promptBuilder.buildChatMessages(
                workingMemory = null,
                userInput = "Привет"
            )

            assertTrue(messages.isNotEmpty())
            assertEquals(ChatRole.SYSTEM, messages.first().role)
        }

        @Test
        @DisplayName("последнее сообщение должно быть от пользователя")
        fun `last message should be user`() {
            val messages = promptBuilder.buildChatMessages(
                workingMemory = null,
                userInput = "Привет"
            )

            assertTrue(messages.isNotEmpty())
            assertEquals(ChatRole.USER, messages.last().role)
            assertEquals("Привет", messages.last().content)
        }

        @Test
        @DisplayName("должен включать историю диалога между системным и пользовательским")
        fun `should include dialog history between system and user messages`() {
            val sessionId = SessionId("s1")
            val historyMessages = listOf(
                Message.create(sessionId, MessageRole.USER, "Вопрос 1"),
                Message.create(sessionId, MessageRole.ASSISTANT, "Ответ 1")
            )

            val messages = promptBuilder.buildChatMessages(
                workingMemory = null,
                recentMessages = historyMessages,
                userInput = "Вопрос 2"
            )

            assertEquals(4, messages.size) // system + history(2) + user
            assertEquals(ChatRole.SYSTEM, messages[0].role)
            assertEquals(ChatRole.USER, messages[1].role)
            assertEquals("Вопрос 1", messages[1].content)
            assertEquals(ChatRole.ASSISTANT, messages[2].role)
            assertEquals("Ответ 1", messages[2].content)
            assertEquals(ChatRole.USER, messages[3].role)
            assertEquals("Вопрос 2", messages[3].content)
        }

        @Test
        @DisplayName("системное сообщение должно содержать WM-контекст")
        fun `system message should contain WM context`() {
            val sessionId = SessionId("s1")
            val wm = WorkingMemory.create(sessionId)
            val wmWithMsg = wm.addMessage(Message.create(sessionId, MessageRole.USER, "Контекст задачи"))

            val messages = promptBuilder.buildChatMessages(
                workingMemory = wmWithMsg,
                userInput = "Продолжи"
            )

            assertContains(messages.first().content, "Recent Messages:")
        }

        @Test
        @DisplayName("должен корректно обрабатывать пустой ввод")
        fun `should handle empty input`() {
            val messages = promptBuilder.buildChatMessages(
                workingMemory = null,
                userInput = ""
            )

            assertEquals(2, messages.size) // system + empty user
            assertEquals(ChatRole.USER, messages.last().role)
            assertEquals("", messages.last().content)
        }
    }
}
