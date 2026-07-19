package io.averkhogliad.ai.challenge.week6.unit.infrastructure.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.ExclusionList
import io.averkhogliad.ai.challenge.week6.application.fileops.SandboxPolicy
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileFilter
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.SearchQuery
import io.averkhogliad.ai.challenge.week6.infrastructure.fileops.LocalFileOpsAdapter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText

class LocalFileOpsAdapterTest : FreeSpec({

    lateinit var rootPath: Path
    lateinit var adapter: LocalFileOpsAdapter

    beforeTest {
        rootPath = createTempDirectory("fileops-it-")
        adapter = LocalFileOpsAdapter(rootPath, SandboxPolicy(ExclusionList()))
    }

    afterTest {
        rootPath.toFile().deleteRecursively()
    }

    "read" - {
        "reads existing file content" {
            runTest {
                Files.createDirectories(rootPath.resolve("src"))
                rootPath.resolve("src/test.txt").writeText("hello world\nthis is a test\n")

                val relPath = RelativePath.from("src/test.txt", rootPath).getOrThrow()
                val result = adapter.read(relPath)

                result.isSuccess shouldBe true
                result.getOrThrow().content shouldBe "hello world\nthis is a test\n"
                result.getOrThrow().truncated shouldBe false
            }
        }

        "returns NotFound for missing file" {
            runTest {
                val relPath = RelativePath.from("nonexistent.txt", rootPath).getOrThrow()
                val result = adapter.read(relPath)
                result.isFailure shouldBe true
            }
        }

        "detects binary files" {
            runTest {
                val pngMagic = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
                rootPath.resolve("image.png").toFile().writeBytes(pngMagic)

                val relPath = RelativePath.from("image.png", rootPath).getOrThrow()
                val result = adapter.read(relPath)
                result.isFailure shouldBe true
            }
        }

        "truncates files over 64KB" {
            runTest {
                val big = "a".repeat(70_000)
                rootPath.resolve("big.txt").writeText(big)

                val relPath = RelativePath.from("big.txt", rootPath).getOrThrow()
                val result = adapter.read(relPath)

                result.isSuccess shouldBe true
                result.getOrThrow().truncated shouldBe true
                result.getOrThrow().sizeBytes shouldBe 70_000L
            }
        }
    }

    "write" - {
        "writes new file" {
            runTest {
                val relPath = RelativePath.from("output.txt", rootPath).getOrThrow()
                val result = adapter.write(relPath, "new content")

                result.isSuccess shouldBe true
                rootPath.resolve("output.txt").toFile().exists() shouldBe true
                rootPath.resolve("output.txt").toFile().readText() shouldBe "new content"
            }
        }

        "overwrites existing file" {
            runTest {
                rootPath.resolve("existing.txt").writeText("old")
                val relPath = RelativePath.from("existing.txt", rootPath).getOrThrow()
                val result = adapter.write(relPath, "updated")
                result.isSuccess shouldBe true
                rootPath.resolve("existing.txt").toFile().readText() shouldBe "updated"
            }
        }

        "creates parent directories" {
            runTest {
                val relPath = RelativePath.from("deep/nested/file.txt", rootPath).getOrThrow()
                val result = adapter.write(relPath, "deep content")
                result.isSuccess shouldBe true
                rootPath.resolve("deep/nested/file.txt").toFile().exists() shouldBe true
            }
        }

        "denies write to excluded directory" {
            runTest {
                val customExclusions = ExclusionList(
                    patterns = ExclusionList.DEFAULT_EXCLUSIONS,
                    custom = listOf("secrets"),
                )
                val strict = LocalFileOpsAdapter(rootPath, SandboxPolicy(customExclusions))
                Files.createDirectories(rootPath.resolve("secrets"))
                val relPath = RelativePath.from("secrets/tokens.txt", rootPath).getOrThrow()
                val result = strict.write(relPath, "secret")
                result.isFailure shouldBe true
            }
        }
    }

    "search" - {
        "finds substring in multiple files" {
            runTest {
                rootPath.resolve("a.txt").writeText("hello world\nfoo bar\n")
                rootPath.resolve("b.txt").writeText("lorem\nhello there\n")

                val query = SearchQuery(query = "hello")
                val result = adapter.search(query)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldHaveSize 2
            }
        }

        "filters by extension" {
            runTest {
                rootPath.resolve("code.kt").writeText("fun hello() = 42")
                rootPath.resolve("readme.md").writeText("# hello world")

                val query = SearchQuery(query = "hello", extension = ".kt")
                val result = adapter.search(query)

                result.isSuccess shouldBe true
                val hits = result.getOrThrow()
                hits shouldHaveSize 1
                hits[0].path.value shouldBe "code.kt"
            }
        }

        "skips excluded directories" {
            runTest {
                rootPath.resolve(".git").toFile().mkdirs()
                rootPath.resolve(".git/config").writeText("hello git")
                rootPath.resolve("README.md").writeText("hello readme")

                val query = SearchQuery(query = "hello")
                val result = adapter.search(query)

                result.isSuccess shouldBe true
                val hits = result.getOrThrow()
                hits shouldHaveSize 1
                hits[0].path.value shouldBe "README.md"
            }
        }

        "returns empty for no match" {
            runTest {
                rootPath.resolve("only.txt").writeText("nothing here\n")
                val query = SearchQuery(query = "zzzzzz")
                val result = adapter.search(query)

                result.isSuccess shouldBe true
                result.getOrThrow().isEmpty() shouldBe true
            }
        }
    }

    "list" - {
        "lists files in root" {
            runTest {
                rootPath.resolve("a.txt").writeText("a")
                rootPath.resolve("b.txt").writeText("b")
                Files.createDirectories(rootPath.resolve("sub"))

                val relPath = RelativePath.from("a.txt", rootPath).getOrThrow()
                val result = adapter.list(relPath, FileFilter())

                result.isSuccess shouldBe true
            }
        }

        "returns empty for empty directory" {
            runTest {
                Files.createDirectories(rootPath.resolve("empty"))

                val relPath = RelativePath.from("empty", rootPath).getOrThrow()
                val result = adapter.list(relPath, FileFilter())

                result.isSuccess shouldBe true
            }
        }
    }

    "info" - {
        "returns metadata for file" {
            runTest {
                rootPath.resolve("info.txt").writeText("test content")

                val relPath = RelativePath.from("info.txt", rootPath).getOrThrow()
                val result = adapter.info(relPath)

                result.isSuccess shouldBe true
                result.getOrThrow().isDirectory shouldBe false
                result.getOrThrow().isBinary shouldBe false
            }
        }

        "returns error for missing file" {
            runTest {
                val relPath = RelativePath.from("ghost.txt", rootPath).getOrThrow()
                val result = adapter.info(relPath)
                result.isFailure shouldBe true
            }
        }
    }

    "exists" - {
        "returns true for existing file" {
            runTest {
                rootPath.resolve("real.txt").writeText("real")
                val relPath = RelativePath.from("real.txt", rootPath).getOrThrow()
                val result = adapter.exists(relPath)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldBe true
            }
        }

        "returns false for missing file" {
            runTest {
                val relPath = RelativePath.from("ghost.txt", rootPath).getOrThrow()
                val result = adapter.exists(relPath)

                result.isSuccess shouldBe true
                result.getOrThrow() shouldBe false
            }
        }
    }
})


