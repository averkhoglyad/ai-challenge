package io.averkhogliad.ai.challenge.week6.unit.infrastructure.git

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.infrastructure.git.ProcessGitAdapter
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.io.path.Path

class ProcessGitAdapterTest : FreeSpec({

    lateinit var adapter: ProcessGitAdapter

    beforeEach {
        adapter = ProcessGitAdapter()
    }

    "getCurrentBranch" - {

        "returns error for non-git directory" {
            runTest {
                // given
                val tempDir = Files.createTempDirectory("week6-test-nongit-")

                // when
                val result = adapter.getCurrentBranch(tempDir)

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.message shouldBe "Ошибка хранилища: Not a git repository: $tempDir"

                // cleanup
                Files.deleteIfExists(tempDir)
            }
        }

        "returns error for non-existent directory" {
            runTest {
                // given
                val nonExistentDir = Path("/tmp/week6-nonexistent-${System.nanoTime()}")

                // when
                val result = adapter.getCurrentBranch(nonExistentDir)

                // then
                result.isFailure shouldBe true
                val failure = result as DomainResult.Failure
                failure.error.message shouldBe "Ошибка хранилища: Not a git repository: $nonExistentDir"
            }
        }
    }

    "checkGitStatus" - {

        "returns false for non-git directory" {
            runTest {
                // given
                val tempDir = Files.createTempDirectory("week6-test-nongit-status-")

                // when
                val result = adapter.checkGitStatus(tempDir)

                // then
                (result as DomainResult.Success).value shouldBe false

                // cleanup
                Files.deleteIfExists(tempDir)
            }
        }
    }
})
