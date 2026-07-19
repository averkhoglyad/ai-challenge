package io.averkhogliad.ai.challenge.week6.unit.application.release

import io.averkhogliad.ai.challenge.llm.chat.ChatMessage
import io.averkhogliad.ai.challenge.llm.chat.ChatParameters
import io.averkhogliad.ai.challenge.llm.chat.ChatResponse
import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.application.release.*
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.*
import io.averkhogliad.ai.challenge.week6.domain.fileops.port.FileOpsPort
import io.averkhogliad.ai.challenge.week6.domain.release.model.Changelog
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitCategory
import io.averkhogliad.ai.challenge.week6.domain.release.model.CommitInfo
import io.averkhogliad.ai.challenge.week6.domain.release.model.Release
import io.averkhogliad.ai.challenge.week6.domain.release.port.ReleaseRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.release.ConventionalCommitParser
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate

class ReleaseUseCasesTest : FreeSpec({
    class FakeLlmClient(private val response: String?, private val failure: Boolean = false) : LlmClient {
        override suspend fun chat(
            prompt: String,
            systemPrompt: String?,
            parameters: ChatParameters,
            model: String?,
            tools: List<JsonObject>?
        ) =
            if (failure) throw IllegalStateException("offline") else ChatResponse(response, "stop", null)

        override suspend fun chatWithMessages(
            messages: List<ChatMessage>,
            parameters: ChatParameters,
            model: String?,
            tools: List<JsonObject>?
        ) = ChatResponse(null, "stop", null)

        override fun close() = Unit
    }

    class FakeRepository(private val latest: Release? = null, private val release: Release? = null) :
        ReleaseRepository {
        override suspend fun save(release: Release) = DomainResult.Success(Unit)
        override suspend fun findById(id: String) = DomainResult.Success(release)
        override suspend fun findByProjectIdAndVersion(projectId: String, version: String) =
            DomainResult.Success(release)

        override suspend fun findByProjectId(projectId: String, limit: Int): DomainResult<List<Release>> =
            DomainResult.Success(emptyList())

        override suspend fun findLatestByProjectId(projectId: String) = DomainResult.Success(latest)
        override suspend fun delete(id: String) = DomainResult.Success(Unit)
    }

    fun commit(category: CommitCategory) =
        CommitInfo("abcdef", "abcdef", "message", "author", Instant.EPOCH, emptyList(), category, null)

    fun release(version: String) = Release(
        "id",
        "project",
        version,
        null,
        "HEAD",
        emptyList(),
        Changelog(version, LocalDate.now(), emptyList(), ""),
        Instant.EPOCH
    )

    "ClassifyCommitUseCase.execute" - {
        "returns category from deterministic LLM response" {
            runTest {
                ClassifyCommitUseCase(FakeLlmClient("FEATURE")).execute("custom feature")
                    .getOrNull() shouldBe CommitCategory.FEATURE
            }
        }
        "falls back to UNKNOWN when LLM fails" {
            runTest {
                ClassifyCommitUseCase(FakeLlmClient(null, failure = true)).execute("custom")
                    .getOrNull() shouldBe CommitCategory.UNKNOWN
            }
        }
    }
    "HybridCommitClassifier.classify" - {
        "uses conventional parser before LLM" {
            runTest {
                val classifier = HybridCommitClassifier(
                    ParseConventionalCommitUseCase(ConventionalCommitParser()),
                    ClassifyCommitUseCase(FakeLlmClient("FIX"))
                )
                classifier.classify("feat: add release automation").getOrNull() shouldBe CommitCategory.FEATURE
            }
        }
    }
    "SuggestVersionUseCase.execute" - {
        "suggests major after breaking change" {
            runTest {
                val result = SuggestVersionUseCase(FakeRepository(release("v1.2.3"))).execute(
                    "project",
                    listOf(commit(CommitCategory.BREAKING))
                ).getOrNull()!!
                result.suggestedVersion shouldBe "v2.0.0"
            }
        }
        "suggests initial minor version without previous release" {
            runTest {
                val result =
                    SuggestVersionUseCase(FakeRepository()).execute("project", listOf(commit(CommitCategory.FEATURE)))
                        .getOrNull()!!
                result.suggestedVersion shouldBe "v0.1.0"
            }
        }
    }
    "ShowReleaseUseCase.execute" - {
        "returns ReleaseNotFound when repository has no release" {
            runTest { ShowReleaseUseCase(FakeRepository()).execute("project", "missing").isFailure shouldBe true }
        }
    }
    "ConfirmReleaseUseCase.execute" - {
        "prepends release notes to existing changelog" {
            runTest {
                // given
                var writtenContent: String? = null
                val root = java.nio.file.Files.createTempDirectory("release-confirm-")
                val path = (RelativePath.from("CHANGELOG.md", root) as DomainResult.Success).value
                val fileOps = object : FileOpsPort {
                    override suspend fun read(path: RelativePath): DomainResult<FileContent> = DomainResult.Success(
                        FileContent(path, "# v1.0.0", sizeBytes = 8)
                    )

                    override suspend fun write(path: RelativePath, content: String): DomainResult<Unit> {
                        writtenContent = content
                        return DomainResult.Success(Unit)
                    }

                    override suspend fun search(query: SearchQuery): DomainResult<List<SearchHit>> = unsupported()
                    override suspend fun list(dir: RelativePath, filter: FileFilter): DomainResult<List<FileMetadata>> =
                        unsupported()

                    override suspend fun info(path: RelativePath): DomainResult<FileMetadata> = unsupported()
                    override suspend fun exists(path: RelativePath): DomainResult<Boolean> = unsupported()
                    override suspend fun delete(path: RelativePath): DomainResult<Unit> = unsupported()
                    private fun <T> unsupported(): DomainResult<T> = DomainResult.Failure(
                        io.averkhogliad.ai.challenge.week6.domain.error.DomainError.repository("unsupported")
                    )
                }

                // when
                val result = ConfirmReleaseUseCase(fileOps, FakeRepository(), path).execute(
                    ReleaseDraft(
                        release("v1.1.0"),
                        "# v1.1.0"
                    )
                )

                // then
                result.isSuccess shouldBe true
                writtenContent shouldBe "# v1.1.0\n\n# v1.0.0"
            }
        }

        "does not overwrite truncated changelog" {
            runTest {
                // given
                var written = false
                val root = java.nio.file.Files.createTempDirectory("release-confirm-")
                val path = (RelativePath.from("CHANGELOG.md", root) as DomainResult.Success).value
                val fileOps = object : FileOpsPort {
                    override suspend fun read(path: RelativePath): DomainResult<FileContent> = DomainResult.Success(
                        FileContent(path, "# v1.0.0", sizeBytes = 64L * 1024 + 1, truncated = true)
                    )

                    override suspend fun write(path: RelativePath, content: String): DomainResult<Unit> {
                        written = true
                        return DomainResult.Success(Unit)
                    }

                    override suspend fun search(query: SearchQuery): DomainResult<List<SearchHit>> = unsupported()
                    override suspend fun list(dir: RelativePath, filter: FileFilter): DomainResult<List<FileMetadata>> =
                        unsupported()

                    override suspend fun info(path: RelativePath): DomainResult<FileMetadata> = unsupported()
                    override suspend fun exists(path: RelativePath): DomainResult<Boolean> = unsupported()
                    override suspend fun delete(path: RelativePath): DomainResult<Unit> = unsupported()
                    private fun <T> unsupported(): DomainResult<T> = DomainResult.Failure(
                        io.averkhogliad.ai.challenge.week6.domain.error.DomainError.repository("unsupported")
                    )
                }

                // when
                val result = ConfirmReleaseUseCase(fileOps, FakeRepository(), path).execute(
                    ReleaseDraft(
                        release("v1.1.0"),
                        "# v1.1.0"
                    )
                )

                // then
                written shouldBe false
                (result as DomainResult.Failure).error.message shouldBe
                        "Ошибка хранилища: CHANGELOG.md is too large to preserve existing history"
            }
        }

        "reports partial persistence when changelog write succeeds but release save fails" {
            runTest {
                // given
                var written = false
                val fileOps = object : FileOpsPort {
                    override suspend fun write(path: RelativePath, content: String): DomainResult<Unit> {
                        written = true
                        return DomainResult.Success(Unit)
                    }

                    override suspend fun read(path: RelativePath): DomainResult<FileContent> = DomainResult.Failure(
                        io.averkhogliad.ai.challenge.week6.domain.error.DomainError.FileNotFound(path)
                    )

                    override suspend fun search(query: SearchQuery): DomainResult<List<SearchHit>> = unsupported()
                    override suspend fun list(dir: RelativePath, filter: FileFilter): DomainResult<List<FileMetadata>> =
                        unsupported()

                    override suspend fun info(path: RelativePath): DomainResult<FileMetadata> = unsupported()
                    override suspend fun exists(path: RelativePath): DomainResult<Boolean> = unsupported()
                    override suspend fun delete(path: RelativePath): DomainResult<Unit> = unsupported()
                    private fun <T> unsupported(): DomainResult<T> = DomainResult.Failure(
                        io.averkhogliad.ai.challenge.week6.domain.error.DomainError.repository("unsupported")
                    )
                }
                val failingRepository = object : ReleaseRepository by FakeRepository() {
                    override suspend fun save(release: Release): DomainResult<Unit> = DomainResult.Failure(
                        io.averkhogliad.ai.challenge.week6.domain.error.DomainError.repository("database unavailable")
                    )
                }
                val root = java.nio.file.Files.createTempDirectory("release-confirm-")
                val path = (RelativePath.from("CHANGELOG.md", root) as DomainResult.Success).value
                val draft = ReleaseDraft(release("v1.0.0"), "# v1.0.0")

                // when
                val result = ConfirmReleaseUseCase(fileOps, failingRepository, path).execute(draft)

                // then
                written shouldBe true
                (result as DomainResult.Failure).error.message shouldBe
                        "CHANGELOG.md обновлён, но релиз не сохранён в БД: Ошибка хранилища: database unavailable"
            }
        }
    }
})
