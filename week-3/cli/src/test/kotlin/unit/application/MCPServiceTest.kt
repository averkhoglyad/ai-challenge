package io.averkhogliad.ai.challenge.week3.cli.application

import io.averkhogliad.ai.challenge.week3.cli.application.service.MCPOperationError
import io.averkhogliad.ai.challenge.week3.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MCPConnectionManager
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MCPServerRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant

class MCPServiceTest : FreeSpec({

    lateinit var repository: MCPServerRepository
    lateinit var connectionManager: MCPConnectionManager
    lateinit var service: MCPService

    val now = Instant.parse("2025-01-01T00:00:00Z")
    val sampleId = ModelId("server-id-01")

    beforeTest {
        repository = mockk()
        connectionManager = mockk()
        service = MCPService(repository, connectionManager)
    }

    "addServer" - {
        "successfully adds a server with Stdio transport" {
            runTest {
                val config = MCPServerConfig(
                    id = sampleId,
                    name = "my-server",
                    transport = MCPTransport.Stdio(command = "echo", args = listOf("hello")),
                    createdAt = now
                )
                coEvery { repository.findByName("my-server") } returns null
                coEvery { repository.save(any<MCPServerConfig>()) } returns config

                val result =
                    service.addServer("my-server", MCPTransport.Stdio(command = "echo", args = listOf("hello")))

                result.isSuccess shouldBe true
                val saved = result.getOrThrow()
                saved.name shouldBe "my-server"
                saved.transport shouldBe MCPTransport.Stdio(command = "echo", args = listOf("hello"))
            }
        }

        "returns AlreadyExists error when name is duplicate" {
            runTest {
                val existing = MCPServerConfig(
                    id = sampleId,
                    name = "my-server",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = now
                )
                coEvery { repository.findByName("my-server") } returns existing

                val result = service.addServer("my-server", MCPTransport.Stdio(command = "echo"))

                result.isFailure shouldBe true
                val error = result.exceptionOrNull()
                error shouldBe MCPOperationError.AlreadyExists("my-server")
            }
        }

        "returns InvalidCommand error for blank Stdio command" {
            runTest {
                val result = service.addServer("my-server", MCPTransport.Stdio(command = "   "))

                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe MCPOperationError.InvalidCommand("Command must not be blank")
            }
        }

        "returns InvalidUrl error for non-HTTP URL" {
            runTest {
                val result = service.addServer("my-server", MCPTransport.StreamableHttp(url = "ftp://bad"))

                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe MCPOperationError.InvalidUrl("ftp://bad")
            }
        }

        "returns InvalidUrl error for malformed URL" {
            runTest {
                val result = service.addServer("my-server", MCPTransport.StreamableHttp(url = "not-a-url"))

                result.isFailure shouldBe true
                val error = result.exceptionOrNull()
                (error is MCPOperationError.InvalidUrl) shouldBe true
            }
        }

        "returns InvalidName error when name is too long" {
            runTest {
                coEvery { repository.findByName(any()) } returns null

                val longName = "a".repeat(51)
                val result = service.addServer(longName, MCPTransport.Stdio(command = "echo"))

                result.isFailure shouldBe true
                val error = result.exceptionOrNull()
                (error is MCPOperationError.InvalidName) shouldBe true
            }
        }
    }

    "removeServer" - {
        "successfully removes an existing disconnected server" {
            runTest {
                val config = MCPServerConfig(
                    id = sampleId,
                    name = "my-server",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = now
                )
                coEvery { repository.findById(sampleId) } returns config
                every { connectionManager.isConnected(sampleId) } returns false
                coEvery { repository.delete(sampleId) } returns Unit

                val result = service.removeServer(sampleId)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldBe "my-server"
                coVerify(exactly = 1) { repository.delete(sampleId) }
            }
        }

        "disconnects before removing if connected" {
            runTest {
                val config = MCPServerConfig(
                    id = sampleId,
                    name = "my-server",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = now
                )
                coEvery { repository.findById(sampleId) } returns config
                every { connectionManager.isConnected(sampleId) } returns true
                coEvery { connectionManager.disconnect(sampleId) } returns Unit
                coEvery { repository.delete(sampleId) } returns Unit

                val result = service.removeServer(sampleId)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldBe "my-server"
                coVerify(exactly = 1) { connectionManager.disconnect(sampleId) }
                coVerify(exactly = 1) { repository.delete(sampleId) }
            }
        }

        "returns NotFound error for unknown server" {
            runTest {
                coEvery { repository.findById(sampleId) } returns null

                val result = service.removeServer(sampleId)

                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe MCPOperationError.NotFound(sampleId.value)
            }
        }
    }

    "listServers" - {
        "returns enriched list with statuses" {
            runTest {
                val config1 = MCPServerConfig(
                    id = ModelId("id-1"),
                    name = "server-1",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = now
                )
                val config2 = MCPServerConfig(
                    id = ModelId("id-2"),
                    name = "server-2",
                    transport = MCPTransport.StreamableHttp(url = "http://localhost:8080"),
                    createdAt = now
                )
                val connectedState = MCPConnectionState.Connected(now)
                val disconnectedState = MCPConnectionState.Disconnected

                coEvery { repository.findAll() } returns listOf(config1, config2)
                every { connectionManager.getStatus(ModelId("id-1")) } returns connectedState
                every { connectionManager.getStatus(ModelId("id-2")) } returns disconnectedState

                val result = service.listServers()

                result shouldHaveSize 2
                result[0].config shouldBe config1
                result[0].status shouldBe connectedState
                result[1].config shouldBe config2
                result[1].status shouldBe disconnectedState
            }
        }

        "returns empty list when no servers exist" {
            runTest {
                coEvery { repository.findAll() } returns emptyList()

                val result = service.listServers()

                result shouldHaveSize 0
            }
        }
    }

    "connect" - {
        "succeeds with Connected state" {
            runTest {
                val connected = MCPConnectionState.Connected(now)
                coEvery { connectionManager.connect(sampleId) } returns connected

                val result = service.connect(sampleId)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldBe connected
            }
        }

        "returns NotFound when server not found" {
            runTest {
                val failed = MCPConnectionState.Failed(
                    error = "Server not found: $sampleId",
                    since = now,
                    reason = MCPFailureReason.NOT_FOUND
                )
                coEvery { connectionManager.connect(sampleId) } returns failed

                val result = service.connect(sampleId)

                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe MCPOperationError.NotFound(sampleId.value)
            }
        }

        "returns ServerDisabled when server is disabled" {
            runTest {
                val failed = MCPConnectionState.Failed(
                    error = "Server is disabled: my-server",
                    since = now,
                    reason = MCPFailureReason.DISABLED
                )
                coEvery { connectionManager.connect(sampleId) } returns failed

                val result = service.connect(sampleId)

                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe MCPOperationError.ServerDisabled(sampleId.value)
            }
        }

        "returns ConnectionFailed for other failures" {
            runTest {
                val failed = MCPConnectionState.Failed(
                    error = "Connection timeout",
                    since = now,
                    reason = MCPFailureReason.TRANSPORT_ERROR
                )
                coEvery { connectionManager.connect(sampleId) } returns failed

                val result = service.connect(sampleId)

                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe MCPOperationError.ConnectionFailed("Connection timeout")
            }
        }

        "returns Connecting state as success" {
            runTest {
                coEvery { connectionManager.connect(sampleId) } returns MCPConnectionState.Connecting

                val result = service.connect(sampleId)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldBe MCPConnectionState.Connecting
            }
        }

        "returns Disconnected state as success" {
            runTest {
                coEvery { connectionManager.connect(sampleId) } returns MCPConnectionState.Disconnected

                val result = service.connect(sampleId)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldBe MCPConnectionState.Disconnected
            }
        }
    }

    "disconnect" - {
        "delegates to connectionManager" {
            runTest {
                coEvery { connectionManager.disconnect(sampleId) } returns Unit

                service.disconnect(sampleId)

                coVerify(exactly = 1) { connectionManager.disconnect(sampleId) }
            }
        }
    }

    "getTools" - {
        "returns tools when connected" {
            runTest {
                val tools = listOf(
                    MCPTool(name = "tool1", description = "First tool", parametersSchema = "{}"),
                    MCPTool(name = "tool2", description = null, parametersSchema = "{}")
                )
                every { connectionManager.isConnected(sampleId) } returns true
                coEvery { connectionManager.getTools(sampleId) } returns tools

                val result = service.getTools(sampleId)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldHaveSize 2
                result.getOrThrow()[0].name shouldBe "tool1"
                result.getOrThrow()[1].name shouldBe "tool2"
            }
        }

        "returns NotConnected error when not connected" {
            runTest {
                every { connectionManager.isConnected(sampleId) } returns false

                val result = service.getTools(sampleId)

                result.isFailure shouldBe true
                result.exceptionOrNull() shouldBe MCPOperationError.NotConnected(sampleId.value)
            }
        }
    }
})
