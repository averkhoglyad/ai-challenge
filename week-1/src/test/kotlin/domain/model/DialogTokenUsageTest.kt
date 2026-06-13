package io.averkhogliad.ai.challenge.week1.domain.model

import io.averkhogliad.ai.challenge.week1.domain.telemetry.TokenUsage
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DialogTokenUsageTest {

    private fun newDialogId() = DialogId(UUID.randomUUID().toString())

    @Test
    fun `should create Dialog with empty tokenUsageHistory`() {
        val dialogId = newDialogId()
        val dialog = Dialog.create(dialogId, "Test Dialog")
        assertTrue(dialog.tokenUsageHistory.isEmpty())
        assertNull(dialog.totalTokenUsage)
    }

    @Test
    fun `should add token usage to history`() {
        val dialogId = newDialogId()
        var dialog = Dialog.create(dialogId, "Test Dialog")
        val usage = TokenUsage(100, 50, 150)

        dialog = dialog.addTokenUsage(usage)

        assertEquals(1, dialog.tokenUsageHistory.size)
        assertEquals(usage, dialog.tokenUsageHistory[0])
    }

    @Test
    fun `should accumulate multiple usages`() {
        val dialogId = newDialogId()
        var dialog = Dialog.create(dialogId, "Test Dialog")
        dialog = dialog.addTokenUsage(TokenUsage(100, 50, 150))
        dialog = dialog.addTokenUsage(TokenUsage(200, 100, 300))

        assertEquals(2, dialog.tokenUsageHistory.size)
        assertNotNull(dialog.totalTokenUsage)
        assertEquals(300, dialog.totalTokenUsage?.promptTokens)
        assertEquals(150, dialog.totalTokenUsage?.completionTokens)
        assertEquals(450, dialog.totalTokenUsage?.totalTokens)
    }

    @Test
    fun `addTokenUsage should return new instance`() {
        val dialogId = newDialogId()
        val dialog = Dialog.create(dialogId, "Test Dialog")
        val updated = dialog.addTokenUsage(TokenUsage(10, 5, 15))

        assertTrue(dialog.tokenUsageHistory.isEmpty())
        assertEquals(1, updated.tokenUsageHistory.size)
    }

    @Test
    fun `totalTokenUsage should be null when history is empty`() {
        val dialogId = newDialogId()
        val dialog = Dialog.create(dialogId, "Test Dialog")
        assertNull(dialog.totalTokenUsage)
    }

    @Test
    fun `totalTokenUsage should sum correctly with single entry`() {
        val dialogId = newDialogId()
        var dialog = Dialog.create(dialogId, "Test Dialog")
        dialog = dialog.addTokenUsage(TokenUsage(100, 50, 150))

        assertNotNull(dialog.totalTokenUsage)
        assertEquals(100, dialog.totalTokenUsage?.promptTokens)
        assertEquals(50, dialog.totalTokenUsage?.completionTokens)
        assertEquals(150, dialog.totalTokenUsage?.totalTokens)
    }

    @Test
    fun `immutability should be preserved across all operations`() {
        val dialogId = newDialogId()
        val original = Dialog.create(dialogId, "Original")
        val withMessage = original.addUserMessage("Hello")
        val withUsage = withMessage.addTokenUsage(TokenUsage(10, 5, 15))

        // Original unchanged
        assertTrue(original.messages.isEmpty())
        assertTrue(original.tokenUsageHistory.isEmpty())

        // With message has message but no usage
        assertEquals(1, withMessage.messages.size)
        assertTrue(withMessage.tokenUsageHistory.isEmpty())

        // With usage has both
        assertEquals(1, withUsage.messages.size)
        assertEquals(1, withUsage.tokenUsageHistory.size)
    }
}
