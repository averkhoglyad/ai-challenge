package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagConfigService
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class RagConfigServiceTest : FreeSpec({

    val service = RagConfigService()

    "setMode" - {

        "updates mode in config" {
            val state = RagSessionState()
            val newState = service.setMode(state, SearchMode.Reranked)
            newState.config.mode shouldBe SearchMode.Reranked
            state.config.mode shouldBe SearchMode.Filtered // original unchanged
        }
    }

    "setThreshold" - {

        "updates threshold with valid value" {
            val state = RagSessionState()
            val newState = service.setThreshold(state, 0.85f)
            newState.config.threshold shouldBe 0.85f
        }

        "validates via SearchConfig.init" {
            val state = RagSessionState()
            shouldThrow<IllegalArgumentException> {
                service.setThreshold(state, 1.5f)
            }
        }
    }

    "setTopK" - {

        "updates both initial and final" {
            val state = RagSessionState()
            val newState = service.setTopK(state, 100, 10)
            newState.config.topKInitial shouldBe 100
            newState.config.topKFinal shouldBe 10
        }

        "validates via SearchConfig.init" {
            val state = RagSessionState()
            shouldThrow<IllegalArgumentException> {
                service.setTopK(state, 5, 10) // final > initial
            }
        }
    }

    "all methods return new state via copy (immutability)" {
        val original = RagSessionState()
        val afterMode = service.setMode(original, SearchMode.Raw)
        val afterThreshold = service.setThreshold(afterMode, 0.9f)
        val afterTopK = service.setTopK(afterThreshold, 80, 8)

        // Each step produces a new object
        original.config.mode shouldBe SearchMode.Filtered
        afterMode.config.mode shouldBe SearchMode.Raw
        afterThreshold.config.threshold shouldBe 0.9f
        afterTopK.config.topKInitial shouldBe 80
        afterTopK.config.topKFinal shouldBe 8
    }

    "getConfig" - {

        "returns current config" {
            val state = RagSessionState()
            val config = service.getConfig(state)
            config.mode shouldBe SearchMode.Filtered
        }
    }
})
