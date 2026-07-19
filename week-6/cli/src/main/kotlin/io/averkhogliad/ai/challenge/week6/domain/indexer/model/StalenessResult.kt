package io.averkhogliad.ai.challenge.week6.domain.indexer.model

sealed interface StalenessResult {
    data object Fresh : StalenessResult
    data class Stale(val reason: String) : StalenessResult
    data object NoIndex : StalenessResult
    data object NotApplicable : StalenessResult
}
