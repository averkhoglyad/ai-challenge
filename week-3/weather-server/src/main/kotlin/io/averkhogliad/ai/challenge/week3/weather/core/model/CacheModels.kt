package io.averkhogliad.ai.challenge.week3.weather.core.model

import kotlinx.serialization.Serializable

@Serializable
data class GeoCacheEntry(
    val name: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val geonameId: Long,
    val admin1: String? = null,
    val population: Int? = null,
    val alternativesCount: Int = 1
)

@Serializable
data class CachedWeatherData<T>(
    val data: T,
    val cachedAt: Long,        // epoch millis
    val stale: Boolean = false
)
