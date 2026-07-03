package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPFailureReason
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import java.time.Instant

class MCPConnectionStateTest : FreeSpec({

    "MCPConnectionState" - {

        "Disconnected" - {

            "isConnected() returns false" {
                // when
                val result = MCPConnectionState.Disconnected.isConnected()

                // then
                result shouldBe false
            }

            "statusDescription() returns non-empty string" {
                // when
                val result = MCPConnectionState.Disconnected.statusDescription()

                // then
                result shouldBe "Disconnected"
            }
        }

        "Connecting" - {

            "isConnected() returns false" {
                // when
                val result = MCPConnectionState.Connecting.isConnected()

                // then
                result shouldBe false
            }

            "statusDescription() returns non-empty string" {
                // when
                val result = MCPConnectionState.Connecting.statusDescription()

                // then
                result shouldBe "Connecting"
            }
        }

        "Connected" - {

            "isConnected() returns true" {
                // given
                val now = Instant.now()

                // when
                val result = MCPConnectionState.Connected(now).isConnected()

                // then
                result shouldBe true
            }

            "statusDescription() includes connectedAt timestamp" {
                // given
                val now = Instant.now()

                // when
                val desc = MCPConnectionState.Connected(now).statusDescription()

                // then
                desc shouldBe "Connected since $now"
            }

            "has connectedAt timestamp" {
                // given
                val now = Instant.now()

                // when
                val state = MCPConnectionState.Connected(now)

                // then
                state.connectedAt shouldBe now
            }
        }

        "Failed" - {

            "isConnected() returns false" {
                // given
                val state = MCPConnectionState.Failed(
                    error = "Connection refused",
                    since = Instant.now()
                )

                // when
                val result = state.isConnected()

                // then
                result shouldBe false
            }

            "statusDescription() includes error message and since timestamp" {
                // given
                val now = Instant.now()
                val state = MCPConnectionState.Failed(
                    error = "Connection refused",
                    since = now
                )

                // when
                val result = state.statusDescription()

                // then
                result shouldBe "Failed: Connection refused (since $now)"
                result.shouldNotBeEmpty()
            }

            "has error message" {
                // given
                val state = MCPConnectionState.Failed(
                    error = "Something went wrong",
                    since = Instant.now()
                )

                // when & then
                state.error shouldBe "Something went wrong"
            }

            "has since timestamp" {
                // given
                val now = Instant.now()
                val state = MCPConnectionState.Failed(
                    error = "Boom",
                    since = now
                )

                // when & then
                state.since shouldBe now
            }

            "reason defaults to TRANSPORT_ERROR" {
                // given
                val state = MCPConnectionState.Failed(
                    error = "Oops",
                    since = Instant.now()
                )

                // when & then
                state.reason shouldBe MCPFailureReason.TRANSPORT_ERROR
            }

            "reason can be explicitly set" {
                // given
                val state = MCPConnectionState.Failed(
                    error = "Not found",
                    since = Instant.now(),
                    reason = MCPFailureReason.NOT_FOUND
                )

                // when & then
                state.reason shouldBe MCPFailureReason.NOT_FOUND
            }
        }
    }
})
