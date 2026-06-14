package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.context.SlidingWindowCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.service.MockLlmPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SlidingWindowStrategyTest {

    private val mockLlm = MockLlmPort()
    private val compressor = SlidingWindowCompressor(mockLlm)
    private val strategy = SlidingWindowStrategy(compressor)

    private fun createDialogWithMessages(count: Int): Dialog {
        var dialog = Dialog.create(DialogId("test-dialog"), "Test Dialog")
        repeat(count) { i ->
            dialog = dialog.addUserMessage("Message ${i + 1}")
        }
        return dialog
    }

    @Test
    fun `should have correct name and description`() {
        assertEquals("Sliding Window", strategy.name)
        assertTrue(strategy.description.contains("последние N сообщений"))
    }

    @Test
    fun `processUserMessage should return empty action result`() = runTest {
        val dialog = createDialogWithMessages(3)
        val config = ContextManagementConfig()

        val result = strategy.processUserMessage(dialog, "Test message", config)

        assertTrue(result.actionsPerformed.isEmpty())
    }

    @Test
    fun `prepareContext should return all messages when count less than windowSize`() = runTest {
        val dialog = createDialogWithMessages(5)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10)
        )

        val context = strategy.prepareContext(dialog, "System prompt", config)

        // System prompt + 5 messages
        assertEquals(6, context.messages.size)
        assertEquals("System prompt", context.messages[0].content)
    }

    @Test
    fun `prepareContext should limit messages to windowSize`() = runTest {
        val dialog = createDialogWithMessages(15)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 5)
        )

        val context = strategy.prepareContext(dialog, "System prompt", config)

        // System prompt + 5 messages (last 5)
        assertEquals(6, context.messages.size)
        // Verify last messages are included
        assertTrue(context.messages.any { it.content.contains("Message 15") })
        assertTrue(context.messages.any { it.content.contains("Message 11") })
    }

    @Test
    fun `prepareContext metadata should contain strategy info`() = runTest {
        val dialog = createDialogWithMessages(10)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 5)
        )

        val context = strategy.prepareContext(dialog, "System prompt", config)

        assertEquals("sliding-window", context.metadata["strategy"])
        assertEquals(5, context.metadata["windowSize"])
    }
}
