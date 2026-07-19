package io.averkhogliad.ai.challenge.week6.unit.cli.rendering

import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.week6.cli.rendering.SearchResultsRenderer
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath
import io.averkhogliad.ai.challenge.week6.domain.fileops.model.SearchHit
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import java.nio.file.Path
import kotlin.io.path.createTempDirectory

class SearchResultsRendererTest : FreeSpec({

    lateinit var rootPath: Path
    lateinit var renderer: SearchResultsRenderer

    beforeTest {
        rootPath = createTempDirectory("sr-test-")
        renderer = SearchResultsRenderer(mockk<Terminal>())
    }

    afterTest { rootPath.toFile().deleteRecursively() }

    "headers return correct values" {
        renderer.headers() shouldBe listOf("File", "Line", "Snippet")
    }

    "rows format single hit correctly" {
        val hits = listOf(
            SearchHit(
                RelativePath.from("src/main.kt", rootPath).getOrThrow(),
                42, "fun main() {", emptyList(), emptyList()
            )
        )
        val rows = renderer.rows(hits)
        rows shouldHaveSize 1
        rows[0] shouldBe listOf("src/main.kt", "42", "fun main() {")
    }

    "rows truncate long snippets to 80 chars" {
        val hits = listOf(
            SearchHit(
                RelativePath.from("long.kt", rootPath).getOrThrow(),
                1, "a".repeat(120), emptyList(), emptyList()
            )
        )
        renderer.rows(hits)[0][2].length shouldBe 80
    }

    "rows handle multiple hits" {
        val hits = listOf(
            SearchHit(RelativePath.from("a.kt", rootPath).getOrThrow(), 1, "hello", emptyList(), emptyList()),
            SearchHit(RelativePath.from("b.kt", rootPath).getOrThrow(), 10, "world", emptyList(), emptyList()),
        )
        val rows = renderer.rows(hits)
        rows shouldHaveSize 2
        rows[0][0] shouldBe "a.kt"
        rows[1][0] shouldBe "b.kt"
    }
})
