package io.averkhogliad.ai.challenge.week3.weather.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class WeatherCondition(val description: String) {
    CLEAR_SKY("Clear sky"),
    MAINLY_CLEAR("Mainly clear"),
    PARTLY_CLOUDY("Partly cloudy"),
    OVERCAST("Overcast"),
    FOG("Fog"),
    DRIZZLE("Drizzle"),
    FREEZING_DRIZZLE("Freezing drizzle"),
    RAIN("Rain"),
    FREEZING_RAIN("Freezing rain"),
    SNOW("Snow"),
    RAIN_SHOWERS("Rain showers"),
    SNOW_SHOWERS("Snow showers"),
    THUNDERSTORM("Thunderstorm"),
    THUNDERSTORM_WITH_HAIL("Thunderstorm with hail"),
    UNKNOWN("Unknown")
}
