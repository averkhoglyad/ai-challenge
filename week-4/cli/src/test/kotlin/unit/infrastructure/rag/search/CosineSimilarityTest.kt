package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.rag.search

import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.search.cosineSimilarity
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class CosineSimilarityTest : FreeSpec({

    "cosineSimilarity" - {

        "returns ~1.0 for identical vectors" {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(1f, 2f, 3f)

            val result = cosineSimilarity(a, b)
            (result >= 0.9999f) shouldBe true
        }

        "returns 0.0 for orthogonal vectors" {
            val a = floatArrayOf(1f, 0f, 0f)
            val b = floatArrayOf(0f, 1f, 0f)

            val result = cosineSimilarity(a, b)
            result shouldBe 0.0f
        }

        "returns ~-1.0 for opposite vectors" {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(-1f, -2f, -3f)

            val result = cosineSimilarity(a, b)
            (result <= -0.9999f) shouldBe true
        }

        "returns 0.0 for zero vectors" {
            val a = floatArrayOf(0f, 0f, 0f)
            val b = floatArrayOf(1f, 2f, 3f)

            val result = cosineSimilarity(a, b)
            result shouldBe 0.0f
        }

        "returns 0.0 for vectors of different size" {
            val a = floatArrayOf(1f, 2f)
            val b = floatArrayOf(1f, 2f, 3f)

            val result = cosineSimilarity(a, b)
            result shouldBe 0.0f
        }

        "returns value close to 1.0 for similar vectors" {
            val a = floatArrayOf(1f, 2f, 3f)
            val b = floatArrayOf(1.1f, 2.1f, 2.9f)

            val result = cosineSimilarity(a, b)
            (result > 0.99f) shouldBe true
        }
    }
})
