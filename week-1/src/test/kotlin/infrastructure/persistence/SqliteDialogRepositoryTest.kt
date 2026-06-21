package io.averkhogliad.ai.challenge.week1.infrastructure.persistence

import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.time.Instant
import kotlin.test.*

/**
 * Интеграционные тесты для [SqliteDialogRepository].
 *
 * Проверяет:
 * - Создание и сохранение диалогов
 * - Загрузку диалогов по ID
 * - Список всех диалогов
 * - Удаление диалогов
 * - Сохранение и загрузку истории сообщений
 */
class SqliteDialogRepositoryTest {

    private lateinit var repository: SqliteDialogRepository
    private lateinit var tempDbPath: String

    @BeforeTest
    fun setUp() {
        val tempFile = Files.createTempFile("test-week1-", ".db")
        tempDbPath = tempFile.toString()
        repository = SqliteDialogRepository(tempDbPath)
    }

    @AfterTest
    fun tearDown() {
        repository.close()
        Files.deleteIfExists(java.nio.file.Paths.get(tempDbPath))
    }

    @Test
    fun `save creates new dialog`() = runTest {
        val dialogId = DialogId("test-dialog-1")
        val dialog = Dialog(
            id = dialogId,
            title = "Test Dialog",
            messages = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        repository.save(dialog)

        val loaded = repository.findById(dialogId)
        assertNotNull(loaded)
        assertEquals(dialogId, loaded.id)
        assertEquals("Test Dialog", loaded.title)
        assertEquals(0, loaded.messages.size)
    }

    @Test
    fun `save updates existing dialog`() = runTest {
        val dialogId = DialogId("test-dialog-2")
        val dialog1 = Dialog(
            id = dialogId,
            title = "Original Title",
            messages = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        repository.save(dialog1)

        val dialog2 = dialog1.copy(title = "Updated Title")
        repository.save(dialog2)

        val loaded = repository.findById(dialogId)
        assertNotNull(loaded)
        assertEquals("Updated Title", loaded.title)
    }

    @Test
    fun `findById returns null for non-existent dialog`() = runTest {
        val dialogId = DialogId("non-existent")
        val loaded = repository.findById(dialogId)
        assertNull(loaded)
    }

    @Test
    fun `findAll returns all dialogs`() = runTest {
        val dialog1 = Dialog(
            id = DialogId("dialog-1"),
            title = "Dialog 1",
            messages = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val dialog2 = Dialog(
            id = DialogId("dialog-2"),
            title = "Dialog 2",
            messages = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        repository.save(dialog1)
        repository.save(dialog2)

        val allDialogs = repository.findAll()
        assertEquals(2, allDialogs.size)
        assertTrue(allDialogs.any { it.id.value == "dialog-1" })
        assertTrue(allDialogs.any { it.id.value == "dialog-2" })
    }

    @Test
    fun `delete removes dialog`() = runTest {
        val dialogId = DialogId("dialog-to-delete")
        val dialog = Dialog(
            id = dialogId,
            title = "To Delete",
            messages = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        repository.save(dialog)
        assertNotNull(repository.findById(dialogId))

        repository.delete(dialogId)
        assertNull(repository.findById(dialogId))
    }

    @Test
    fun `save preserves message history`() = runTest {
        val dialogId = DialogId("dialog-with-messages")
        val messages = listOf(
            ChatMessage(ChatRole.USER, "Hello"),
            ChatMessage(ChatRole.ASSISTANT, "Hi there!"),
            ChatMessage(ChatRole.USER, "How are you?"),
            ChatMessage(ChatRole.ASSISTANT, "I'm doing well, thanks!")
        )
        val dialog = Dialog(
            id = dialogId,
            title = "Dialog with Messages",
            messages = messages,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        repository.save(dialog)

        val loaded = repository.findById(dialogId)
        assertNotNull(loaded)
        assertEquals(4, loaded.messages.size)
        assertEquals(ChatRole.USER, loaded.messages[0].role)
        assertEquals("Hello", loaded.messages[0].content)
        assertEquals(ChatRole.ASSISTANT, loaded.messages[1].role)
        assertEquals("Hi there!", loaded.messages[1].content)
        assertEquals(ChatRole.USER, loaded.messages[2].role)
        assertEquals("How are you?", loaded.messages[2].content)
        assertEquals(ChatRole.ASSISTANT, loaded.messages[3].role)
        assertEquals("I'm doing well, thanks!", loaded.messages[3].content)
    }

    @Test
    fun `findAll returns correct message counts`() = runTest {
        val dialog1 = Dialog(
            id = DialogId("dialog-count-1"),
            title = "Dialog 1",
            messages = listOf(
                ChatMessage(ChatRole.USER, "Message 1"),
                ChatMessage(ChatRole.ASSISTANT, "Response 1")
            ),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val dialog2 = Dialog(
            id = DialogId("dialog-count-2"),
            title = "Dialog 2",
            messages = listOf(
                ChatMessage(ChatRole.USER, "Message 1"),
                ChatMessage(ChatRole.ASSISTANT, "Response 1"),
                ChatMessage(ChatRole.USER, "Message 2"),
                ChatMessage(ChatRole.ASSISTANT, "Response 2"),
                ChatMessage(ChatRole.USER, "Message 3")
            ),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        repository.save(dialog1)
        repository.save(dialog2)

        val allDialogs = repository.findAll()
        val summary1 = allDialogs.find { it.id.value == "dialog-count-1" }
        val summary2 = allDialogs.find { it.id.value == "dialog-count-2" }

        assertNotNull(summary1)
        assertNotNull(summary2)
        assertEquals(2, summary1.messageCount)
        assertEquals(5, summary2.messageCount)
    }

    @Test
    fun `delete is idempotent`() = runTest {
        val dialogId = DialogId("non-existent-delete")

        // Should not throw exception
        repository.delete(dialogId)
        repository.delete(dialogId)
    }

    @Test
    fun `save handles system messages`() = runTest {
        val dialogId = DialogId("dialog-with-system")
        val messages = listOf(
            ChatMessage(ChatRole.SYSTEM, "You are a helpful assistant."),
            ChatMessage(ChatRole.USER, "Hello"),
            ChatMessage(ChatRole.ASSISTANT, "Hi!")
        )
        val dialog = Dialog(
            id = dialogId,
            title = "Dialog with System",
            messages = messages,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        repository.save(dialog)

        val loaded = repository.findById(dialogId)
        assertNotNull(loaded)
        assertEquals(3, loaded.messages.size)
        assertEquals(ChatRole.SYSTEM, loaded.messages[0].role)
        assertEquals("You are a helpful assistant.", loaded.messages[0].content)
    }
}
