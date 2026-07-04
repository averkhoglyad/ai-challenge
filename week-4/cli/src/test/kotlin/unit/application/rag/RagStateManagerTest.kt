package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.DefaultRagStateManager
import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagStateManager
import io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow

class RagStateManagerTest : FreeSpec({

    lateinit var stateManager: RagStateManager
    lateinit var stateHolder: MutableStateFlow<RagSessionState>
    val config = RagConfig(relevanceThreshold = 0.70f)

    beforeEach {
        stateHolder = MutableStateFlow(
            RagSessionState(relevanceThreshold = config.relevanceThreshold)
        )
        stateManager = DefaultRagStateManager(config, stateHolder)
    }

    "updateRelevanceThreshold" - {

        "updates threshold in state" {
            stateManager.updateRelevanceThreshold(0.85f)
            stateManager.getState().relevanceThreshold shouldBe 0.85f
        }

        "multiple updates work correctly" {
            stateManager.updateRelevanceThreshold(0.60f)
            stateManager.getState().relevanceThreshold shouldBe 0.60f
            stateManager.updateRelevanceThreshold(0.95f)
            stateManager.getState().relevanceThreshold shouldBe 0.95f
        }

        "accepts boundary values" {
            stateManager.updateRelevanceThreshold(0.0f)
            stateManager.getState().relevanceThreshold shouldBe 0.0f
            stateManager.updateRelevanceThreshold(1.0f)
            stateManager.getState().relevanceThreshold shouldBe 1.0f
        }
    }

    "resetToDefaults" - {

        "resets threshold to config value" {
            stateManager.updateRelevanceThreshold(0.90f)
            stateManager.getState().relevanceThreshold shouldBe 0.90f
            stateManager.resetToDefaults()
            stateManager.getState().relevanceThreshold shouldBe 0.70f
        }

        "reset is idempotent" {
            stateManager.resetToDefaults()
            stateManager.getState().relevanceThreshold shouldBe 0.70f
            stateManager.resetToDefaults()
            stateManager.getState().relevanceThreshold shouldBe 0.70f
        }
    }

    "getState" - {

        "returns current state" {
            val state = stateManager.getState()
            state.relevanceThreshold shouldBe 0.70f
            state.enabled shouldBe false
        }

        "reflects latest changes" {
            stateManager.updateRelevanceThreshold(0.55f)
            val state = stateManager.getState()
            state.relevanceThreshold shouldBe 0.55f
        }
    }
})
