package io.averkhogliad.ai.challenge.week6.application.review

import io.averkhogliad.ai.challenge.week6.domain.review.ReviewFinding

/**
 * Thread-safe holder for review findings during LLM tool-calling session.
 * Populated by SaveReviewTool, consumed by ReviewCodeUseCase after LLM response.
 */
object ReviewSessionHolder {
    private val findings = mutableListOf<ReviewFinding>()
    private var summary: String? = null

    fun addFindings(newFindings: List<ReviewFinding>) {
        synchronized(this) {
            findings.addAll(newFindings)
        }
    }

    fun setSummary(s: String) {
        synchronized(this) {
            summary = s
        }
    }

    fun collect(): Pair<List<ReviewFinding>, String?> {
        synchronized(this) {
            val result = findings.toList() to summary
            clear()
            return result
        }
    }

    fun clear() {
        synchronized(this) {
            findings.clear()
            summary = null
        }
    }
}
