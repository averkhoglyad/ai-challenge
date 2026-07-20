package io.averkhogliad.ai.challenge.week6.unit.application

import io.averkhogliad.ai.challenge.llm.chat.*
import io.averkhogliad.ai.challenge.week6.application.AgentLoopService
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.ToolRegistryImpl
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolDefinition
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Path

class AgentLoopServiceTest : FreeSpec({

    lateinit var llmClient: LlmClient
    lateinit var toolRegistry: ToolRegistryImpl
    lateinit var projectContextProvider: ProjectContextProvider
    lateinit var service: AgentLoopService

    beforeEach {
        llmClient = mockk()
        toolRegistry = ToolRegistryImpl()
        projectContextProvider = mockk()
        service = AgentLoopService(llmClient, toolRegistry, projectContextProvider)
    }

    "processQuery" - {

        "returns llm response when no tool calls" {
            runTest {
                // given
                val ctx = ProjectContext(
                    projectId = "test-1",
                    rootPath = Path.of("/tmp/test"),
                    docsPaths = emptyList(),
                    isGitEnabled = false,
                )
                coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
                coEvery { llmClient.chat(any(), any(), any(), any(), any()) } returns
                        ChatResponse(content = "Hello from LLM", finishReason = "stop", usage = null)

                // when
                val results = service.processQuery("Hello").toList()

                // then
                results shouldBe listOf("Hello from LLM")
            }
        }

        "instructs the llm to inspect the project and cite sources" {
            runTest {
                // given
                val ctx = ProjectContext(
                    projectId = "test-1",
                    rootPath = Path.of("/tmp/test"),
                    docsPaths = listOf(Path.of("/tmp/test/docs")),
                    isGitEnabled = false,
                )
                val systemPrompt = slot<String>()
                coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
                coEvery { llmClient.chat(any(), capture(systemPrompt), any(), any(), any()) } returns
                        ChatResponse(content = "Ответ", finishReason = "stop", usage = null)

                // when
                service.processQuery("Как устроен проект?").toList()

                // then
                systemPrompt.captured shouldContain "сначала используй"
                systemPrompt.captured shouldContain "Не отвечай по предположениям"
                systemPrompt.captured shouldContain "path/to/file:123"
                coVerify { llmClient.chat(any(), any(), any(), any(), any()) }
            }
        }

        "passes document citation metadata to the final tool-call response" {
            runTest {
                // given
                val ctx = ProjectContext(
                    projectId = "test-1",
                    rootPath = Path.of("/tmp/test"),
                    docsPaths = listOf(Path.of("/tmp/test/docs")),
                    isGitEnabled = false,
                )
                val searchDocsTool = object : Tool {
                    override val definition = ToolDefinition(
                        name = "search_docs",
                        description = "Search documentation",
                        inputSchema = buildJsonObject { put("type", "object") },
                    )

                    override suspend fun execute(arguments: JsonObject): ToolResult =
                        ToolResult.Success("--- Result 1 (source: docs/architecture.md:10-14) ---\nArchitecture details")
                }
                toolRegistry.register(searchDocsTool)
                val toolCall = ToolCall(
                    id = "call-1",
                    function = FunctionCall("search_docs", "{\"query\":\"architecture\"}"),
                )
                val messages = slot<List<ChatMessage>>()
                coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
                coEvery { llmClient.chat(any(), any(), any(), any(), any()) } returns ChatResponse(
                    content = null,
                    finishReason = "tool_calls",
                    usage = null,
                    toolCalls = listOf(toolCall),
                )
                coEvery { llmClient.chatWithMessages(capture(messages), any(), any(), any()) } returns ChatResponse(
                    content = "Описание архитектуры: docs/architecture.md:10-14",
                    finishReason = "stop",
                    usage = null,
                )

                // when
                val results = service.processQuery("Как устроена архитектура?").toList()

                // then
                messages.captured.single { it.role == "tool" }.content shouldContain "docs/architecture.md:10-14"
                results shouldBe listOf("Описание архитектуры: docs/architecture.md:10-14")
            }
        }

        "returns fallback when llm response is empty" {
            runTest {
                // given
                val ctx = ProjectContext(
                    projectId = "test-1",
                    rootPath = Path.of("/tmp/test"),
                    docsPaths = emptyList(),
                    isGitEnabled = false,
                )
                coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
                coEvery { llmClient.chat(any(), any(), any(), any(), any()) } returns
                        ChatResponse(content = "   ", finishReason = "stop", usage = null)

                // when
                val results = service.processQuery("Неизвестный вопрос").toList()

                // then
                results shouldBe listOf(
                    "Не могу ответить на этот вопрос по доступной информации проекта. " +
                            "Уточните вопрос или добавьте документацию."
                )
            }
        }

        "returns error when project context fails" {
            runTest {
                // given
                coEvery { projectContextProvider.getContext() } returns
                        DomainResult.Failure(
                            io.averkhogliad.ai.challenge.week6.domain.error.DomainError.NoActiveProject()
                        )

                // when
                val results = service.processQuery("Hello").toList()

                // then
                results.size shouldBe 1
                results[0] shouldContain "Ошибка получения контекста проекта"
            }
        }
    }
})
