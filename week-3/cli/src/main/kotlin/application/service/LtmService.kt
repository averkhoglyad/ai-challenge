package io.averkhogliad.ai.challenge.week3.cli.application.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact
import io.averkhogliad.ai.challenge.week3.cli.domain.model.FactId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.FactRepository
import java.time.Clock
import java.util.*

/**
 * Application service for long-term memory fact use cases.
 */
class LtmService(
    private val factRepository: FactRepository,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun saveFact(content: String): Fact {
        val fact = Fact(
            id = FactId(UUID.randomUUID().toString()),
            content = content,
            createdAt = clock.instant()
        )
        return factRepository.save(fact)
    }

    suspend fun listFacts(): List<Fact> = factRepository.findAll()

    suspend fun forgetFact(factId: String): Boolean = factRepository.delete(FactId(factId))

    suspend fun searchFacts(query: String): List<Fact> = factRepository.search(query)
}
