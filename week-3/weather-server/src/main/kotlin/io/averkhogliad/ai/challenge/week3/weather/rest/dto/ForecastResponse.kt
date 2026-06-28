package io.averkhogliad.ai.challenge.week3.weather.rest.dto

import io.averkhogliad.ai.challenge.week3.weather.core.model.WeatherCondition
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable


@Serializable
data class ForecastResponse(
    val city: CityDto,
    val days: List<DailyForecastDto>,
    val generatedAt: LocalDate,
    val stale: Boolean = false
)


@Serializable
data class DailyForecastDto(
    val date: LocalDate,
    val weatherCondition: WeatherCondition,

    val weatherDescription: String,
    val temperatureMax: Double,
    val temperatureMin: Double,
    val precipitationSum: Double,
    val windSpeedMax: Double
)
