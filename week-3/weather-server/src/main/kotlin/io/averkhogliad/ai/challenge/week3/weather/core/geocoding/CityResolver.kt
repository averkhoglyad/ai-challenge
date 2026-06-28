package io.averkhogliad.ai.challenge.week3.weather.core.geocoding

import io.averkhogliad.ai.challenge.week3.weather.core.model.CityInfo

interface CityResolver {
    fun resolveCity(city: String, country: String?): CityInfo
}
