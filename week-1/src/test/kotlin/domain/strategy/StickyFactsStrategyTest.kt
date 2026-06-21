package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.*
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StickyFactsStrategyTest {

    private lateinit var strategy: StickyFactsStrategy

    @BeforeEach
    fun setup() {
        val fakeExtractor = FakeFactsExtractor()
        strategy = StickyFactsStrategy(fakeExtractor)
    }

    // --- Fake Extractor ---

    /**
     * Тестовый экстрактор фактов: не вызывает LLM, возвращает заданные факты
     * или выбрасывает исключение / зависает по флагу.
     */
    private class FakeFactsExtractor : FactsExtractor(FakeLlmPort()) {
        var resultFacts: List<StickyFact> = emptyList()
        var shouldThrow: Boolean = false
        var shouldHangMs: Long = 0L

        override suspend fun extractFacts(
            userMessage: String,
            messageIndex: Int,
            extractionModelId: String?
        ): List<StickyFact> {
            if (shouldThrow) throw RuntimeException("LLM error")
            if (shouldHangMs > 0) {
                delay(shouldHangMs)
                return emptyList()
            }
            return resultFacts
        }
    }

    /**
     * Тестовый LlmPort — никогда не вызывается в этих тестах.
     */
    private class FakeLlmPort : LlmPort {
        override suspend fun chat(
            prompt: io.averkhogliad.ai.challenge.week1.domain.Prompt,
            config: io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
        ): io.averkhogliad.ai.challenge.week1.domain.TaskResult {
            throw UnsupportedOperationException("LLM should not be called in tests")
        }

        override suspend fun chatWithMessages(
            messages: List<io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage>,
            config: io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
        ): io.averkhogliad.ai.challenge.week1.domain.TaskResult {
            throw UnsupportedOperationException("LLM should not be called in tests")
        }

        override suspend fun listModels(): List<io.averkhogliad.ai.challenge.week1.domain.ModelId> {
            throw UnsupportedOperationException("LLM should not be called in tests")
        }
    }

    // --- Helper ---

    private fun createDialogWithMessages(count: Int): Dialog {
        var dialog = Dialog.create(DialogId("test-dialog"), "Test Dialog")
        repeat(count) { i ->
            dialog = dialog.addUserMessage("Message ${i + 1}")
            dialog = dialog.addAssistantMessage("Response ${i + 1}")
        }
        return dialog
    }

    private fun configureExtractor(
        facts: List<StickyFact> = emptyList(),
        shouldThrow: Boolean = false,
        shouldHangMs: Long = 0L
    ) {
        val extractor = getFakeExtractor()
        extractor.resultFacts = facts
        extractor.shouldThrow = shouldThrow
        extractor.shouldHangMs = shouldHangMs
    }

    private fun getFakeExtractor(): FakeFactsExtractor {
        val field = StickyFactsStrategy::class.java.getDeclaredField("factsExtractor")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(strategy) as FakeFactsExtractor
    }

    // ═══════════════════════════════════════════════════════════════
    // Извлечение фактов
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `processUserMessage should extract facts and update state`() = runTest {
        val facts = listOf(
            StickyFact(
                key = "preference:name",
                value = "Alice",
                category = FactCategory.PREFERENCE,
                sourceMessageIndex = 0
            )
        )
        configureExtractor(facts = facts)

        val dialog = createDialogWithMessages(1)
        val initialState = StrategyState.StickyFactsState.createInitial()

        val result = strategy.processUserMessage(
            dialog = dialog,
            userMessage = "Меня зовут Alice",
            config = ContextManagementConfig(),
            state = initialState
        )

        assertTrue(result.actionsPerformed.any { it is StrategyAction.FactsExtracted })
        val factsExtracted = result.actionsPerformed.filterIsInstance<StrategyAction.FactsExtracted>().first()
        assertEquals(1, factsExtracted.factsCount)

        val newState = result.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState.StickyFactsState
        assertNotNull(newState)
        assertEquals(1, newState.factsStore.facts.size)
    }

    @Test
    fun `processUserMessage when factsExtractor returns empty list should not modify state`() = runTest {
        configureExtractor(facts = emptyList())

        val dialog = createDialogWithMessages(1)
        val initialState = StrategyState.StickyFactsState.createInitial()

        val result = strategy.processUserMessage(
            dialog = dialog,
            userMessage = "Сообщение без фактов",
            config = ContextManagementConfig(),
            state = initialState
        )

        val newState = result.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState.StickyFactsState
        assertNotNull(newState)
        assertEquals(0, newState.factsStore.facts.size)
    }

    @Test
    fun `processUserMessage when factsExtractor throws exception should return empty facts`() = runTest {
        configureExtractor(shouldThrow = true)

        val dialog = createDialogWithMessages(1)
        val initialState = StrategyState.StickyFactsState.createInitial()

        val result = strategy.processUserMessage(
            dialog = dialog,
            userMessage = "Сообщение",
            config = ContextManagementConfig(),
            state = initialState
        )

        assertTrue(result.actionsPerformed.any { it is StrategyAction.FactsExtracted })
        val factsExtracted = result.actionsPerformed.filterIsInstance<StrategyAction.FactsExtracted>().first()
        assertEquals(0, factsExtracted.factsCount)

        val newState = result.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState.StickyFactsState
        assertNotNull(newState)
        assertEquals(0, newState.factsStore.facts.size)
    }

    // ═══════════════════════════════════════════════════════════════
    // Подготовка контекста
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `prepareContext should include facts in system prompt`() = runTest {
        val factsStore = FactsStore().addOrUpdate(
            StickyFact(
                key = "preference:name",
                value = "Alice",
                category = FactCategory.PREFERENCE,
                sourceMessageIndex = 0
            )
        )
        val state = StrategyState.StickyFactsState(factsStore = factsStore)

        val dialog = createDialogWithMessages(3)

        val result = strategy.prepareContext(
            dialog = dialog,
            systemPrompt = "You are a helpful assistant",
            config = ContextManagementConfig(),
            state = state
        )

        assertTrue(
            result.messages[0].content.contains("Alice"),
            "System prompt should contain facts"
        )
        assertEquals(ChatRole.SYSTEM, result.messages[0].role)
        assertEquals(1, result.metadata[StrategyMetadataKeys.FACTS_COUNT])
    }

    @Test
    fun `prepareContext with empty facts should use original system prompt`() = runTest {
        val state = StrategyState.StickyFactsState.createInitial()

        val dialog = createDialogWithMessages(3)

        val result = strategy.prepareContext(
            dialog = dialog,
            systemPrompt = "You are a helpful assistant",
            config = ContextManagementConfig(),
            state = state
        )

        assertEquals("You are a helpful assistant", result.messages[0].content)
        assertEquals(ChatRole.SYSTEM, result.messages[0].role)
        assertEquals(0, result.metadata[StrategyMetadataKeys.FACTS_COUNT])
    }

    @Test
    fun `prepareContext should return only last windowSize messages`() = runTest {
        val state = StrategyState.StickyFactsState.createInitial()

        // Создаём 7 пар user+assistant = 14 сообщений
        val dialog = createDialogWithMessages(7)
        val config = ContextManagementConfig(
            stickyFacts = StickyFactsConfig(windowSize = 5)
        )

        val result = strategy.prepareContext(
            dialog = dialog,
            systemPrompt = "System prompt",
            config = config,
            state = state
        )

        // 1 system + 5 сообщений = 6
        assertEquals(6, result.messages.size)
        assertEquals(5, result.metadata[StrategyMetadataKeys.WINDOW_SIZE])
    }

    // ═══════════════════════════════════════════════════════════════
    // Обработка ошибок
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `processUserMessage with blank userMessage should throw IllegalArgumentException`() = runTest {
        val dialog = createDialogWithMessages(1)

        assertThrows<IllegalArgumentException> {
            strategy.processUserMessage(
                dialog = dialog,
                userMessage = "   ",
                config = ContextManagementConfig()
            )
        }
    }

    @Test
    fun `processUserMessage when factsExtractor times out should return empty facts`() = runTest {
        // Создаём стратегию с зависающим экстрактором
        val hangingExtractor = object : FactsExtractor(FakeLlmPort()) {
            override suspend fun extractFacts(
                userMessage: String,
                messageIndex: Int,
                extractionModelId: String?
            ): List<StickyFact> {
                delay(5000L)
                return emptyList()
            }
        }
        val timeoutStrategy = StickyFactsStrategy(
            factsExtractor = hangingExtractor
        )

        val dialog = createDialogWithMessages(1)
        val initialState = StrategyState.StickyFactsState.createInitial()

        val result = timeoutStrategy.processUserMessage(
            dialog = dialog,
            userMessage = "Сообщение",
            config = ContextManagementConfig(
                timeouts = TimeoutsConfig(factExtractionTimeoutMs = 100L) // короткий таймаут через конфиг
            ),
            state = initialState
        )

        // Должен вернуть 0 фактов (таймаут)
        assertTrue(result.actionsPerformed.any { it is StrategyAction.FactsExtracted })
        val factsExtracted = result.actionsPerformed.filterIsInstance<StrategyAction.FactsExtracted>().first()
        assertEquals(0, factsExtracted.factsCount)
    }
}
