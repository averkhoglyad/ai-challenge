package io.averkhogliad.ai.challenge.week4.cli.unit.domain.indexer.model

import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.model.Embedding
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.util.*

class EmbeddingTest : FreeSpec({

    "equals" - {

        "should return true for identical embeddings" {
            // given
            val chunkId = UUID.randomUUID()
            val vector = floatArrayOf(1.0f, 2.0f, 3.0f)
            val model = "nomic-embed-text"

            val e1 = Embedding(chunkId = chunkId, vector = vector, model = model)
            val e2 = Embedding(chunkId = chunkId, vector = vector, model = model)

            // when & then
            e1 shouldBe e2
            e1.hashCode() shouldBe e2.hashCode()
        }

        "should return false for different chunkId" {
            // given
            val vector = floatArrayOf(1.0f, 2.0f)
            val model = "nomic-embed-text"

            val e1 = Embedding(chunkId = UUID.randomUUID(), vector = vector.copyOf(), model = model)
            val e2 = Embedding(chunkId = UUID.randomUUID(), vector = vector.copyOf(), model = model)

            // when & then
            (e1 == e2) shouldBe false
        }

        "should return false for different vectors with same content" {
            // given
            val chunkId = UUID.randomUUID()
            val model = "nomic-embed-text"

            val e1 = Embedding(chunkId = chunkId, vector = floatArrayOf(1.0f, 2.0f), model = model)
            val e2 = Embedding(chunkId = chunkId, vector = floatArrayOf(3.0f, 4.0f), model = model)

            // when & then
            (e1 == e2) shouldBe false
        }

        "should return false for different model names" {
            // given
            val chunkId = UUID.randomUUID()
            val vector = floatArrayOf(1.0f, 2.0f)

            val e1 = Embedding(chunkId = chunkId, vector = vector.copyOf(), model = "model-a")
            val e2 = Embedding(chunkId = chunkId, vector = vector.copyOf(), model = "model-b")

            // when & then
            (e1 == e2) shouldBe false
        }

        "should return true for FloatArray contentEquals (not reference equality)" {
            // given — different array instances with same content
            val chunkId = UUID.randomUUID()
            val model = "nomic-embed-text"

            val e1 = Embedding(chunkId = chunkId, vector = floatArrayOf(0.1f, 0.2f), model = model)
            val e2 = Embedding(chunkId = chunkId, vector = floatArrayOf(0.1f, 0.2f), model = model)

            // when & then — FloatArray uses contentEquals in overridden equals
            e1 shouldBe e2
        }
    }

    "hashCode" - {

        "should be consistent for equal embeddings" {
            // given
            val chunkId = UUID.randomUUID()
            val vector = floatArrayOf(1.0f, 2.0f, 3.0f)
            val model = "text-embedding-3-small"

            val e1 = Embedding(chunkId = chunkId, vector = vector, model = model)
            val e2 = Embedding(chunkId = chunkId, vector = vector, model = model)

            // when & then — contentHashCode ensures same hash for same content
            e1.hashCode() shouldBe e2.hashCode()
        }
    }
})
