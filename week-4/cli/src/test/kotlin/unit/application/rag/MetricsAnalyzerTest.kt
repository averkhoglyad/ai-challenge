package io.averkhogliad.ai.challenge.week4.cli.unit.application.rag

import io.averkhogliad.ai.challenge.week4.cli.application.rag.AggregatedStats
import io.averkhogliad.ai.challenge.week4.cli.application.rag.MetricsAnalyzer
import io.averkhogliad.ai.challenge.week4.cli.application.rag.ModeStats
import io.averkhogliad.ai.challenge.week4.cli.application.rag.QueryHistoryService
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.SearchMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class MetricsAnalyzerTest : FreeSpec({

    lateinit var historyService: QueryHistoryService
    lateinit var analyzer: MetricsAnalyzer

    beforeEach {
        historyService = mockk()
        analyzer = MetricsAnalyzer(historyService)
    }

    "analyze" - {

        "returns empty report when no queries" {
            runTest {
                // given
                coEvery { historyService.getAggregatedStats(50) } returns AggregatedStats(
                    totalQueries = 0,
                    byMode = emptyMap(),
                    avgTotalTimeMs = 0L,
                    avgTokenUsage = 0
                )

                // when
                val report = analyzer.analyze()

                // then
                report.modeStats shouldBe emptyMap()
                report.summary shouldBe "Нет данных для анализа. Выполните несколько запросов."
            }
        }

        "returns recommendations when data available" {
            runTest {
                // given
                val filteredStats = ModeStats(count = 20, avgTimeMs = 1100, avgScore = 0.78f, avgTokens = 350)
                val rerankedStats = ModeStats(count = 10, avgTimeMs = 3400, avgScore = 0.89f, avgTokens = 1280)
                val stats = AggregatedStats(
                    totalQueries = 30,
                    byMode = mapOf(SearchMode.Filtered to filteredStats, SearchMode.Reranked to rerankedStats),
                    avgTotalTimeMs = 1800L,
                    avgTokenUsage = 650
                )
                coEvery { historyService.getAggregatedStats(50) } returns stats

                // when
                val report = analyzer.analyze()

                // then
                report.modeStats.size shouldBe 2
                report.recommendations.isNotEmpty() shouldBe true
                report.modeStats[SearchMode.Reranked]!!.avgScore shouldBe 0.89f
            }
        }
    }

    "compareModes" - {

        "computes correct deltas" {
            runTest {
                // given
                val rawStats = ModeStats(count = 5, avgTimeMs = 900, avgScore = 0.62f, avgTokens = 320)
                val rerankedStats = ModeStats(count = 5, avgTimeMs = 3400, avgScore = 0.89f, avgTokens = 1280)
                val stats = AggregatedStats(
                    totalQueries = 10,
                    byMode = mapOf(SearchMode.Raw to rawStats, SearchMode.Reranked to rerankedStats),
                    avgTotalTimeMs = 2150L,
                    avgTokenUsage = 800
                )
                coEvery { historyService.getAggregatedStats(100) } returns stats

                // when
                val comparison = analyzer.compareModes(SearchMode.Raw, SearchMode.Reranked)

                // then
                comparison.mode1 shouldBe SearchMode.Raw
                comparison.mode2 shouldBe SearchMode.Reranked
                // (3400-900)/900 ≈ +278%
                assert(comparison.delta.timeDeltaPercent > 200f)
                // (0.89-0.62)/0.62 ≈ +44%
                assert(comparison.delta.scoreDeltaPercent > 30f)
                // (1280-320)/320 ≈ +300%
                assert(comparison.delta.tokenDeltaPercent > 200f)
            }
        }
    }
})
