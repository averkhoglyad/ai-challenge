package io.averkhogliad.ai.challenge.week6.unit.cli.handlers.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.FileInfoUseCase
import io.averkhogliad.ai.challenge.week6.application.fileops.FileListUseCase
import io.averkhogliad.ai.challenge.week6.application.fileops.FileReadUseCase
import io.averkhogliad.ai.challenge.week6.cli.handlers.fileops.FileInfoCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.fileops.FileListCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.fileops.FileReadCommandHandler
import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileContent
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileMetadata
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.cli.repl.core.CommandEffect
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.createTempDirectory

class FileCommandHandlersTest : FreeSpec({

    lateinit var fileReadUseCase: FileReadUseCase
    lateinit var fileInfoUseCase: FileInfoUseCase
    lateinit var fileListUseCase: FileListUseCase
    lateinit var readHandler: FileReadCommandHandler
    lateinit var infoHandler: FileInfoCommandHandler
    lateinit var listHandler: FileListCommandHandler
    lateinit var rootPath: Path
    var relPath: RelativePath? = null

    beforeEach {
        fileReadUseCase = mockk()
        fileInfoUseCase = mockk()
        fileListUseCase = mockk()
        readHandler = FileReadCommandHandler(fileReadUseCase)
        infoHandler = FileInfoCommandHandler(fileInfoUseCase)
        listHandler = FileListCommandHandler(fileListUseCase)

        rootPath = createTempDirectory("handler-test-")
        relPath = RelativePath.from("test.txt", rootPath).getOrThrow()
    }

    afterEach {
        rootPath.toFile().deleteRecursively()
    }

    // ---- FileReadCommandHandler ----

    "FileReadCommandHandler" - {

        "canHandle /file read with path" {
            readHandler.canHandle("/file read README.md") shouldBe true
        }

        "does not handle /file info" {
            readHandler.canHandle("/file info README.md") shouldBe false
        }

        "reads file and returns numbered content" {
            runTest {
                val content = FileContent(
                    path = relPath!!,
                    content = "line1\nline2\nline3",
                    encoding = StandardCharsets.UTF_8,
                    sizeBytes = 100,
                    truncated = false,
                )
                coEvery { fileReadUseCase.execute("test.txt") } returns DomainResult.Success(content)

                val result = readHandler.execute("/file read test.txt")

                (result as CommandEffect.Print).message shouldContain "line1"
                (result as CommandEffect.Print).message shouldContain "line2"
                (result as CommandEffect.Print).message shouldContain "line3"
            }
        }

        "shows usage when path is empty" {
            runTest {
                val result = readHandler.execute("/file read")

                (result as CommandEffect.Print).isError shouldBe true
                (result as CommandEffect.Print).message shouldContain "Использование"
            }
        }

        "displays error for not found" {
            runTest {
                coEvery { fileReadUseCase.execute("missing.txt") } returns
                        DomainResult.Failure(DomainError.FileNotFound(relPath!!))

                val result = readHandler.execute("/file read missing.txt")

                (result as CommandEffect.DisplayDomainError<*>).error.message shouldContain "File not found"
            }
        }

        "shows truncation warning for large files" {
            runTest {
                val largeContent = FileContent(
                    path = relPath!!,
                    content = "truncated content",
                    encoding = StandardCharsets.UTF_8,
                    sizeBytes = 100_000,
                    truncated = true,
                )
                coEvery { fileReadUseCase.execute("large.txt") } returns DomainResult.Success(largeContent)

                val result = readHandler.execute("/file read large.txt")

                (result as CommandEffect.Print).message shouldContain "обрезан"
            }
        }
    }

    // ---- FileInfoCommandHandler ----

    "FileInfoCommandHandler" - {

        "canHandle /file info with path" {
            infoHandler.canHandle("/file info README.md") shouldBe true
        }

        "does not handle /file read" {
            infoHandler.canHandle("/file read README.md") shouldBe false
        }

        "shows file metadata with formatted size" {
            runTest {
                val meta = FileMetadata(
                    path = relPath!!,
                    sizeBytes = 1024,
                    isDirectory = false,
                    lastModified = java.time.Instant.EPOCH,
                    isBinary = false,
                )
                coEvery { fileInfoUseCase.execute("test.txt") } returns DomainResult.Success(meta)

                val result = infoHandler.execute("/file info test.txt")

                (result as CommandEffect.Print).message shouldContain "Файл:"
                (result as CommandEffect.Print).message shouldContain "1 KB"
            }
        }

        "shows usage when path is empty" {
            runTest {
                val result = infoHandler.execute("/file info")

                (result as CommandEffect.Print).isError shouldBe true
                (result as CommandEffect.Print).message shouldContain "Использование"
            }
        }
    }

    // ---- FileListCommandHandler ----

    "FileListCommandHandler" - {

        "canHandle /file list" {
            listHandler.canHandle("/file list") shouldBe true
        }

        "canHandle /file list with dir" {
            listHandler.canHandle("/file list src") shouldBe true
        }

        "canHandle /ls alias" {
            listHandler.canHandle("/ls") shouldBe true
        }

        "canHandle /ls with dir" {
            listHandler.canHandle("/ls src") shouldBe true
        }

        "lists files in directory" {
            runTest {
                val files = listOf(
                    FileMetadata(
                        path = relPath!!,
                        sizeBytes = 100,
                        isDirectory = false,
                        lastModified = Instant.now(),
                        isBinary = false,
                    ),
                    FileMetadata(
                        path = RelativePath.from("dir", rootPath).getOrThrow(),
                        sizeBytes = 0,
                        isDirectory = true,
                        lastModified = Instant.now(),
                        isBinary = false,
                    ),
                )
                coEvery { fileListUseCase.execute("src") } returns DomainResult.Success(files)

                val result = listHandler.execute("/file list src")

                (result as CommandEffect.Print).message shouldContain "Содержимое"
                (result as CommandEffect.Print).message shouldContain "[FILE]"
                (result as CommandEffect.Print).message shouldContain "[DIR]"
            }
        }

        "shows empty message when directory empty" {
            runTest {
                coEvery { fileListUseCase.execute("empty") } returns DomainResult.Success(emptyList())

                val result = listHandler.execute("/file list empty")

                (result as CommandEffect.Print).message shouldContain "пуста"
            }
        }

        "uses current dir when no argument" {
            runTest {
                coEvery { fileListUseCase.execute(".") } returns DomainResult.Success(emptyList())

                val result = listHandler.execute("/file list")

                (result as CommandEffect.Print).message shouldContain "пуста"
            }
        }

        "displays error on failure" {
            runTest {
                coEvery { fileListUseCase.execute("bad") } returns
                        DomainResult.Failure(DomainError.NoActiveProject())

                val result = listHandler.execute("/file list bad")

                (result as CommandEffect.DisplayDomainError<*>).error.message shouldContain "Активный проект не выбран"
            }
        }
    }
})
