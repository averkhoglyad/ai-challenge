package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContext
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SlidingWindowStrategyTest {

    // --- Fake compressor that returns predictable DialogContext without LLM ---

    /**
     * Mock LlmPort, который всегда возвращает ошибку (не должен вызываться в тестах).
     */

    /**
     * Тестовый компрессор: не вызывает LLM, а возвращает [DialogContext] с фиксированным summary
     * и последними windowSize сообщениями.
     */
    private class FakeCompressor : DialogContextCompressor {
        var lastPreviousSummary: String? = null
            private set

        override suspend fun compress(
            messages: List<ChatMessage>,
            config: ContextCompressionConfig,
            previousSummary: String?
        ): DialogContext {
            lastPreviousSummary = previousSummary

            if (messages.size <= config.windowSize) {
                return DialogContext(
                    summary = previousSummary,
                    recentMessages = messages,
                    compressedMessageCount = 0
                )
            }

            val recentMessages = messages.takeLast(config.windowSize)
            val compressedCount = messages.size - config.windowSize

            return DialogContext(
                summary = "[Compressed $compressedCount messages: ...]",
                recentMessages = recentMessages,
                compressedMessageCount = compressedCount
            )
        }
    }

    // --- Helper ---

    private fun createDialogWithMessages(count: Int, title: String = "Test Dialog"): Dialog {
        var dialog = Dialog.create(DialogId("test-dialog"), title)
        repeat(count) { i ->
            dialog = dialog.addUserMessage("Message ${i + 1}")
            dialog = dialog.addAssistantMessage("Response ${i + 1}")
        }
        return dialog
    }

    private fun createCompressor() = FakeCompressor()

    // --- Tests ---

    @Test
    fun `name and description should be correct`() {
        val strategy = SlidingWindowStrategy(createCompressor())
        assertEquals("Sliding Window", strategy.name)
        assertTrue(strategy.description.contains("Sliding Window") || strategy.description.contains("скользящего окна"))
    }

    @Test
    fun `processUserMessage should return empty result`() = runTest {
        val strategy = SlidingWindowStrategy(createCompressor())
        val dialog = createDialogWithMessages(3)
        val config = ContextManagementConfig()

        val result = strategy.processUserMessage(dialog, "Hello", config)

        assertEquals(StrategyActionResult.empty(), result)
    }

    // --- prepareContext tests ---

    @Test
    fun `prepareContext with messages less than windowSize should return full context without compression`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor)
        val dialog = createDialogWithMessages(4) // 8 messages total (4 pairs)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // System message + all 8 messages
        assertEquals(9, result.messages.size)
        assertEquals("sliding-window", result.metadata["strategy"])
        assertEquals(10, result.metadata["windowSize"])
        assertEquals(0, result.metadata["compressedMessageCount"])
        assertTrue((result.metadata["newAccumulatedSummary"] as? String).isNullOrBlank())
    }

    @Test
    fun `prepareContext with messages exceeding windowSize should compress`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor)
        val dialog = createDialogWithMessages(12) // 24 messages total (12 pairs)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10, blockSize = 5)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // Should have compressed (24 - 10 = 14 messages)
        assertTrue((result.metadata["compressedMessageCount"] as? Int ?: 0) > 0)
        assertEquals("sliding-window", result.metadata["strategy"])
        assertEquals(10, result.metadata["windowSize"])
        // newAccumulatedSummary should be non-null and non-blank
        val summary = result.metadata["newAccumulatedSummary"] as? String
        assertNotNull(summary)
        assertTrue(summary.isNotBlank())
    }

    @Test
    fun `prepareContext should pass accumulatedSummary to compressor as previousSummary`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor)
        val dialog = createDialogWithMessages(12)
            .updateAccumulatedSummary("Previous summary text")
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10, blockSize = 5)
        )

        strategy.prepareContext(dialog, "System prompt", config)

        assertEquals("Previous summary text", compressor.lastPreviousSummary)
    }

    @Test
    fun `prepareContext should fall back to static config when configProvider is null`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor, configProvider = null)
        val dialog = createDialogWithMessages(12)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 8, blockSize = 4)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        assertEquals(8, result.metadata["windowSize"])
        assertEquals(4, result.metadata["blockSize"])
    }

    @Test
    fun `prepareContext should prefer dynamic configProvider over static config`() = runTest {
        val compressor = createCompressor()
        val configProvider = ContextCompressionConfigProvider(
            ContextCompressionConfig(
                enabled = false, // enabled is ignored by strategy
                windowSize = 6,
                blockSize = 3,
                summaryModelId = "test-model"
            )
        )
        val strategy = SlidingWindowStrategy(compressor, configProvider)
        val dialog = createDialogWithMessages(12)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 20, blockSize = 10)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // Should use dynamic values (6, 3), not static (20, 10)
        assertEquals(6, result.metadata["windowSize"])
        assertEquals(3, result.metadata["blockSize"])
    }

    @Test
    fun `prepareContext metadata should contain blockSize alongside windowSize`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor)
        val dialog = createDialogWithMessages(12)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10, blockSize = 5)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        assertNotNull(result.metadata["blockSize"])
        assertTrue((result.metadata["blockSize"] as? Int ?: 0) > 0)
    }

    @Test
    fun `prepareContext with exactly windowSize messages should return full context`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor)
        val dialog = createDialogWithMessages(5) // 10 messages total (5 pairs)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // messages.size == windowSize → no compression
        assertEquals(0, result.metadata["compressedMessageCount"])
        assertTrue((result.metadata["newAccumulatedSummary"] as? String).isNullOrBlank())
    }

    @Test
    fun `applyAccumulatedSummary should update dialog when summary is present`() {
        runTest {
            val compressor = createCompressor()
            val strategy = SlidingWindowStrategy(compressor)
            val dialog = createDialogWithMessages(12)
            val config = ContextManagementConfig(
                slidingWindow = SlidingWindowConfig(windowSize = 5, blockSize = 2)
            )

            val result = strategy.prepareContext(dialog, "System prompt", config)

            val summary = result.metadata["newAccumulatedSummary"] as? String
            assertNotNull(summary, "newAccumulatedSummary should be present when compression occurs")
            assertTrue(summary.isNotBlank(), "newAccumulatedSummary should be non-blank")
        }
    }
}
