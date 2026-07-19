package io.averkhogliad.ai.challenge.week6.unit.application

import io.averkhogliad.ai.challenge.llm.chat.ChatResponse
import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.application.AgentLoopService
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.ToolRegistryImpl
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
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
