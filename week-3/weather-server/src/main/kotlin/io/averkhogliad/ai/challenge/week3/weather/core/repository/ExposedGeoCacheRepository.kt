package io.averkhogliad.ai.challenge.week3.weather.core.repository

import io.averkhogliad.ai.challenge.week3.weather.core.model.GeoCacheEntry
import io.averkhogliad.ai.challenge.week3.weather.infra.config.WeatherDurationProperties
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Repository


@Repository
class ExposedGeoCacheRepository(
    private val database: Database,
    private val durationProperties: WeatherDurationProperties
) : GeoCacheRepository {

    init {
        transaction(database) {
            SchemaUtils.create(GeoCacheTable)
        }
    }

    override fun findByKey(cacheKey: String): GeoCacheEntry? = transaction(database) {
        val cutoff = System.currentTimeMillis() - durationProperties.geoTtl.inWholeMilliseconds
        GeoCacheTable.selectAll()
            .where { (GeoCacheTable.cacheKey eq cacheKey) and (GeoCacheTable.createdAt greaterEq cutoff) }
            .singleOrNull()
            ?.toGeoCacheEntry()
    }

    override fun save(cacheKey: String, entry: GeoCacheEntry) {
        transaction(database) {
            val cachedAt = System.currentTimeMillis()
            val inserted = GeoCacheTable.insertIgnore {
                it[GeoCacheTable.cacheKey] = cacheKey
                it[name] = entry.name
                it[country] = entry.country
                it[countryCode] = entry.countryCode
                it[latitude] = entry.latitude
                it[longitude] = entry.longitude
                it[geonameId] = entry.geonameId
                it[admin1] = entry.admin1
                it[population] = entry.population
                it[alternativesCount] = entry.alternativesCount
                it[createdAt] = cachedAt
            }.insertedCount > 0

            if (!inserted) {
                GeoCacheTable.update({ GeoCacheTable.cacheKey eq cacheKey }) {
                    it[name] = entry.name
                    it[country] = entry.country
                    it[countryCode] = entry.countryCode
                    it[latitude] = entry.latitude
                    it[longitude] = entry.longitude
                    it[geonameId] = entry.geonameId
                    it[admin1] = entry.admin1
                    it[population] = entry.population
                    it[alternativesCount] = entry.alternativesCount
                    it[createdAt] = cachedAt
                }
            }
        }
    }

    override fun deleteExpired(maxAgeMillis: Long) {
        transaction(database) {
            val cutoff = System.currentTimeMillis() - maxAgeMillis
            GeoCacheTable.deleteWhere { GeoCacheTable.createdAt less cutoff }
        }
    }

    private fun ResultRow.toGeoCacheEntry() = GeoCacheEntry(
        name = this[GeoCacheTable.name],
        country = this[GeoCacheTable.country],
        countryCode = this[GeoCacheTable.countryCode],
        latitude = this[GeoCacheTable.latitude],
        longitude = this[GeoCacheTable.longitude],
        geonameId = this[GeoCacheTable.geonameId],
        admin1 = this[GeoCacheTable.admin1],
        population = this[GeoCacheTable.population],
        alternativesCount = this[GeoCacheTable.alternativesCount]
    )
}
