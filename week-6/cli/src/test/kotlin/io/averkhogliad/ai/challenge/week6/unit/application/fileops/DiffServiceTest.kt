package io.averkhogliad.ai.challenge.week6.unit.application.fileops

import io.averkhogliad.ai.challenge.week6.application.fileops.DiffService
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.DiffLine
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.FileChange
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class DiffServiceTest : FreeSpec({

    lateinit var diffService: DiffService
    lateinit var rootPath: Path

    beforeTest {
        diffService = DiffService()
        rootPath = createTempDirectory("diff-test-")
    }

    afterTest {
        rootPath.toFile().deleteRecursively()
    }

    "buildDiffs" - {

        "handles empty changes list" {
            val diffs = diffService.buildDiffs(emptyList())
            diffs shouldHaveSize 0
        }

        "handles only additions — new file with no old content" {
            val path = RelativePath.from("newfile.txt", rootPath).getOrThrow()
            val change = FileChange(path, oldContent = null, newContent = "line1\nline2\nline3")
            val diffs = diffService.buildDiffs(listOf(change))

            diffs shouldHaveSize 1
            val diff = diffs[0]
            diff.path shouldBe path
            diff.oldContent shouldBe null
            diff.newContent shouldBe "line1\nline2\nline3"

            val allLines = diff.hunks.flatMap { it.lines }
            allLines.isNotEmpty() shouldBe true
            allLines.forEach { line ->
                line.shouldBeInstanceOf<DiffLine.Added>()
            }
        }

        "handles only deletions — all old lines removed" {
            val path = RelativePath.from("oldfile.txt", rootPath).getOrThrow()
            val change = FileChange(path, oldContent = "line1\nline2\nline3", newContent = "")
            val diffs = diffService.buildDiffs(listOf(change))

            diffs shouldHaveSize 1
            val allLines = diffs[0].hunks.flatMap { it.lines }
            allLines.isNotEmpty() shouldBe true
            // Empty newContent has one line (""), so last Removed+Added merge into Modified
            allLines.count { it is DiffLine.Removed || it is DiffLine.Modified || it is DiffLine.Added } shouldBe 3
        }

        "handles mixed additions and deletions" {
            val path = RelativePath.from("mixed.txt", rootPath).getOrThrow()
            val change = FileChange(
                path,
                oldContent = "keep\nremove me\nalso keep",
                newContent = "keep\nalso keep\nnew line",
            )
            val diffs = diffService.buildDiffs(listOf(change))

            diffs shouldHaveSize 1
            val allLines = diffs[0].hunks.flatMap { it.lines }
            allLines.any { it is DiffLine.Removed } shouldBe true
            allLines.any { it is DiffLine.Added } shouldBe true
        }

        "detects changed lines as Modified with word diffs" {
            val path = RelativePath.from("mod.txt", rootPath).getOrThrow()
            val change = FileChange(
                path,
                oldContent = "hello world\nfoo bar\nbaz qux",
                newContent = "hello modified\nfoo bar\nbaz updated",
            )
            val diffs = diffService.buildDiffs(listOf(change))

            diffs shouldHaveSize 1
            val allLines = diffs[0].hunks.flatMap { it.lines }
            // "foo bar" (same) is Context; adjacent Removed+Added pairs merge into Modified
            allLines.any { it is DiffLine.Modified } shouldBe true
            allLines.any { it is DiffLine.Context } shouldBe true
            allLines.none { it is DiffLine.Removed } shouldBe true
            allLines.none { it is DiffLine.Added } shouldBe true
        }

        "handles multiple files" {
            val path1 = RelativePath.from("a.txt", rootPath).getOrThrow()
            val path2 = RelativePath.from("b.txt", rootPath).getOrThrow()
            val changes = listOf(
                FileChange(path1, oldContent = null, newContent = "hello"),
                FileChange(path2, oldContent = "old", newContent = "new"),
            )
            val diffs = diffService.buildDiffs(changes)

            diffs shouldHaveSize 2
            diffs.map { it.path }.toSet() shouldBe setOf(path1, path2)
        }

        "handles identical content — no changes" {
            val path = RelativePath.from("same.txt", rootPath).getOrThrow()
            val change = FileChange(
                path,
                oldContent = "line1\nline2",
                newContent = "line1\nline2",
            )
            val diffs = diffService.buildDiffs(listOf(change))

            diffs shouldHaveSize 1
            val allLines = diffs[0].hunks.flatMap { it.lines }
            // All lines are context, no added/removed/modified
            val nonContext = allLines.filter { it !is DiffLine.Context }
            nonContext shouldHaveSize 0
        }
    }
})
