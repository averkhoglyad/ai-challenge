package io.averkhogliad.ai.challenge.week6.unit.infrastructure.tools

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import io.averkhogliad.ai.challenge.week6.infrastructure.tools.GitBuiltinTool
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.nio.file.Path

class GitBuiltinToolTest : FreeSpec({

    // ── Helpers ───────────────────────────────────────────────────

    fun noArgs(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
        put("required", buildJsonArray { })
    }

    fun diffArgs(base: String, head: String? = null, maxLines: Int? = null): JsonObject =
        buildJsonObject {
            put("base", JsonPrimitive(base))
            head?.let { put("head", JsonPrimitive(it)) }
            maxLines?.let { put("maxLines", JsonPrimitive(it)) }
        }

    fun logArgs(range: String, limit: Int? = null, maxLines: Int? = null): JsonObject =
        buildJsonObject {
            put("range", JsonPrimitive(range))
            limit?.let { put("limit", JsonPrimitive(it)) }
            maxLines?.let { put("maxLines", JsonPrimitive(it)) }
        }

    fun mockCtx(
        projectId: String = "test-project",
        rootPath: Path = Path.of("/fake"),
        isGitEnabled: Boolean = true,
    ): ProjectContextProvider {
        val ctx = ProjectContext(projectId, rootPath, emptyList(), isGitEnabled = isGitEnabled)
        val provider = mockk<ProjectContextProvider>()
        coEvery { provider.getContext() } returns DomainResult.Success(ctx)
        return provider
    }

    // ── git_branch ────────────────────────────────────────────────

    "git_branch" - {
        "returns current branch on success" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getCurrentBranch(any()) } returns DomainResult.Success("main")
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_branch" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "Current branch: main"
            }
        }

        "returns error when git fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getCurrentBranch(any()) } returns
                        DomainResult.Failure(DomainError.repository("git not installed"))
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_branch" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "git not installed"
            }
        }

        "returns error when no active project" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Success(null)
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_branch" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "No active project"
            }
        }

        "returns error when context resolution fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Failure(DomainError.repository("db error"))
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_branch" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "db error"
            }
        }
    }

    // ── git_status ────────────────────────────────────────────────

    "git_status" - {
        "returns DIRTY when uncommitted changes exist" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.checkGitStatus(any()) } returns DomainResult.Success(true)
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_status" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "DIRTY"
            }
        }

        "returns CLEAN when no uncommitted changes" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.checkGitStatus(any()) } returns DomainResult.Success(false)
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_status" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "CLEAN"
            }
        }

        "returns error when git fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.checkGitStatus(any()) } returns
                        DomainResult.Failure(DomainError.repository("not a git repo"))
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_status" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "not a git repo"
            }
        }

        "returns error when context resolution fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Failure(DomainError.repository("db error"))
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_status" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "db error"
            }
        }

        "returns error when no active project" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Success(null)
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_status" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "No active project"
            }
        }
    }

    // ── git_diff ──────────────────────────────────────────────────

    "git_diff" - {
        "returns diff between two commits" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getDiffBetweenCommits(any(), "HEAD~1", "HEAD") } returns
                        DomainResult.Success("diff --git a/A.kt b/A.kt\n+added line")
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_diff" }

                val result = tool.execute(diffArgs("HEAD~1", "HEAD"))

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "diff --git a/A.kt"
                result.content shouldContain "+added line"
            }
        }

        "truncates output when exceeds maxLines" {
            runTest {
                val gitPort = mockk<GitPort>()
                val longDiff = (1..10).joinToString("\n") { "line $it" }
                coEvery { gitPort.getDiffBetweenCommits(any(), any(), any()) } returns DomainResult.Success(longDiff)
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_diff" }

                val result = tool.execute(diffArgs("HEAD~1", maxLines = 3))

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "truncated"
                result.content shouldContain "total 10"
            }
        }

        "returns error when base parameter missing" {
            runTest {
                val gitPort = mockk<GitPort>()
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_diff" }

                val result = tool.execute(buildJsonObject { })

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "Missing required parameter: base"
            }
        }

        "returns error when git fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getDiffBetweenCommits(any(), any(), any()) } returns
                        DomainResult.Failure(DomainError.repository("bad revision"))
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_diff" }

                val result = tool.execute(diffArgs("bad-ref"))

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "bad revision"
            }
        }

        "returns error when context resolution fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Failure(DomainError.repository("db error"))
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_diff" }

                val result = tool.execute(diffArgs("HEAD~1"))

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "db error"
            }
        }

        "returns error when no active project" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Success(null)
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_diff" }

                val result = tool.execute(diffArgs("HEAD~1"))

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "No active project"
            }
        }
    }

    // ── git_log ───────────────────────────────────────────────────

    "git_log" - {
        "returns formatted commit history" {
            runTest {
                val gitPort = mockk<GitPort>()
                val rawLog =
                    "\u001eabc123\u001fabc\u001fAuthor\u001f2025-01-01T00:00:00Z\u001ffeat: add feature\u001fA.kt\nB.kt"
                coEvery { gitPort.getCommitsBetween(any(), null, "HEAD", any()) } returns DomainResult.Success(rawLog)
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("HEAD"))

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "commit abc123"
                result.content shouldContain "feat: add feature"
                result.content shouldContain "A.kt"
                result.content shouldContain "B.kt"
            }
        }

        "parses range with base..head" {
            runTest {
                val gitPort = mockk<GitPort>()
                val rawLog = "\u001edef456\u001fdef\u001fAuthor\u001f2025-01-01T00:00:00Z\u001ffix: bug"
                coEvery { gitPort.getCommitsBetween(any(), "main", "HEAD", any()) } returns DomainResult.Success(rawLog)
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("main..HEAD"))

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "commit def456"
                result.content shouldContain "fix: bug"
            }
        }

        "truncates when exceeds maxLines" {
            runTest {
                val gitPort = mockk<GitPort>()
                val rawLog = (1..20).joinToString("\u001e") { i ->
                    "hash$i\u001fsh$i\u001fA\u001f2025-01-01T00:00:00Z\u001fmsg $i"
                }
                coEvery { gitPort.getCommitsBetween(any(), any(), any(), any()) } returns DomainResult.Success(rawLog)
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("HEAD", maxLines = 5))

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "truncated"
            }
        }

        "returns error when git fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getCommitsBetween(any(), any(), any(), any()) } returns
                        DomainResult.Failure(DomainError.repository("range not found"))
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("bad..range"))

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "range not found"
            }
        }

        "shows message when no commits found" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getCommitsBetween(any(), any(), any(), any()) } returns DomainResult.Success("")
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("HEAD"))

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "No commits found"
            }
        }

        "falls back to raw record when fields insufficient" {
            runTest {
                val gitPort = mockk<GitPort>()
                val rawLog = "\u001eincomplete-record"
                coEvery { gitPort.getCommitsBetween(any(), any(), any(), any()) } returns DomainResult.Success(rawLog)
                val tool = GitBuiltinTool(gitPort, mockCtx()).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("HEAD"))

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "incomplete-record"
            }
        }

        "returns error when context resolution fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Failure(DomainError.repository("db error"))
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("HEAD"))

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "db error"
            }
        }

        "returns error when no active project" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Success(null)
                val tool = GitBuiltinTool(gitPort, provider).createTools().first { it.definition.name == "git_log" }

                val result = tool.execute(logArgs("HEAD"))

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "No active project"
            }
        }
    }

    // ── git_current_commit ────────────────────────────────────────

    "git_current_commit" - {
        "returns HEAD hash on success" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getCurrentCommit(any()) } returns
                        DomainResult.Success("abc123def456")
                val tool = GitBuiltinTool(gitPort, mockCtx())
                    .createTools()
                    .first { it.definition.name == "git_current_commit" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Success>()
                result.content shouldContain "HEAD: abc123def456"
            }
        }

        "returns error when git fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                coEvery { gitPort.getCurrentCommit(any()) } returns
                        DomainResult.Failure(DomainError.repository("no commits yet"))
                val tool = GitBuiltinTool(gitPort, mockCtx())
                    .createTools()
                    .first { it.definition.name == "git_current_commit" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "no commits yet"
            }
        }

        "returns error when context resolution fails" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Failure(DomainError.repository("db error"))
                val tool = GitBuiltinTool(gitPort, provider)
                    .createTools()
                    .first { it.definition.name == "git_current_commit" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "db error"
            }
        }

        "returns error when no active project" {
            runTest {
                val gitPort = mockk<GitPort>()
                val provider = mockk<ProjectContextProvider>()
                coEvery { provider.getContext() } returns DomainResult.Success(null)
                val tool = GitBuiltinTool(gitPort, provider)
                    .createTools()
                    .first { it.definition.name == "git_current_commit" }

                val result = tool.execute(noArgs())

                result.shouldBeInstanceOf<ToolResult.Error>()
                result.message shouldContain "No active project"
            }
        }
    }

    // ── createTools returns all 5 tools ───────────────────────────

    "createTools returns all expected tools" {
        val tools = GitBuiltinTool(mockk(), mockk()).createTools()
        val names = tools.map { it.definition.name }.toSet()
        assert(names.size == 5) { "Expected 5 tools, got ${names.size}: $names" }
        assert(names.containsAll(listOf("git_branch", "git_status", "git_diff", "git_log", "git_current_commit")))
    }
})
