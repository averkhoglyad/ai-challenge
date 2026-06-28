package io.averkhogliad.ai.challenge.week3.weather.infra.client

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


// --------------- Geocoding ---------------

@Serializable
data class GeocodingResponse(
    val results: List<GeoResult>? = null
)

@Serializable
data class GeoResult(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String,
    @SerialName("admin1")
    val admin1: String? = null,
    @SerialName("country_code")
    val countryCode: String,
    val population: Long? = null
)

// --------------- Current Weather ---------------

@Serializable
data class OpenMeteoCurrentResponse(
    val current: CurrentData? = null,
    @SerialName("timezone")
    val timezone: String? = null,
    @SerialName("timezone_abbreviation")
    val timezoneAbbreviation: String? = null
)

@Serializable
data class CurrentData(
    @SerialName("temperature_2m")
    val temperature2m: Double = 0.0,
    @SerialName("relative_humidity_2m")
    val relativeHumidity2m: Int? = null,
    @SerialName("apparent_temperature")
    val apparentTemperature: Double? = null,
    @SerialName("weather_code")
    val weatherCode: Int = 0,
    @SerialName("wind_speed_10m")
    val windSpeed10m: Double = 0.0,
    @SerialName("wind_gusts_10m")
    val windGusts10m: Double? = null,
    @SerialName("surface_pressure")
    val surfacePressure: Double = 0.0,
    @SerialName("precipitation")
    val precipitation: Double = 0.0,
    @SerialName("precipitation_probability")
    val precipitationProbability: Int? = null,
    @SerialName("wind_direction_10m")
    val windDirection10m: Int = 0,
    val time: LocalDateTime? = null
)


// --------------- Forecast ---------------

@Serializable
data class OpenMeteoForecastResponse(
    val daily: DailyData? = null,
    @SerialName("timezone")
    val timezone: String? = null,
    @SerialName("timezone_abbreviation")
    val timezoneAbbreviation: String? = null
)

@Serializable
data class DailyData(
    val time: List<LocalDate> = emptyList(),
    @SerialName("weather_code")

    val weatherCode: List<Int> = emptyList(),
    @SerialName("temperature_2m_max")
    val temperature2mMax: List<Double> = emptyList(),
    @SerialName("temperature_2m_min")
    val temperature2mMin: List<Double> = emptyList(),
    @SerialName("precipitation_sum")
    val precipitationSum: List<Double> = emptyList(),
    @SerialName("precipitation_probability_max")
    val precipitationProbabilityMax: List<Int>? = null,
    @SerialName("wind_speed_10m_max")
    val windSpeed10mMax: List<Double> = emptyList(),
    @SerialName("wind_direction_10m_dominant")
    val windDirection10mDominant: List<Int>? = null,
    @SerialName("surface_pressure_max")
    val surfacePressureMax: List<Double>? = null
)
