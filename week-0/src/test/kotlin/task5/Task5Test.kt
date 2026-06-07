package io.averkhogliad.ai.challenge.week0.task5

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import io.averkhogliad.ai.challenge.utils.llm.ModelInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit-тесты для Task5.
 */
class Task5Test {

    /**
     * Простая реализация Config для тестирования.
     */
    private class TestConfig(private val properties: Map<String, String>) : Config {
        override fun get(key: String): String = properties[key] ?: throw NoSuchElementException("Key not found: $key")
        override fun getOrNull(key: String): String? = properties[key]
        override fun keys(): Set<String> = properties.keys
    }

    private fun createTask5WithModels(models: List<ModelInfo>): Task5 {
        val modelsString = models.joinToString(",") { model ->
            buildString {
                append(model.modelId)
                if (model.name != model.modelId) {
                    append(":${model.name}")
                }
                if (model.costPer1kInputTokens != null) {
                    append("(${model.costPer1kInputTokens}")
                    if (model.costPer1kOutputTokens != null && model.costPer1kOutputTokens != model.costPer1kInputTokens) {
                        append(",${model.costPer1kOutputTokens}")
                    }
                    append(")")
                }
            }
        }
        
        val config = TestConfig(mapOf(
            "models" to modelsString,
            "api.base-url" to "https://api.example.com",
            "api.key" to "test-key",
            "api.model" to "test-model",
            "api.connect-timeout" to "PT10S",
            "api.request-timeout" to "PT30S"
        ))
        val llmClient = LlmClient(config)
        return Task5(config, llmClient)
    }

    // ==================== Тесты formatTime ====================

    @Test
    fun `formatTime - миллисекунды меньше 1000`() {
        val task = createTask5WithModels(listOf(ModelInfo("test-model")))
        
        // Используем reflection для доступа к private методу
        val method = Task5::class.java.getDeclaredMethod("formatTime", Long::class.java)
        method.isAccessible = true
        
        assertEquals("500 мс", method.invoke(task, 500L) as String)
        assertEquals("999 мс", method.invoke(task, 999L) as String)
        assertEquals("0 мс", method.invoke(task, 0L) as String)
    }

    @Test
    fun `formatTime - секунды`() {
        val task = createTask5WithModels(listOf(ModelInfo("test-model")))
        
        val method = Task5::class.java.getDeclaredMethod("formatTime", Long::class.java)
        method.isAccessible = true
        
        assertEquals("1.0 сек", method.invoke(task, 1000L) as String)
        assertEquals("1.5 сек", method.invoke(task, 1500L) as String)
        assertEquals("2.3 сек", method.invoke(task, 2345L) as String)
        assertEquals("10.0 сек", method.invoke(task, 10000L) as String)
    }

    // ==================== Тесты handleCommand ====================

    @Test
    fun `handleCommand - распознает команду models`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1"), ModelInfo("model2")))
        
        assertTrue(task.handleCommand(":models"))
        assertTrue(task.handleCommand(":models 1,2"))
    }

    @Test
    fun `handleCommand - распознает команду maxTokens`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertTrue(task.handleCommand(":maxTokens"))
        assertTrue(task.handleCommand(":maxTokens 1000"))
    }

    @Test
    fun `handleCommand - распознает команду reset`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertTrue(task.handleCommand(":reset"))
    }

    @Test
    fun `handleCommand - распознает команду params`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertTrue(task.handleCommand(":params"))
    }

    @Test
    fun `handleCommand - не распознает неизвестную команду`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertFalse(task.handleCommand(":unknown"))
        assertFalse(task.handleCommand("models"))
        assertFalse(task.handleCommand(""))
    }

    // ==================== Тесты selectModels ====================

    @Test
    fun `selectModels - валидные индексы`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2"),
            ModelInfo("model3", "Model 3")
        )
        val task = createTask5WithModels(models)
        
        // Вызываем handleCommand с валидными индексами
        val result = task.handleCommand(":models 1,3")
        assertTrue(result)
        
        // Проверяем через getPromptHint, что выбрано 2 модели
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))
    }

    @Test
    fun `selectModels - дубликаты индексов удаляются`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2")
        )
        val task = createTask5WithModels(models)
        
        // Вызываем с дубликатами
        task.handleCommand(":models 1,1,2")
        
        // Проверяем, что выбрано 2 модели (дубликат удален)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))
    }

    @Test
    fun `selectModels - невалидный индекс игнорируется`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2")
        )
        val task = createTask5WithModels(models)
        
        // Вызываем с невалидным индексом
        task.handleCommand(":models 1,5")
        
        // Проверяем, что модели не изменились (остались все)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))
    }

    // ==================== Тесты handleMaxTokensCommand ====================

    @Test
    fun `handleMaxTokensCommand - валидное значение`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        // Устанавливаем maxTokens
        task.handleCommand(":maxTokens 1000")
        
        // Проверяем через getPromptHint
        val hint = task.getPromptHint()
        assertTrue(hint.contains("maxTokens=1000"))
    }

    @Test
    fun `handleMaxTokensCommand - значение вне диапазона`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        // Пытаемся установить значение вне диапазона
        task.handleCommand(":maxTokens 200000")
        
        // Проверяем, что значение осталось по умолчанию (500)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("maxTokens=500"))
    }

    @Test
    fun `handleMaxTokensCommand - невалидное значение`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        // Пытаемся установить невалидное значение
        task.handleCommand(":maxTokens abc")
        
        // Проверяем, что значение осталось по умолчанию (500)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("maxTokens=500"))
    }

    // ==================== Тесты handleResetCommand ====================

    @Test
    fun `handleResetCommand - сбрасывает параметры`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2")
        )
        val task = createTask5WithModels(models)
        
        // Изменяем параметры
        task.handleCommand(":models 1")
        task.handleCommand(":maxTokens 1000")
        
        // Сбрасываем
        task.handleCommand(":reset")
        
        // Проверяем, что параметры сброшены
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))  // Все модели
        assertTrue(hint.contains("maxTokens=500"))  // Значение по умолчанию
    }

    // ==================== Тесты getHelpText ====================

    @Test
    fun `getHelpText - содержит все команды`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        val helpText = task.getHelpText()
        
        assertTrue(helpText.contains(":models"))
        assertTrue(helpText.contains(":maxTokens"))
        assertTrue(helpText.contains(":reset"))
        assertTrue(helpText.contains(":params"))
    }

    // ==================== Тесты getPromptHint ====================

    @Test
    fun `getPromptHint - показывает текущие параметры`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2")
        )
        val task = createTask5WithModels(models)
        
        val hint = task.getPromptHint()
        
        assertTrue(hint.contains("models=2"))
        assertTrue(hint.contains("maxTokens=500"))
        assertTrue(hint.contains(":models"))
        assertTrue(hint.contains(":maxTokens"))
        assertTrue(hint.contains(":reset"))
        assertTrue(hint.contains(":params"))
    }

    // ==================== Тесты title ====================

    @Test
    fun `title - возвращает правильное название`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertEquals("Task 5: Сравнение производительности моделей", task.title)
    }
}
