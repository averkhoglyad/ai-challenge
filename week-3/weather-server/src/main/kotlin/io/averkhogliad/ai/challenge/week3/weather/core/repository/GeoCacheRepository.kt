package io.averkhogliad.ai.challenge.week3.weather.core.repository

import io.averkhogliad.ai.challenge.week3.weather.core.model.GeoCacheEntry

interface GeoCacheRepository {
    fun findByKey(cacheKey: String): GeoCacheEntry?
    fun save(cacheKey: String, entry: GeoCacheEntry)
    fun deleteExpired(maxAgeMillis: Long)
}
