package io.averkhogliad.ai.challenge.week6.unit.application.review

import io.averkhogliad.ai.challenge.week6.application.review.JsonReviewParser
import io.averkhogliad.ai.challenge.week6.domain.review.FindingCategory
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewFinding
import io.averkhogliad.ai.challenge.week6.domain.review.Severity
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class JsonReviewParserTest : FreeSpec({

    "parseFindings" - {

        "parses valid JSON with findings array" {
            val json = """
                {
                    "findings": [
                        {
                            "category": "BUG",
                            "severity": "CRITICAL",
                            "file": "src/main/App.kt",
                            "line": 42,
                            "description": "Null pointer dereference",
                            "recommendation": "Add null check"
                        }
                    ],
                    "summary": "One critical bug found"
                }
            """.trimIndent()

            val findings = JsonReviewParser.parseFindings(json)

            findings shouldHaveSize 1
            findings[0].category shouldBe FindingCategory.BUG
            findings[0].severity shouldBe Severity.CRITICAL
            findings[0].file shouldBe "src/main/App.kt"
            findings[0].line shouldBe 42
            findings[0].description shouldBe "Null pointer dereference"
            findings[0].recommendation shouldBe "Add null check"
        }

        "parses JSON wrapped in markdown code fences" {
            val json = """
                ```json
                {
                    "findings": [
                        {
                            "category": "ARCHITECTURE",
                            "severity": "WARNING",
                            "file": "src/Service.kt",
                            "line": 10,
                            "description": "God object detected",
                            "recommendation": "Split into smaller services"
                        }
                    ]
                }
                ```
            """.trimIndent()

            val findings = JsonReviewParser.parseFindings(json)

            findings shouldHaveSize 1
            findings[0].category shouldBe FindingCategory.ARCHITECTURE
            findings[0].severity shouldBe Severity.WARNING
        }

        "parses JSON with issues key (alternative format)" {
            val json = """
                {
                    "issues": [
                        {
                            "category": "SECURITY",
                            "severity": "CRITICAL",
                            "description": "SQL injection vulnerability"
                        }
                    ]
                }
            """.trimIndent()

            val findings = JsonReviewParser.parseFindings(json)

            findings shouldHaveSize 1
            findings[0].category shouldBe FindingCategory.SECURITY
            findings[0].severity shouldBe Severity.CRITICAL
            findings[0].description shouldContain "SQL injection"
        }

        "handles lowercase and fuzzy category names" {
            val json = """
                {
                    "findings": [
                        {
                            "category": "bug",
                            "severity": "warning",
                            "description": "Some bug"
                        },
                        {
                            "category": "performance optimization",
                            "severity": "critical issue",
                            "description": "Slow query"
                        },
                        {
                            "category": "maintainability concern",
                            "severity": "info",
                            "description": "Complex method"
                        }
                    ]
                }
            """.trimIndent()

            val findings = JsonReviewParser.parseFindings(json)

            findings shouldHaveSize 3
            findings[0].category shouldBe FindingCategory.BUG
            findings[0].severity shouldBe Severity.WARNING
            findings[1].category shouldBe FindingCategory.PERFORMANCE
            findings[1].severity shouldBe Severity.CRITICAL
            findings[2].category shouldBe FindingCategory.MAINTAINABILITY
            findings[2].severity shouldBe Severity.INFO
        }

        "returns empty list for empty input" {
            val findings = JsonReviewParser.parseFindings("")

            findings.shouldBeEmpty()
        }

        "returns empty list for invalid JSON" {
            val findings = JsonReviewParser.parseFindings("not json at all {broken}")

            findings.shouldBeEmpty()
        }

        "returns empty list for JSON without findings or issues key" {
            val json = """{"something": "else"}"""

            val findings = JsonReviewParser.parseFindings(json)

            findings.shouldBeEmpty()
        }

        "skips findings without required description field" {
            val json = """
                {
                    "findings": [
                        {
                            "category": "BUG",
                            "severity": "CRITICAL"
                        },
                        {
                            "category": "PERFORMANCE",
                            "severity": "WARNING",
                            "description": "Valid finding"
                        }
                    ]
                }
            """.trimIndent()

            val findings = JsonReviewParser.parseFindings(json)

            findings shouldHaveSize 1
            findings[0].description shouldBe "Valid finding"
        }
    }

    "parseSummary" - {

        "parses summary from JSON" {
            val json = """{"findings": [], "summary": "All good"}"""

            val summary = JsonReviewParser.parseSummary(json)

            summary shouldBe "All good"
        }

        "returns null for JSON without summary" {
            val json = """{"findings": [{"category":"BUG","severity":"INFO","description":"x"}]}"""

            val summary = JsonReviewParser.parseSummary(json)

            summary shouldBe null
        }

        "returns null for empty input" {
            val summary = JsonReviewParser.parseSummary("")

            summary shouldBe null
        }
    }
})
