package io.averkhogliad.ai.challenge.week3.weather.core.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable


@Serializable
data class CityInfo(
    val name: String,
    val country: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val geonameId: Long,
    val admin1: String? = null,
    val population: Int? = null
)

@Serializable
data class CurrentWeatherData(
    val temperature: Double,        // °C
    val feelsLike: Double,          // °C
    val humidity: Int,              // %
    val windSpeed: Double,          // m/s
    val windGusts: Double?,         // m/s
    val pressure: Double,           // hPa
    val weatherCondition: WeatherCondition,
    val weatherDescription: String,
    val observationTime: LocalDateTime,
    val city: CityInfo,

    val stale: Boolean = false      // true if data from fallback cache
)

@Serializable
data class DailyForecastData(
    val date: LocalDate,
    val weatherCondition: WeatherCondition,

    val weatherDescription: String,
    val temperatureMax: Double,     // °C
    val temperatureMin: Double,     // °C
    val precipitationSum: Double,   // mm
    val windSpeedMax: Double        // m/s
)

@Serializable
data class ForecastData(
    val city: CityInfo,
    val days: List<DailyForecastData>,
    val generatedAt: LocalDate,
    val stale: Boolean = false      // true if data from fallback cache
)
