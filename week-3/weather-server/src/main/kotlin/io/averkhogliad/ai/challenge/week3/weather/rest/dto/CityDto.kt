package io.averkhogliad.ai.challenge.week3.weather.rest.dto

import io.averkhogliad.ai.challenge.week3.weather.core.model.CityInfo
import kotlinx.serialization.Serializable

@Serializable
data class CityDto(
    val name: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val geonameId: Long,
    val admin1: String? = null,
    val population: Int? = null
)

fun CityInfo.toDto() = CityDto(
    name = name,
    country = country,
    countryCode = countryCode,
    latitude = latitude,
    longitude = longitude,
    geonameId = geonameId,
    admin1 = admin1,
    population = population
)
