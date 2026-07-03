package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.mcp

import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPFailureReason
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPServerConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTransport
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MCPServerRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.mcp.DefaultMCPConnectionManager
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant

class DefaultMCPConnectionManagerTest : FreeSpec({

    lateinit var repository: MCPServerRepository
    lateinit var manager: DefaultMCPConnectionManager

    val now = Instant.parse("2025-01-01T00:00:00Z")
    val sampleId = ModelId("server-id-01")

    beforeTest {
        repository = mockk()
        manager = DefaultMCPConnectionManager(repository)
    }

    "connect" - {

        "returns Failed when server not found" {
            runTest {
                // given
                coEvery { repository.findById(sampleId) } returns null

                // when
                val state = manager.connect(sampleId)

                // then
                (state is MCPConnectionState.Failed) shouldBe true
                val failed = state as MCPConnectionState.Failed
                failed.error shouldBe "Server not found: $sampleId"
                failed.reason shouldBe MCPFailureReason.NOT_FOUND
            }
        }

        "returns Failed when server is disabled" {
            runTest {
                // given
                val config = MCPServerConfig(
                    id = sampleId,
                    name = "disabled-server",
                    transport = MCPTransport.Stdio(command = "echo"),
                    enabled = false,
                    createdAt = now
                )
                coEvery { repository.findById(sampleId) } returns config

                // when
                val state = manager.connect(sampleId)

                // then
                (state is MCPConnectionState.Failed) shouldBe true
                val failed = state as MCPConnectionState.Failed
                failed.error shouldBe "Server is disabled: disabled-server"
                failed.reason shouldBe MCPFailureReason.DISABLED
            }
        }
    }

    "getStatus" - {

        "returns Disconnected for unknown server" {
            // when
            val status = manager.getStatus(sampleId)

            // then
            status shouldBe MCPConnectionState.Disconnected
        }

        "returns Disconnected for known but never connected server" {
            // when
            // No prior connect call, so no client exists
            val status = manager.getStatus(sampleId)

            // then
            status shouldBe MCPConnectionState.Disconnected
        }
    }

    "isConnected" - {

        "returns false for unknown server" {
            // when
            val result = manager.isConnected(sampleId)

            // then
            result shouldBe false
        }
    }
})
