package io.averkhogliad.ai.challenge.llm.chat

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
        assertNull(model.costPerMillionInputTokens)
        assertNull(model.costPerMillionOutputTokens)
    }

    @Test
    fun `parse - id with name`() {
        val model = ModelInfo.parse("minimax/minimax-m3:Minimax M3")
        assertEquals("minimax/minimax-m3", model.modelId)
        assertEquals("Minimax M3", model.name)
        assertNull(model.costPerMillionInputTokens)
        assertNull(model.costPerMillionOutputTokens)
    }

    @Test
    fun `parse - id with single cost`() {
        val model = ModelInfo.parse("minimax/minimax-m3(0.1)")
        assertEquals("minimax/minimax-m3", model.modelId)
        assertEquals("minimax/minimax-m3", model.name)
        assertEquals(0.1, model.costPerMillionInputTokens)
        assertNull(model.costPerMillionOutputTokens)
    }

    @Test
    fun `parse - id with input and output cost`() {
        val model = ModelInfo.parse("openai/gpt-4o(2.5,10.0)")
        assertEquals("openai/gpt-4o", model.modelId)
        assertEquals("openai/gpt-4o", model.name)
        assertEquals(2.5, model.costPerMillionInputTokens)
        assertEquals(10.0, model.costPerMillionOutputTokens)
    }

    @Test
    fun `parse - full format id name cost`() {
        val model = ModelInfo.parse("minimax/minimax-m3:Minimax M3(0.1)")
        assertEquals("minimax/minimax-m3", model.modelId)
        assertEquals("Minimax M3", model.name)
        assertEquals(0.1, model.costPerMillionInputTokens)
        assertNull(model.costPerMillionOutputTokens)
    }

    @Test
    fun `parse - full format with different costs`() {
        val model = ModelInfo.parse("openai/gpt-4o-mini:GPT-4o Mini(0.15,0.6)")
        assertEquals("openai/gpt-4o-mini", model.modelId)
        assertEquals("GPT-4o Mini", model.name)
        assertEquals(0.15, model.costPerMillionInputTokens)
        assertEquals(0.6, model.costPerMillionOutputTokens)
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
        val model = ModelInfo("model-id", costPerMillionInputTokens = 1.0)
        // 1000 input tokens * 1.0/1_000_000 + 500 output tokens * 1.0/1_000_000 = 0.001 + 0.0005 = 0.0015
        assertEquals(0.0015, model.calculateCost(1000, 500))
    }

    @Test
    fun `calculateCost - different costs`() {
        val model = ModelInfo(
            "model-id",
            costPerMillionInputTokens = 2.0,
            costPerMillionOutputTokens = 10.0
        )
        // 1000 input * 2.0/1_000_000 + 500 output * 10.0/1_000_000 = 0.002 + 0.005 = 0.007
        assertEquals(0.007, model.calculateCost(1000, 500))
    }

    @Test
    fun `calculateCost - zero tokens`() {
        val model = ModelInfo("model-id", costPerMillionInputTokens = 1.0)
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
        val model = ModelInfo("model-id", costPerMillionInputTokens = 0.1)
        val result = model.formatTariff()
        assertEquals("₽0.10/1M токенов", result)
    }

    @Test
    fun `formatTariff - different costs`() {
        val model = ModelInfo(
            "model-id",
            costPerMillionInputTokens = 2.5,
            costPerMillionOutputTokens = 10.0
        )
        val result = model.formatTariff()
        assertEquals("₽2.50/1M input, ₽10.00/1M output", result)
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
            "minimax/minimax-m3:Minimax M3(0.1),openai/gpt-4o-mini:GPT-4o Mini(0.15,0.6),openai/gpt-4o:GPT-4o(2.5,10.0)"
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
        val models = ModelInfo.parseList("model1(1.0,2.0),model2(3.0)")
        assertEquals(2, models.size)
        assertEquals("model1", models[0].modelId)
        assertEquals(1.0, models[0].costPerMillionInputTokens)
        assertEquals(2.0, models[0].costPerMillionOutputTokens)
        assertEquals("model2", models[1].modelId)
        assertEquals(3.0, models[1].costPerMillionInputTokens)
    }
}
