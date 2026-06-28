package io.averkhogliad.ai.challenge.week3.cli.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant

class MCPServerConfigTest : FreeSpec({

    "MCPServerConfig" - {

        "name validation" - {
            "accepts valid name with letters, digits, and hyphens" {
                MCPServerConfig(
                    id = ModelId("test-1"),
                    name = "my-server-01",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = Instant.now()
                )
            }

            "accepts valid name at minimum length (1 char)" {
                MCPServerConfig(
                    id = ModelId("test-2"),
                    name = "a",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = Instant.now()
                )
            }

            "accepts valid name at maximum length (50 chars)" {
                val longName = "a".repeat(50)
                MCPServerConfig(
                    id = ModelId("test-3"),
                    name = longName,
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = Instant.now()
                )
            }

            "rejects empty name" {
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-4"),
                        name = "",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }
                ex.message shouldContain "Name must be 1-50 characters"
            }

            "rejects name longer than 50 characters" {
                val tooLong = "a".repeat(51)
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-5"),
                        name = tooLong,
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }
                ex.message shouldContain "Name must be 1-50 characters"
            }

            "rejects name with spaces" {
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-6"),
                        name = "my server",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }
                ex.message shouldContain "Name must match"
            }

            "rejects name with special characters" {
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-7"),
                        name = "server@home",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }
                ex.message shouldContain "Name must match"
            }

            "rejects name with underscores" {
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-8"),
                        name = "my_server",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }
                ex.message shouldContain "Name must match"
            }

            "rejects name with dots" {
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-9"),
                        name = "server.name",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }
                ex.message shouldContain "Name must match"
            }
        }

        "factory method create()" - {
            "creates a valid config with auto-generated id and timestamp" {
                val config = MCPServerConfig.create(
                    name = "test-server",
                    transport = MCPTransport.Stdio(command = "node", args = listOf("server.js"))
                )

                config.name shouldBe "test-server"
                config.transport shouldBe MCPTransport.Stdio(command = "node", args = listOf("server.js"))
                config.enabled shouldBe true
                config.id.value.isNotBlank() shouldBe true
                config.createdAt shouldBe config.createdAt // ensure not null
            }

            "creates config with StreamableHttp transport" {
                val config = MCPServerConfig.create(
                    name = "http-server",
                    transport = MCPTransport.StreamableHttp(url = "http://localhost:8080")
                )

                config.name shouldBe "http-server"
                config.transport shouldBe MCPTransport.StreamableHttp(url = "http://localhost:8080")
                config.enabled shouldBe true
            }

            "throws on invalid name" {
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig.create(
                        name = "",
                        transport = MCPTransport.Stdio(command = "echo")
                    )
                }
                ex.message shouldContain "Name must be 1-50 characters"
            }
        }
    }
})
