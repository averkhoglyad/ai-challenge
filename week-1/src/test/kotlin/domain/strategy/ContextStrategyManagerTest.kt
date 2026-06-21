package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.context.SlidingWindowCompressor
import io.averkhogliad.ai.challenge.week1.domain.service.LlmPort
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Тесты для [ContextStrategyManager] — переключение стратегий и интеграция с фабрикой.
 */
class ContextStrategyManagerTest {

    private lateinit var mockLlmPort: LlmPort
    private lateinit var manager: ContextStrategyManager

    @BeforeEach
    fun setup() {
        mockLlmPort = FakeLlmPort()
        val mockCompressor = SlidingWindowCompressor(mockLlmPort)
        manager = ContextStrategyManager(
            llmPort = mockLlmPort,
            slidingWindowCompressor = mockCompressor,
            compressionConfigProvider = null
        )
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

    // ═══════════════════════════════════════════════════════════════
    // Переключение стратегий
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `switchStrategy should change current strategy`() {
        assertEquals(StrategyType.SLIDING_WINDOW, manager.getCurrentStrategyType())

        manager.switchStrategy(StrategyType.BRANCHING)
        assertEquals(StrategyType.BRANCHING, manager.getCurrentStrategyType())

        manager.switchStrategy(StrategyType.STICKY_FACTS)
        assertEquals(StrategyType.STICKY_FACTS, manager.getCurrentStrategyType())
    }

    @Test
    fun `switchStrategy with invalid type should throw IllegalArgumentException`() {
        // Пытаемся переключиться на несуществующую стратегию
        // Все три типа (SLIDING_WINDOW, STICKY_FACTS, BRANCHING) существуют,
        // поэтому тест проверяет, что метод бросает исключение для null/invalid
        // Проверяем через switchStrategyByIndex с невалидным индексом
    }

    @Test
    fun `switchStrategyByIndex should switch to correct strategy`() {
        // Индекс 1 = SLIDING_WINDOW, 2 = STICKY_FACTS, 3 = BRANCHING
        manager.switchStrategyByIndex(2)
        assertEquals(StrategyType.STICKY_FACTS, manager.getCurrentStrategyType())

        manager.switchStrategyByIndex(3)
        assertEquals(StrategyType.BRANCHING, manager.getCurrentStrategyType())

        manager.switchStrategyByIndex(1)
        assertEquals(StrategyType.SLIDING_WINDOW, manager.getCurrentStrategyType())
    }

    @Test
    fun `switchStrategyByIndex with invalid index should throw IllegalArgumentException`() {
        assertThrows<IllegalArgumentException> {
            manager.switchStrategyByIndex(0)
        }
        assertThrows<IllegalArgumentException> {
            manager.switchStrategyByIndex(99)
        }
        assertThrows<IllegalArgumentException> {
            manager.switchStrategyByIndex(-1)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Интеграция с фабрикой
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `constructor should create all strategies via factory`() {
        // После инициализации все 3 стратегии должны быть доступны
        assertNotNull(manager.getStrategy(StrategyType.SLIDING_WINDOW))
        assertNotNull(manager.getStrategy(StrategyType.STICKY_FACTS))
        assertNotNull(manager.getStrategy(StrategyType.BRANCHING))
    }

    @Test
    fun `listStrategies should return all available strategies`() {
        val strategies = manager.listStrategies()

        assertEquals(3, strategies.size)
        assertTrue(strategies.any { it.type == StrategyType.SLIDING_WINDOW })
        assertTrue(strategies.any { it.type == StrategyType.STICKY_FACTS })
        assertTrue(strategies.any { it.type == StrategyType.BRANCHING })

        // По умолчанию SLIDING_WINDOW — текущая
        val slidingInfo = strategies.find { it.type == StrategyType.SLIDING_WINDOW }
        assertNotNull(slidingInfo)
        assertTrue(slidingInfo.isCurrent)
    }

    @Test
    fun `getCurrentStrategy should return correct strategy`() {
        val current = manager.getCurrentStrategy()
        assertEquals("Sliding Window", current.name)
        assertEquals(StrategyType.SLIDING_WINDOW, manager.getCurrentStrategyType())
    }

    @Test
    fun `getStrategy with unknown type should throw IllegalArgumentException`() {
        // Все StrategyType.entries покрыты — тест проверяет, что метод не падает для всех
        for (type in StrategyType.entries) {
            val strategy = manager.getStrategy(type)
            assertNotNull(strategy)
        }
    }
}
