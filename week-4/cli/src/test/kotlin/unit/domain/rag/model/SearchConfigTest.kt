package io.averkhogliad.ai.challenge.week4.cli.unit.domain.rag.model

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class SearchConfigTest : FreeSpec({

    "SearchConfig" - {

        "valid config creates successfully with default values" {
            val config = SearchConfig()
            config.mode shouldBe SearchMode.Filtered
            config.topKInitial shouldBe 50
            config.topKFinal shouldBe 5
            config.threshold shouldBe 0.75f
        }

        "topKInitial must be > 0" {
            shouldThrow<IllegalArgumentException> {
                SearchConfig(topKInitial = 0)
            }
            shouldThrow<IllegalArgumentException> {
                SearchConfig(topKInitial = -1)
            }
        }

        "topKFinal must be > 0" {
            shouldThrow<IllegalArgumentException> {
                SearchConfig(topKFinal = 0)
            }
            shouldThrow<IllegalArgumentException> {
                SearchConfig(topKFinal = -1)
            }
        }

        "topKFinal must be <= topKInitial" {
            shouldThrow<IllegalArgumentException> {
                SearchConfig(topKInitial = 5, topKFinal = 10)
            }
            // Equal is fine
            SearchConfig(topKInitial = 10, topKFinal = 10).topKFinal shouldBe 10
        }

        "threshold must be 0.0..1.0" {
            shouldThrow<IllegalArgumentException> {
                SearchConfig(threshold = -0.01f)
            }
            shouldThrow<IllegalArgumentException> {
                SearchConfig(threshold = 1.01f)
            }
            // Boundaries are fine
            SearchConfig(threshold = 0.0f).threshold shouldBe 0.0f
            SearchConfig(threshold = 1.0f).threshold shouldBe 1.0f
        }

        "copy preserves immutable semantics" {
            val original = SearchConfig(mode = SearchMode.Raw, topKInitial = 30, topKFinal = 3, threshold = 0.5f)
            val modified = original.copy(mode = SearchMode.Reranked)
            modified.mode shouldBe SearchMode.Reranked
            original.mode shouldBe SearchMode.Raw // original unchanged
        }
    }
})
