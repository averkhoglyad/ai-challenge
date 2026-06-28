package io.averkhogliad.ai.challenge.week3.cli.domain.model

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import java.time.Instant

class MCPConnectionStateTest : FreeSpec({

    "MCPConnectionState" - {

        "Disconnected" - {
            "isConnected() returns false" {
                MCPConnectionState.Disconnected.isConnected() shouldBe false
            }

            "statusDescription() returns non-empty string" {
                MCPConnectionState.Disconnected.statusDescription() shouldBe "Disconnected"
            }
        }

        "Connecting" - {
            "isConnected() returns false" {
                MCPConnectionState.Connecting.isConnected() shouldBe false
            }

            "statusDescription() returns non-empty string" {
                MCPConnectionState.Connecting.statusDescription() shouldBe "Connecting"
            }
        }

        "Connected" - {
            "isConnected() returns true" {
                val now = Instant.now()
                MCPConnectionState.Connected(now).isConnected() shouldBe true
            }

            "statusDescription() includes connectedAt timestamp" {
                val now = Instant.now()
                val desc = MCPConnectionState.Connected(now).statusDescription()
                desc shouldBe "Connected since $now"
            }

            "has connectedAt timestamp" {
                val now = Instant.now()
                val state = MCPConnectionState.Connected(now)
                state.connectedAt shouldBe now
            }
        }

        "Failed" - {
            "isConnected() returns false" {
                MCPConnectionState.Failed(
                    error = "Connection refused",
                    since = Instant.now()
                ).isConnected() shouldBe false
            }

            "statusDescription() includes error message and since timestamp" {
                val now = Instant.now()
                val state = MCPConnectionState.Failed(
                    error = "Connection refused",
                    since = now
                )
                state.statusDescription() shouldBe "Failed: Connection refused (since $now)"
                state.statusDescription().shouldNotBeEmpty()
            }

            "has error message" {
                val state = MCPConnectionState.Failed(
                    error = "Something went wrong",
                    since = Instant.now()
                )
                state.error shouldBe "Something went wrong"
            }

            "has since timestamp" {
                val now = Instant.now()
                val state = MCPConnectionState.Failed(
                    error = "Boom",
                    since = now
                )
                state.since shouldBe now
            }

            "reason defaults to TRANSPORT_ERROR" {
                val state = MCPConnectionState.Failed(
                    error = "Oops",
                    since = Instant.now()
                )
                state.reason shouldBe MCPFailureReason.TRANSPORT_ERROR
            }

            "reason can be explicitly set" {
                val state = MCPConnectionState.Failed(
                    error = "Not found",
                    since = Instant.now(),
                    reason = MCPFailureReason.NOT_FOUND
                )
                state.reason shouldBe MCPFailureReason.NOT_FOUND
            }
        }
    }
})
