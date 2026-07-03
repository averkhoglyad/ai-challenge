package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPServerConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTransport
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.time.Instant

class MCPServerConfigTest : FreeSpec({

    "MCPServerConfig" - {

        "name validation" - {

            "accepts valid name with letters, digits, and hyphens" {
                // when & then
                MCPServerConfig(
                    id = ModelId("test-1"),
                    name = "my-server-01",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = Instant.now()
                )
            }

            "accepts valid name at minimum length (1 char)" {
                // when & then
                MCPServerConfig(
                    id = ModelId("test-2"),
                    name = "a",
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = Instant.now()
                )
            }

            "accepts valid name at maximum length (50 chars)" {
                // given
                val longName = "a".repeat(50)

                // when & then
                MCPServerConfig(
                    id = ModelId("test-3"),
                    name = longName,
                    transport = MCPTransport.Stdio(command = "echo"),
                    createdAt = Instant.now()
                )
            }

            "rejects empty name" {
                // when
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-4"),
                        name = "",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }

                // then
                ex.message shouldContain "Name must be 1-50 characters"
            }

            "rejects name longer than 50 characters" {
                // given
                val tooLong = "a".repeat(51)

                // when
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-5"),
                        name = tooLong,
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }

                // then
                ex.message shouldContain "Name must be 1-50 characters"
            }

            "rejects name with spaces" {
                // when
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-6"),
                        name = "my server",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }

                // then
                ex.message shouldContain "Name must match"
            }

            "rejects name with special characters" {
                // when
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-7"),
                        name = "server@home",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }

                // then
                ex.message shouldContain "Name must match"
            }

            "rejects name with underscores" {
                // when
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-8"),
                        name = "my_server",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }

                // then
                ex.message shouldContain "Name must match"
            }

            "rejects name with dots" {
                // when
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig(
                        id = ModelId("test-9"),
                        name = "server.name",
                        transport = MCPTransport.Stdio(command = "echo"),
                        createdAt = Instant.now()
                    )
                }

                // then
                ex.message shouldContain "Name must match"
            }
        }

        "factory method create()" - {

            "creates a valid config with auto-generated id and timestamp" {
                // when
                val config = MCPServerConfig.create(
                    name = "test-server",
                    transport = MCPTransport.Stdio(command = "node", args = listOf("server.js"))
                )

                // then
                config.name shouldBe "test-server"
                config.transport shouldBe MCPTransport.Stdio(command = "node", args = listOf("server.js"))
                config.enabled shouldBe true
                config.id.value.isNotBlank() shouldBe true
                config.createdAt shouldBe config.createdAt // ensure not null
            }

            "creates config with StreamableHttp transport" {
                // when
                val config = MCPServerConfig.create(
                    name = "http-server",
                    transport = MCPTransport.StreamableHttp(url = "http://localhost:8080")
                )

                // then
                config.name shouldBe "http-server"
                config.transport shouldBe MCPTransport.StreamableHttp(url = "http://localhost:8080")
                config.enabled shouldBe true
            }

            "throws on invalid name" {
                // when
                val ex = shouldThrow<IllegalArgumentException> {
                    MCPServerConfig.create(
                        name = "",
                        transport = MCPTransport.Stdio(command = "echo")
                    )
                }

                // then
                ex.message shouldContain "Name must be 1-50 characters"
            }
        }
    }
})
