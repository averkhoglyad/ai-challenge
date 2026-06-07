package io.averkhogliad.ai.challenge.utils.llm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ModelInfoTest {

    // === Парсинг модели ===

    @Test
    fun `parse - only id`() {
        val model = ModelInfo.parse("minimax/minimax-m3")
        assertEquals("minimax/minimax-m3", model.modelId)
        assertEquals("minimax/minimax-m3", model.name)
        assertNull(model.costPer1kInputTokens)
        assertNull(model.costPer1kOutputTokens)
    }

    @Test
    fun `parse - id with name`() {
        val model = ModelInfo.parse("minimax/minimax-m3:Minimax M3")
        assertEquals("minimax/minimax-m3", model.modelId)
        assertEquals("Minimax M3", model.name)
        assertNull(model.costPer1kInputTokens)
        assertNull(model.costPer1kOutputTokens)
    }

    @Test
    fun `parse - id with single cost`() {
        val model = ModelInfo.parse("minimax/minimax-m3(0.0001)")
        assertEquals("minimax/minimax-m3", model.modelId)
        assertEquals("minimax/minimax-m3", model.name)
        assertEquals(0.0001, model.costPer1kInputTokens)
        assertNull(model.costPer1kOutputTokens)
    }

    @Test
    fun `parse - id with input and output cost`() {
        val model = ModelInfo.parse("openai/gpt-4o(0.0025,0.01)")
        assertEquals("openai/gpt-4o", model.modelId)
        assertEquals("openai/gpt-4o", model.name)
        assertEquals(0.0025, model.costPer1kInputTokens)
        assertEquals(0.01, model.costPer1kOutputTokens)
    }

    @Test
    fun `parse - full format id name cost`() {
        val model = ModelInfo.parse("minimax/minimax-m3:Minimax M3(0.0001)")
        assertEquals("minimax/minimax-m3", model.modelId)
        assertEquals("Minimax M3", model.name)
        assertEquals(0.0001, model.costPer1kInputTokens)
        assertNull(model.costPer1kOutputTokens)
    }

    @Test
    fun `parse - full format with different costs`() {
        val model = ModelInfo.parse("openai/gpt-4o-mini:GPT-4o Mini(0.00015,0.0006)")
        assertEquals("openai/gpt-4o-mini", model.modelId)
        assertEquals("GPT-4o Mini", model.name)
        assertEquals(0.00015, model.costPer1kInputTokens)
        assertEquals(0.0006, model.costPer1kOutputTokens)
    }

    // === Валидация ===

    @Test
    fun `parse - blank entry throws`() {
        assertFailsWith<IllegalArgumentException> {
            ModelInfo.parse("")
        }
    }

    @Test
    fun `parse - whitespace only entry throws`() {
        assertFailsWith<IllegalArgumentException> {
            ModelInfo.parse("   ")
        }
    }

    @Test
    fun `parse - empty model id throws`() {
        assertFailsWith<IllegalArgumentException> {
            ModelInfo.parse(":SomeName")
        }
    }

    @Test
    fun `parse - invalid cost format throws`() {
        assertFailsWith<IllegalArgumentException> {
            ModelInfo.parse("model(abc)")
        }
    }

    @Test
    fun `parse - negative cost throws`() {
        assertFailsWith<IllegalArgumentException> {
            ModelInfo.parse("model(-0.001)")
        }
    }

    @Test
    fun `parse - too many cost values throws`() {
        assertFailsWith<IllegalArgumentException> {
            ModelInfo.parse("model(0.001,0.002,0.003)")
        }
    }

    // === Расчёт стоимости ===

    @Test
    fun `calculateCost - no cost returns null`() {
        val model = ModelInfo("model-id")
        assertNull(model.calculateCost(100, 50))
    }

    @Test
    fun `calculateCost - single cost applied to both`() {
        val model = ModelInfo("model-id", costPer1kInputTokens = 0.001)
        // 1000 input tokens * 0.001/1000 + 500 output tokens * 0.001/1000 = 0.001 + 0.0005 = 0.0015
        assertEquals(0.0015, model.calculateCost(1000, 500))
    }

    @Test
    fun `calculateCost - different costs`() {
        val model = ModelInfo(
            "model-id",
            costPer1kInputTokens = 0.002,
            costPer1kOutputTokens = 0.01
        )
        // 1000 input * 0.002/1000 + 500 output * 0.01/1000 = 0.002 + 0.005 = 0.007
        assertEquals(0.007, model.calculateCost(1000, 500))
    }

    @Test
    fun `calculateCost - zero tokens`() {
        val model = ModelInfo("model-id", costPer1kInputTokens = 0.001)
        assertEquals(0.0, model.calculateCost(0, 0))
    }

    // === Форматирование тарифа ===

    @Test
    fun `formatTariff - no cost returns free`() {
        val model = ModelInfo("model-id")
        assertEquals("бесплатно", model.formatTariff())
    }

    @Test
    fun `formatTariff - single cost`() {
        val model = ModelInfo("model-id", costPer1kInputTokens = 0.0001)
        val result = model.formatTariff()
        assertEquals("$0.0001/1K токенов", result)
    }

    @Test
    fun `formatTariff - different costs`() {
        val model = ModelInfo(
            "model-id",
            costPer1kInputTokens = 0.0025,
            costPer1kOutputTokens = 0.01
        )
        val result = model.formatTariff()
        assertEquals("$0.0025/1K input, $0.0100/1K output", result)
    }

    // === Парсинг списка ===

    @Test
    fun `parseList - empty string returns empty list`() {
        assertEquals(emptyList(), ModelInfo.parseList(""))
    }

    @Test
    fun `parseList - blank string returns empty list`() {
        assertEquals(emptyList(), ModelInfo.parseList("   "))
    }

    @Test
    fun `parseList - single model`() {
        val models = ModelInfo.parseList("minimax/minimax-m3")
        assertEquals(1, models.size)
        assertEquals("minimax/minimax-m3", models[0].modelId)
    }

    @Test
    fun `parseList - multiple models`() {
        val models = ModelInfo.parseList(
            "minimax/minimax-m3:Minimax M3(0.0001),openai/gpt-4o-mini:GPT-4o Mini(0.00015,0.0006),openai/gpt-4o:GPT-4o(0.0025,0.01)"
        )
        assertEquals(3, models.size)
        assertEquals("minimax/minimax-m3", models[0].modelId)
        assertEquals("Minimax M3", models[0].name)
        assertEquals("openai/gpt-4o-mini", models[1].modelId)
        assertEquals("GPT-4o Mini", models[1].name)
        assertEquals("openai/gpt-4o", models[2].modelId)
        assertEquals("GPT-4o", models[2].name)
    }

    @Test
    fun `parseList - handles commas inside parentheses correctly`() {
        val models = ModelInfo.parseList("model1(0.001,0.002),model2(0.003)")
        assertEquals(2, models.size)
        assertEquals("model1", models[0].modelId)
        assertEquals(0.001, models[0].costPer1kInputTokens)
        assertEquals(0.002, models[0].costPer1kOutputTokens)
        assertEquals("model2", models[1].modelId)
        assertEquals(0.003, models[1].costPer1kInputTokens)
    }
}
