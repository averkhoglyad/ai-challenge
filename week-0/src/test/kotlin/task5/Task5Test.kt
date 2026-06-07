package io.averkhogliad.ai.challenge.week0.task5

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.LlmClient
import io.averkhogliad.ai.challenge.utils.llm.ModelInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for Task5.
 */
class Task5Test {

    /**
     * Simple Config implementation for testing.
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
                if (model.costPerMillionInputTokens != null) {
                    append("(${model.costPerMillionInputTokens}")
                    if (model.costPerMillionOutputTokens != null && model.costPerMillionOutputTokens != model.costPerMillionInputTokens) {
                        append(",${model.costPerMillionOutputTokens}")
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

    // ==================== Tests formatTime ====================

    @Test
    fun `formatTime - milliseconds less than 1000`() {
        val task = createTask5WithModels(listOf(ModelInfo("test-model")))

        // Use reflection to access private method
        val method = Task5::class.java.getDeclaredMethod("formatTime", Long::class.java)
        method.isAccessible = true
        
        assertEquals("500 мс", method.invoke(task, 500L) as String)
        assertEquals("999 мс", method.invoke(task, 999L) as String)
        assertEquals("0 мс", method.invoke(task, 0L) as String)
    }

    @Test
    fun `formatTime - seconds`() {
        val task = createTask5WithModels(listOf(ModelInfo("test-model")))
        
        val method = Task5::class.java.getDeclaredMethod("formatTime", Long::class.java)
        method.isAccessible = true
        
        assertEquals("1.0 сек", method.invoke(task, 1000L) as String)
        assertEquals("1.5 сек", method.invoke(task, 1500L) as String)
        assertEquals("2.3 сек", method.invoke(task, 2345L) as String)
        assertEquals("10.0 сек", method.invoke(task, 10000L) as String)
    }

    // ==================== Tests handleCommand ====================

    @Test
    fun `handleCommand - recognizes models command`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1"), ModelInfo("model2")))
        
        assertTrue(task.handleCommand(":models"))
        assertTrue(task.handleCommand(":models 1,2"))
    }

    @Test
    fun `handleCommand - recognizes maxTokens command`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertTrue(task.handleCommand(":maxTokens"))
        assertTrue(task.handleCommand(":maxTokens 1000"))
    }

    @Test
    fun `handleCommand - recognizes reset command`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertTrue(task.handleCommand(":reset"))
    }

    @Test
    fun `handleCommand - recognizes params command`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertTrue(task.handleCommand(":params"))
    }

    @Test
    fun `handleCommand - does not recognize unknown command`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertFalse(task.handleCommand(":unknown"))
        assertFalse(task.handleCommand("models"))
        assertFalse(task.handleCommand(""))
    }

    // ==================== Tests selectModels ====================

    @Test
    fun `selectModels - valid indices`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2"),
            ModelInfo("model3", "Model 3")
        )
        val task = createTask5WithModels(models)

        // Call handleCommand with valid indices
        val result = task.handleCommand(":models 1,3")
        assertTrue(result)

        // Check via getPromptHint that 2 models are selected
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))
    }

    @Test
    fun `selectModels - duplicate indices are removed`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2")
        )
        val task = createTask5WithModels(models)

        // Call with duplicates
        task.handleCommand(":models 1,1,2")

        // Check that 2 models are selected (duplicate removed)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))
    }

    @Test
    fun `selectModels - invalid index is ignored`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2")
        )
        val task = createTask5WithModels(models)

        // Call with invalid index
        task.handleCommand(":models 1,5")

        // Check that models haven't changed (all remain)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))
    }

    // ==================== Tests handleMaxTokensCommand ====================

    @Test
    fun `handleMaxTokensCommand - valid value`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))

        // Set maxTokens
        task.handleCommand(":maxTokens 1000")

        // Check via getPromptHint
        val hint = task.getPromptHint()
        assertTrue(hint.contains("maxTokens=1000"))
    }

    @Test
    fun `handleMaxTokensCommand - value out of range`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))

        // Try to set value out of range
        task.handleCommand(":maxTokens 200000")

        // Check that value remains default (500)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("maxTokens=500"))
    }

    @Test
    fun `handleMaxTokensCommand - invalid value`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))

        // Try to set invalid value
        task.handleCommand(":maxTokens abc")

        // Check that value remains default (500)
        val hint = task.getPromptHint()
        assertTrue(hint.contains("maxTokens=500"))
    }

    // ==================== Tests handleResetCommand ====================

    @Test
    fun `handleResetCommand - resets parameters`() {
        val models = listOf(
            ModelInfo("model1", "Model 1"),
            ModelInfo("model2", "Model 2")
        )
        val task = createTask5WithModels(models)

        // Change parameters
        task.handleCommand(":models 1")
        task.handleCommand(":maxTokens 1000")

        // Reset
        task.handleCommand(":reset")

        // Check that parameters are reset
        val hint = task.getPromptHint()
        assertTrue(hint.contains("models=2"))  // All models
        assertTrue(hint.contains("maxTokens=500"))  // Default value
    }

    // ==================== Tests getHelpText ====================

    @Test
    fun `getHelpText - contains all commands`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        val helpText = task.getHelpText()
        
        assertTrue(helpText.contains(":models"))
        assertTrue(helpText.contains(":maxTokens"))
        assertTrue(helpText.contains(":reset"))
        assertTrue(helpText.contains(":params"))
    }

    // ==================== Tests getPromptHint ====================

    @Test
    fun `getPromptHint - shows current parameters`() {
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

    // ==================== Tests title ====================

    @Test
    fun `title - returns the correct name`() {
        val task = createTask5WithModels(listOf(ModelInfo("model1")))
        
        assertEquals("Task 5: Сравнение производительности моделей", task.title)
    }
}
