package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.DialogSummary
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/**
 * Тесты для [ConversationalAgent].
 *
 * Проверяет:
 * - Создание нового диалога при первом запросе
 * - Сохранение истории сообщений
 * - Переключение между диалогами
 * - Изоляцию контекста диалогов
 */
class ConversationalAgentTest {

    private class FakeDialogRepository : DialogRepository {
        val dialogs = mutableMapOf<DialogId, Dialog>()

        override suspend fun save(dialog: Dialog) {
            dialogs[dialog.id] = dialog
        }

        override suspend fun findById(id: DialogId): Dialog? {
            return dialogs[id]
        }

        override suspend fun findAll(): List<DialogSummary> {
            return dialogs.values.map { DialogSummary.fromDialog(it) }
        }

        override suspend fun delete(id: DialogId) {
            dialogs.remove(id)
        }
    }

    private class FakeLlmPort : LlmPort {
        var lastMessages: List<ChatMessage> = emptyList()
        var responseToReturn: TaskResult = TaskResult.Success("Test response")

        override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
            return responseToReturn
        }

        override suspend fun chatWithMessages(messages: List<ChatMessage>, config: TaskExecutionConfig): TaskResult {
            lastMessages = messages
            return responseToReturn
        }

        override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week1.domain.ModelId> {
            return emptyList()
        }
    }

    @Test
    fun `process creates new dialog on first request`() = runTest {
        val repository = FakeDialogRepository()
        val llmPort = FakeLlmPort()
        val agent = ConversationalAgent(llmPort, repository)
        val dialogId = DialogId("test-dialog-1")

        val result = agent.process(Prompt("Hello"), TaskExecutionConfig(), dialogId)

        assertIs<TaskResult.Success>(result)
        assertEquals(1, repository.dialogs.size)

        val dialog = repository.dialogs.values.first()
        assertEquals(2, dialog.messages.size) // user + assistant
        assertEquals(ChatRole.USER, dialog.messages[0].role)
        assertEquals("Hello", dialog.messages[0].content)
        assertEquals(ChatRole.ASSISTANT, dialog.messages[1].role)
        assertEquals("Test response", dialog.messages[1].content)
    }

    @Test
    fun `process adds messages to existing dialog`() = runTest {
        val repository = FakeDialogRepository()
        val llmPort = FakeLlmPort()
        val agent = ConversationalAgent(llmPort, repository)
        val dialogId = DialogId("test-dialog-1")

        // First request
        agent.process(Prompt("First message"), TaskExecutionConfig(), dialogId)

        // Second request
        agent.process(Prompt("Second message"), TaskExecutionConfig(), dialogId)

        val dialog = repository.findById(dialogId)
        assertNotNull(dialog)
        assertEquals(4, dialog.messages.size) // 2 user + 2 assistant
        assertEquals("First message", dialog.messages[0].content)
        assertEquals("Second message", dialog.messages[2].content)
    }

    @Test
    fun `process works with different dialogs independently`() = runTest {
        val repository = FakeDialogRepository()
        val llmPort = FakeLlmPort()
        val agent = ConversationalAgent(llmPort, repository)
        val dialog1Id = DialogId("dialog-1")
        val dialog2Id = DialogId("dialog-2")

        // Process in dialog 1
        agent.process(Prompt("Dialog 1 - Message 1"), TaskExecutionConfig(), dialog1Id)

        // Process in dialog 2
        agent.process(Prompt("Dialog 2 - Message 1"), TaskExecutionConfig(), dialog2Id)

        // Verify isolation
        val dialog1 = repository.findById(dialog1Id)
        val dialog2 = repository.findById(dialog2Id)
        assertNotNull(dialog1)
        assertNotNull(dialog2)

        assertEquals(2, dialog1.messages.size)
        assertEquals(2, dialog2.messages.size)
        assertTrue(dialog1.messages.any { it.content.contains("Dialog 1") })
        assertTrue(dialog2.messages.any { it.content.contains("Dialog 2") })
        assertTrue(dialog1.messages.none { it.content.contains("Dialog 2") })
        assertTrue(dialog2.messages.none { it.content.contains("Dialog 1") })
    }

    @Test
    fun `system prompt is included in messages`() = runTest {
        val repository = FakeDialogRepository()
        val llmPort = FakeLlmPort()
        val systemPrompt = "You are a helpful assistant."
        val agent = ConversationalAgent(llmPort, repository, systemPrompt)
        val dialogId = DialogId("test-dialog-1")

        agent.process(Prompt("Hello"), TaskExecutionConfig(), dialogId)

        // В момент вызова LLM messages содержит: system + user (assistant ещё не добавлен)
        assertEquals(2, llmPort.lastMessages.size)
        assertEquals(ChatRole.SYSTEM, llmPort.lastMessages[0].role)
        assertEquals(systemPrompt, llmPort.lastMessages[0].content)
        assertEquals(ChatRole.USER, llmPort.lastMessages[1].role)
        assertEquals("Hello", llmPort.lastMessages[1].content)
    }

    @Test
    fun `process sends full history to LLM on subsequent requests`() = runTest {
        val repository = FakeDialogRepository()
        val llmPort = FakeLlmPort()
        val agent = ConversationalAgent(llmPort, repository)
        val dialogId = DialogId("test-dialog-1")

        // First request
        agent.process(Prompt("First message"), TaskExecutionConfig(), dialogId)

        // Verify first request sent 1 user message to LLM
        assertEquals(1, llmPort.lastMessages.size)
        assertEquals("First message", llmPort.lastMessages[0].content)

        // Second request - should send full history (user1 + assistant1 + user2)
        agent.process(Prompt("Second message"), TaskExecutionConfig(), dialogId)

        // Verify LLM received full history: user1 + assistant1 + user2
        assertEquals(3, llmPort.lastMessages.size)
        assertEquals(ChatRole.USER, llmPort.lastMessages[0].role)
        assertEquals("First message", llmPort.lastMessages[0].content)
        assertEquals(ChatRole.ASSISTANT, llmPort.lastMessages[1].role)
        assertEquals("Test response", llmPort.lastMessages[1].content)
        assertEquals(ChatRole.USER, llmPort.lastMessages[2].role)
        assertEquals("Second message", llmPort.lastMessages[2].content)
    }

    @Test
    fun `process handles error response`() = runTest {
        val repository = FakeDialogRepository()
        val llmPort = FakeLlmPort()
        llmPort.responseToReturn = TaskResult.Error("LLM error")
        val agent = ConversationalAgent(llmPort, repository)
        val dialogId = DialogId("test-dialog-1")

        val result = agent.process(Prompt("Hello"), TaskExecutionConfig(), dialogId)

        assertIs<TaskResult.Error>(result)
        // Dialog should still be saved with user message
        assertEquals(1, repository.dialogs.size)
        val dialog = repository.dialogs.values.first()
        assertEquals(1, dialog.messages.size) // Only user message
        assertEquals(ChatRole.USER, dialog.messages[0].role)
    }

    @Test
    fun `process requires dialogId parameter`() = runTest {
        val repository = FakeDialogRepository()
        val llmPort = FakeLlmPort()
        val agent = ConversationalAgent(llmPort, repository)

        // ConversationalAgent должен требовать dialogId
        try {
            agent.process(Prompt("Hello"), TaskExecutionConfig(), null)
            throw AssertionError("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Ожидаемое исключение
        }
    }
}
