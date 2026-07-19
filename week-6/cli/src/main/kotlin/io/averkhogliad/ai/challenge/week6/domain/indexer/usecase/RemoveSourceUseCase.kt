package io.averkhogliad.ai.challenge.week6.domain.indexer.usecase

import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedSourceRepository

class RemoveSourceUseCase(
    private val sourceRepository: IndexedSourceRepository,
) {
    suspend fun execute(sourceId: String) {
        sourceRepository.removeSource(sourceId)
    }
}
