package io.averkhogliad.ai.challenge.week6.unit.application.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.ExclusionList
import io.averkhogliad.ai.challenge.week6.application.fileops.FileOperation
import io.averkhogliad.ai.challenge.week6.application.fileops.SandboxPolicy
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class SandboxPolicyTest : FreeSpec({

    lateinit var rootPath: Path
    lateinit var policy: SandboxPolicy

    beforeTest {
        rootPath = createTempDirectory("sandbox-test-")
        policy = SandboxPolicy(ExclusionList())
    }

    afterTest {
        rootPath.toFile().deleteRecursively()
    }

    "check" - {

        "allows read from src/" {
            val path = RelativePath.from("src/main.kt", rootPath).getOrThrow()
            val result = policy.check(path, FileOperation.Read)
            result.isSuccess shouldBe true
        }

        "denies read from .git/" {
            val path = RelativePath.from(".git/config", rootPath).getOrThrow()
            val result = policy.check(path, FileOperation.Read)
            result.isFailure shouldBe true
        }

        "denies write to node_modules/" {
            val path = RelativePath.from("node_modules/pkg/index.js", rootPath).getOrThrow()
            val result = policy.check(path, FileOperation.Write)
            result.isFailure shouldBe true
        }

        "denies search in build/" {
            val path = RelativePath.from("build/output/app.jar", rootPath).getOrThrow()
            val result = policy.check(path, FileOperation.Search)
            result.isFailure shouldBe true
        }

        "denies access to .env files" {
            val path = RelativePath.from(".env", rootPath).getOrThrow()
            val result = policy.check(path, FileOperation.Read)
            result.isFailure shouldBe true
        }

        "denies access to .pem files" {
            val path = RelativePath.from("certs/private.pem", rootPath).getOrThrow()
            val result = policy.check(path, FileOperation.Read)
            result.isFailure shouldBe true
        }

        "allows write to non-excluded dir" {
            val path = RelativePath.from("docs/adr/001.md", rootPath).getOrThrow()
            val result = policy.check(path, FileOperation.Write)
            result.isSuccess shouldBe true
        }
    }

    "custom exclusions" - {

        "respects custom exclusions from config" {
            val customPolicy = SandboxPolicy(
                ExclusionList(custom = listOf("secrets", "*.secret"))
            )
            val path = RelativePath.from("secrets/tokens.txt", rootPath).getOrThrow()
            val result = customPolicy.check(path, FileOperation.Read)
            result.isFailure shouldBe true
        }
    }
})
