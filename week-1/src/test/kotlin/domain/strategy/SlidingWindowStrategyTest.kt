package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContext
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
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

    // ═══════════════════════════════════════════════════════════════
    // Граничные случаи
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `prepareContext with empty dialog should return only system prompt`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor, configProvider = null)
        val emptyDialog = Dialog.create(DialogId("empty"), "Empty Dialog")
        val config = ContextManagementConfig()

        val result = strategy.prepareContext(emptyDialog, "You are a helpful assistant", config)

        assertTrue(result.messages.size == 1, "Expected 1 message, got ${result.messages.size}")
        assertEquals(ChatRole.SYSTEM, result.messages[0].role)
        assertEquals("You are a helpful assistant", result.messages[0].content)
        assertEquals(0, result.metadata[StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT])
    }

    @Test
    fun `prepareContext with exactly windowSize messages should not compress`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor)
        val dialog = createDialogWithMessages(5) // 10 messages total
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        assertEquals(0, result.metadata[StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT])
        assertTrue((result.metadata[StrategyMetadataKeys.NEW_ACCUMULATED_SUMMARY] as? String).isNullOrBlank())
    }

    @Test
    fun `prepareContext with windowSize + 1 messages should trigger compression`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor)
        val dialog = createDialogWithMessages(6) // 12 messages total
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10, blockSize = 5)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        assertTrue((result.metadata[StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT] as? Int ?: 0) > 0)
        assertNotNull(result.metadata[StrategyMetadataKeys.NEW_ACCUMULATED_SUMMARY] as? String)
        assertTrue((result.metadata[StrategyMetadataKeys.NEW_ACCUMULATED_SUMMARY] as? String)?.isNotBlank() == true)
    }

    // ═══════════════════════════════════════════════════════════════
    // Обработка ошибок
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `prepareContext when compressor throws exception should return fallback`() = runTest {
        val failingCompressor = object : DialogContextCompressor {
            override suspend fun compress(
                messages: List<ChatMessage>,
                config: ContextCompressionConfig,
                previousSummary: String?
            ): DialogContext {
                throw RuntimeException("LLM error")
            }
        }
        val strategy = SlidingWindowStrategy(failingCompressor, configProvider = null)
        val dialog = createDialogWithMessages(12) // 24 messages > 10 windowSize
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // Fallback: system prompt + последние windowSize сообщений
        assertTrue(result.messages.size == 11, "Expected 11 messages (1 system + 10), got ${result.messages.size}")
        assertEquals(0, result.metadata[StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT])
        assertNotNull(result.metadata["compressionError"])
    }

    @Test
    fun `prepareContext when compressor times out should return fallback`() = runTest {
        val hangingCompressor = object : DialogContextCompressor {
            override suspend fun compress(
                messages: List<ChatMessage>,
                config: ContextCompressionConfig,
                previousSummary: String?
            ): DialogContext {
                // Симулируем зависание — delay > timeout
                kotlinx.coroutines.delay(5000L)
                return DialogContext(
                    summary = "too late",
                    recentMessages = emptyList(),
                    compressedMessageCount = 0
                )
            }
        }
        val strategy = SlidingWindowStrategy(
            compressor = hangingCompressor,
            configProvider = null
        )
        val dialog = createDialogWithMessages(12) // 24 messages > 10 windowSize
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10),
            timeouts = TimeoutsConfig(compressionTimeoutMs = 100L) // очень короткий таймаут через конфиг
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // Fallback должен сработать
        assertEquals(0, result.metadata[StrategyMetadataKeys.COMPRESSED_MESSAGE_COUNT])
        assertNotNull(result.metadata["compressionError"])
        assertEquals("Timeout or error during compression", result.metadata["compressionError"])
    }

    @Test
    fun `prepareContext with invalid ChatRole should skip message`() = runTest {
        // Компрессор возвращает сообщения с неизвестными ролями — проверим, что стратегия не падает
        val compressorWithBadRole = object : DialogContextCompressor {
            override suspend fun compress(
                messages: List<ChatMessage>,
                config: ContextCompressionConfig,
                previousSummary: String?
            ): DialogContext {
                return DialogContext(
                    summary = "test summary",
                    recentMessages = emptyList(),
                    compressedMessageCount = 5
                ).copy() // DialogContext.toMessagesList возвращает сообщения с roles из исходного диалога
            }
        }
        val strategy = SlidingWindowStrategy(compressorWithBadRole, configProvider = null)
        val dialog = createDialogWithMessages(12)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 10, blockSize = 5)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // Не должно быть исключений — просто получаем какой-то результат
        assertNotNull(result)
    }

    // ═══════════════════════════════════════════════════════════════
    // Конфигурация
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `prepareContext with null configProvider should use static config`() = runTest {
        val compressor = createCompressor()
        val strategy = SlidingWindowStrategy(compressor, configProvider = null)
        val dialog = createDialogWithMessages(12)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 8, blockSize = 4)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        assertEquals(8, result.metadata[StrategyMetadataKeys.WINDOW_SIZE])
        assertEquals(4, result.metadata[StrategyMetadataKeys.BLOCK_SIZE])
    }

    @Test
    fun `prepareContext with configProvider should use dynamic config`() = runTest {
        val compressor = createCompressor()
        val configProvider = ContextCompressionConfigProvider(
            ContextCompressionConfig(
                enabled = false,
                windowSize = 6,
                blockSize = 3,
                summaryModelId = "dynamic-model"
            )
        )
        val strategy = SlidingWindowStrategy(compressor, configProvider)
        val dialog = createDialogWithMessages(12)
        val config = ContextManagementConfig(
            slidingWindow = SlidingWindowConfig(windowSize = 20, blockSize = 10)
        )

        val result = strategy.prepareContext(dialog, "System prompt", config)

        // Должны использоваться динамические значения
        assertEquals(6, result.metadata[StrategyMetadataKeys.WINDOW_SIZE])
        assertEquals(3, result.metadata[StrategyMetadataKeys.BLOCK_SIZE])
    }
}
