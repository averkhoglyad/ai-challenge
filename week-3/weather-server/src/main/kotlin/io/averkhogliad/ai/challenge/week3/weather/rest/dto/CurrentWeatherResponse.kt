package io.averkhogliad.ai.challenge.week3.weather.rest.dto

import io.averkhogliad.ai.challenge.week3.weather.core.model.WeatherCondition
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable


@Serializable
data class CurrentWeatherResponse(
    val city: CityDto,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val windGusts: Double?,
    val pressure: Double,
    val weatherCondition: WeatherCondition,
    val weatherDescription: String,
    val observationTime: LocalDateTime,
    val stale: Boolean = false
)
