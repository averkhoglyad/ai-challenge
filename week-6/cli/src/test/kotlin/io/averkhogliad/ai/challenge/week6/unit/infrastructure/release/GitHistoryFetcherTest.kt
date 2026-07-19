package io.averkhogliad.ai.challenge.week6.unit.infrastructure.release

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory
import io.averkhogliad.ai.challenge.week6.infrastructure.git.ProcessGitAdapter
import io.averkhogliad.ai.challenge.week6.infrastructure.release.GitHistoryFetcher
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path

class GitHistoryFetcherTest : FreeSpec({

    "fetch" - {
        "maps git log records and filters merge commits by default" {
            // given
            val gitPort = FakeGitPort(
                DomainResult.Success(
                    "abcdef123456\u001fabcdef1\u001fAda\u001f2026-01-20T10:15:30Z\u001ffeat: add releases #42\u001fsrc/main/kotlin/Release.kt\nREADME.md\u001e" +
                            "123456abcdef\u001f123456a\u001fBob\u001f2026-01-21T10:15:30Z\u001fMerge branch 'main'\u001f\u001e"
                )
            )
            val fetcher = GitHistoryFetcher(gitPort)

            // when
            val result = fetcher.fetch(Path.of("."), "v1.0.0..HEAD")

            // then
            val commits = (result as DomainResult.Success).value
            commits.size shouldBe 1
            commits.single().category shouldBe CommitCategory.FEATURE
            commits.single().ticketId shouldBe "#42"
            commits.single().changedFiles shouldBe listOf("src/main/kotlin/Release.kt", "README.md")
            gitPort.base shouldBe "v1.0.0"
            gitPort.head shouldBe "HEAD"
            gitPort.limit shouldBe 500
        }

        "parses multiple separator-prefixed git records with files and breaking footer" {
            // given
            val gitPort = FakeGitPort(
                DomainResult.Success(
                    "\u001eabcdef123456\u001fabcdef1\u001fAda\u001f2026-01-20T10:15:30Z\u001ffix: change API\n\nBREAKING CHANGE: callers must migrate\u001fsrc/Api.kt\n" +
                            "\u001e123456abcdef\u001f123456a\u001fBob\u001f2026-01-21T10:15:30Z\u001ffeat: add output\u001fREADME.md\n"
                )
            )
            val fetcher = GitHistoryFetcher(gitPort)

            // when
            val result = fetcher.fetch(Path.of("."), "HEAD")

            // then
            val commits = (result as DomainResult.Success).value
            commits.size shouldBe 2
            commits[0].category shouldBe CommitCategory.BREAKING
            commits[0].changedFiles shouldBe listOf("src/Api.kt")
            commits[1].category shouldBe CommitCategory.FEATURE
            commits[1].changedFiles shouldBe listOf("README.md")
        }

        "parses real git log records with breaking footer and changed files" {
            runTest {
                // given
                val root = Files.createTempDirectory("git-history-")
                try {
                    runGit(root, "init")
                    runGit(root, "config", "user.name", "Test User")
                    runGit(root, "config", "user.email", "test@example.com")
                    Files.writeString(root.resolve("Api.kt"), "class Api")
                    Files.writeString(root.resolve("README.md"), "# API")
                    runGit(root, "add", "Api.kt", "README.md")
                    runGit(root, "commit", "-m", "fix: change API", "-m", "BREAKING CHANGE: callers must migrate")
                    val fetcher = GitHistoryFetcher(ProcessGitAdapter())

                    // when
                    val result = fetcher.fetch(root, "HEAD")

                    // then
                    val commit = (result as DomainResult.Success).value.single()
                    commit.category shouldBe CommitCategory.BREAKING
                    commit.message shouldBe "fix: change API\n\nBREAKING CHANGE: callers must migrate"
                    commit.changedFiles shouldBe listOf("Api.kt", "README.md")
                } finally {
                    root.toFile().deleteRecursively()
                }
            }
        }

        "returns no commits error for empty history" {
            // given
            val fetcher = GitHistoryFetcher(FakeGitPort(DomainResult.Success("")))

            // when
            val result = fetcher.fetch(Path.of("."), "HEAD~5..HEAD")

            // then
            (result as DomainResult.Failure).error.message shouldBe "В git range нет коммитов: HEAD~5..HEAD"
        }

        "returns invalid range error before querying git" {
            // given
            val gitPort = FakeGitPort(DomainResult.Success(""))
            val fetcher = GitHistoryFetcher(gitPort)

            // when
            val result = fetcher.fetch(Path.of("."), "v1.0.0..")

            // then
            (result as DomainResult.Failure).error.message shouldBe "Git range не найден: v1.0.0.."
            gitPort.wasCalled shouldBe false
        }
    }
}) {
    private class FakeGitPort(
        private val response: DomainResult<String>,
    ) : GitPort {
        var base: String? = null
        var head: String? = null
        var limit: Int? = null
        var wasCalled = false

        override suspend fun getCommitsBetween(
            rootPath: Path,
            base: String?,
            head: String,
            limit: Int
        ): DomainResult<String> {
            wasCalled = true
            this.base = base
            this.head = head
            this.limit = limit
            return response
        }

        override suspend fun getCurrentBranch(rootPath: Path): DomainResult<String> = unsupported()
        override suspend fun getCurrentCommit(rootPath: Path): DomainResult<String> = unsupported()
        override suspend fun checkGitStatus(rootPath: Path): DomainResult<Boolean> = unsupported()
        override suspend fun getDiffBetweenBranches(
            rootPath: Path,
            sourceBranch: String,
            targetBranch: String
        ): DomainResult<String> = unsupported()

        override suspend fun getDiffBetweenCommits(rootPath: Path, base: String, head: String): DomainResult<String> =
            unsupported()

        override suspend fun getLastCommitHash(rootPath: Path): DomainResult<String> = unsupported()
        override suspend fun isMergeCommit(rootPath: Path): DomainResult<Boolean> = unsupported()
        override suspend fun branchExists(rootPath: Path, branch: String): DomainResult<Boolean> = unsupported()

        @Suppress("UNCHECKED_CAST")
        private fun <T> unsupported(): DomainResult<T> = DomainResult.Failure(
            io.averkhogliad.ai.challenge.week6.domain.error.DomainError.repository("unsupported")
        )
    }
}

private fun runGit(root: Path, vararg arguments: String) {
    val process = ProcessBuilder(listOf("git") + arguments)
        .directory(root.toFile())
        .redirectErrorStream(true)
        .start()
    check(process.waitFor() == 0) { process.inputStream.bufferedReader().readText() }
}
