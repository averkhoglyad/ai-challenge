package io.averkhogliad.ai.challenge.week6.unit.infrastructure.fileops

import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.*
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort
import io.averkhogliad.ai.challenge.week6.domain.model.ProjectContext
import io.averkhogliad.ai.challenge.week6.domain.tools.Tool
import io.averkhogliad.ai.challenge.week6.domain.tools.ToolResult
import io.averkhogliad.ai.challenge.week6.infrastructure.fileops.FileOpsBuiltinTool
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class FileOpsBuiltinToolTest : FreeSpec({

    lateinit var fileOpsPort: FileOpsPort
    lateinit var projectContextProvider: ProjectContextProvider
    lateinit var rootPath: Path
    lateinit var ctx: ProjectContext
    lateinit var tools: Map<String, Tool>

    beforeTest {
        rootPath = createTempDirectory("fops-bt-test-")
        ctx = ProjectContext(
            projectId = "test-proj",
            rootPath = rootPath,
            docsPaths = emptyList(),
            isGitEnabled = true,
        )
        fileOpsPort = mockk()
        projectContextProvider = mockk()
        coEvery { projectContextProvider.getContext() } returns DomainResult.Success(ctx)
        val factory = FileOpsBuiltinTool(fileOpsPort, projectContextProvider)
        tools = factory.createTools().associateBy { it.definition.name }
    }

    afterTest {
        rootPath.toFile().deleteRecursively()
    }

    fun args(vararg ps: Pair<String, String>) = buildJsonObject {
        ps.forEach { (k, v) -> put(k, JsonPrimitive(v)) }
    }

    "read_file" - {
        "returns file content for valid path" {
            runTest {
                val tool = tools["read_file"]!!
                val relPath = RelativePath.from("src/main.kt", rootPath).getOrThrow()
                val fc = FileContent(relPath, "fun main() {}\n", sizeBytes = 14)
                coEvery { fileOpsPort.read(relPath) } returns DomainResult.Success(fc)
                val result = tool.execute(args("path" to "src/main.kt"))
                result shouldBe ToolResult.Success("fun main() {}\n")
            }
        }
        "returns error for not found" {
            runTest {
                val tool = tools["read_file"]!!
                val relPath = RelativePath.from("missing.txt", rootPath).getOrThrow()
                coEvery { fileOpsPort.read(relPath) } returns DomainResult.Failure(
                    DomainError.FileNotFound(relPath)
                )
                val result = tool.execute(args("path" to "missing.txt"))
                require(result is ToolResult.Error)
                result.message shouldContain "File not found"
            }
        }
        "returns error for path traversal" {
            runTest {
                val tool = tools["read_file"]!!
                val result = tool.execute(args("path" to "../etc/passwd"))
                require(result is ToolResult.Error)
                result.message shouldContain "Invalid path"
            }
        }
        "returns error when missing path parameter" {
            runTest {
                val tool = tools["read_file"]!!
                val result = tool.execute(buildJsonObject { })
                result shouldBe ToolResult.Error("Missing required parameter: path")
            }
        }
        "returns truncated warning for large files" {
            runTest {
                val tool = tools["read_file"]!!
                val relPath = RelativePath.from("big.txt", rootPath).getOrThrow()
                val fc = FileContent(relPath, "data...", sizeBytes = 128 * 1024, truncated = true)
                coEvery { fileOpsPort.read(relPath) } returns DomainResult.Success(fc)
                val result = tool.execute(args("path" to "big.txt"))
                require(result is ToolResult.Success)
                result.content shouldContain "truncated"
            }
        }
    }

    "write_file" - {
        "returns pending confirm message" {
            runTest {
                val tool = tools["write_file"]!!
                val result = tool.execute(args("path" to "out.txt", "content" to "hello"))
                require(result is ToolResult.PendingConfirm)
                result.message shouldContain "requires confirmation"
            }
        }
        "returns error for traversal path" {
            runTest {
                val tool = tools["write_file"]!!
                val result = tool.execute(args("path" to "../etc/passwd", "content" to "bad"))
                require(result is ToolResult.Error)
                result.message shouldContain "Invalid path"
            }
        }
        "returns error when missing content" {
            runTest {
                val tool = tools["write_file"]!!
                val result = tool.execute(args("path" to "f.txt"))
                result shouldBe ToolResult.Error("Missing required parameter: content")
            }
        }
    }

    "search_code" - {
        "returns search hits" {
            runTest {
                val tool = tools["search_code"]!!
                val relPath = RelativePath.from("src/App.kt", rootPath).getOrThrow()
                val hit = SearchHit(relPath, 5, "fun main()", emptyList(), emptyList())
                coEvery { fileOpsPort.search(any<SearchQuery>()) } returns DomainResult.Success(listOf(hit))
                val result = tool.execute(args("query" to "main"))
                require(result is ToolResult.Success)
                result.content shouldContain "src/App.kt"
            }
        }
        "returns empty result when no hits" {
            runTest {
                val tool = tools["search_code"]!!
                coEvery { fileOpsPort.search(any()) } returns DomainResult.Success(emptyList())
                val result = tool.execute(args("query" to "none"))
                require(result is ToolResult.Success)
                result.content shouldContain "No results found"
            }
        }
        "returns error missing query" {
            runTest {
                val tool = tools["search_code"]!!
                val result = tool.execute(buildJsonObject { })
                result shouldBe ToolResult.Error("Missing required parameter: query")
            }
        }
    }

    "list_files" - {
        "returns file list" {
            runTest {
                val tool = tools["list_files"]!!
                val relDir = RelativePath.from("src", rootPath).getOrThrow()
                val now = java.time.Instant.now()
                val files = listOf(
                    FileMetadata(
                        RelativePath.from("src/Main.kt", rootPath).getOrThrow(),
                        1024, false, now, false
                    ),
                )
                coEvery { fileOpsPort.list(relDir, any()) } returns DomainResult.Success(files)
                val result = tool.execute(args("dir" to "src"))
                require(result is ToolResult.Success)
                result.content shouldContain "src/Main.kt"
            }
        }
        "shows empty message for empty dir" {
            runTest {
                val tool = tools["list_files"]!!
                coEvery { fileOpsPort.list(any(), any()) } returns DomainResult.Success(emptyList())
                val result = tool.execute(args("dir" to "emptydir"))
                require(result is ToolResult.Success)
                result.content shouldContain "emptydir"
            }
        }
    }

    "file_info" - {
        "returns metadata" {
            runTest {
                val tool = tools["file_info"]!!
                val relPath = RelativePath.from("build.gradle.kts", rootPath).getOrThrow()
                val meta = FileMetadata(relPath, 2048, false, java.time.Instant.now(), false)
                coEvery { fileOpsPort.info(relPath) } returns DomainResult.Success(meta)
                val result = tool.execute(args("path" to "build.gradle.kts"))
                require(result is ToolResult.Success)
                result.content shouldContain "build.gradle.kts"
                result.content shouldContain "2 KB"
            }
        }
        "returns error missing path" {
            runTest {
                val tool = tools["file_info"]!!
                val result = tool.execute(buildJsonObject { })
                result shouldBe ToolResult.Error("Missing required parameter: path")
            }
        }
    }
})
