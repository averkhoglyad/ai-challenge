package io.averkhogliad.ai.challenge.week6.unit.domain.fileops.model

import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class RelativePathTest : FreeSpec({

    lateinit var rootPath: Path

    beforeTest {
        rootPath = createTempDirectory("relative-path-test-")
    }

    afterTest {
        rootPath.toFile().deleteRecursively()
    }

    "from" - {

        "accepts valid relative path" {
            val result = RelativePath.from("src/main/kotlin/App.kt", rootPath)
            result.isSuccess shouldBe true
            result.getOrThrow().value shouldBe "src/main/kotlin/App.kt"
        }

        "accepts nested directories" {
            val result = RelativePath.from("a/b/c/d.txt", rootPath)
            result.isSuccess shouldBe true
            result.getOrThrow().value shouldBe "a/b/c/d.txt"
        }

        "accepts single file" {
            val result = RelativePath.from("README.md", rootPath)
            result.isSuccess shouldBe true
            result.getOrThrow().value shouldBe "README.md"
        }

        "rejects path traversal with .." {
            val result = RelativePath.from("../etc/passwd", rootPath)
            result.isFailure shouldBe true
        }

        "rejects path traversal with .. in middle" {
            val result = RelativePath.from("src/../../etc/passwd", rootPath)
            result.isFailure shouldBe true
        }

        "rejects absolute paths" {
            val result = RelativePath.from("/etc/passwd", rootPath)
            result.isFailure shouldBe true
        }

        "rejects backslash separator" {
            val result = RelativePath.from("src\\main\\App.kt", rootPath)
            result.isFailure shouldBe true
        }

        "rejects empty string" {
            val result = RelativePath.from("", rootPath)
            result.isFailure shouldBe true
        }

        "rejects dot segment" {
            val result = RelativePath.from("./config.yml", rootPath)
            result.isFailure shouldBe true
        }
    }

    "toAbsolutePath" - {

        "resolves correctly against rootPath" {
            val relPath = RelativePath.from("docs/readme.md", rootPath).getOrThrow()
            val absPath = relPath.toAbsolutePath(rootPath)
            absPath shouldBe rootPath.resolve("docs/readme.md").normalize()
        }
    }

    "toString" - {

        "returns relative path string" {
            val relPath = RelativePath.from("src/main.kt", rootPath).getOrThrow()
            relPath.toString() shouldBe "src/main.kt"
        }
    }
})
