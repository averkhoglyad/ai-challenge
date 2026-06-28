package io.averkhogliad.ai.challenge.week3.weather.core.model

object WmoMapper {
    fun fromWmoCode(code: Int): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR_SKY
        1 -> WeatherCondition.MAINLY_CLEAR
        2 -> WeatherCondition.PARTLY_CLOUDY
        3 -> WeatherCondition.OVERCAST
        45, 48 -> WeatherCondition.FOG
        51, 53, 55 -> WeatherCondition.DRIZZLE
        56, 57 -> WeatherCondition.FREEZING_DRIZZLE
        61, 63, 65 -> WeatherCondition.RAIN
        66, 67 -> WeatherCondition.FREEZING_RAIN
        71, 73, 75, 77 -> WeatherCondition.SNOW
        80, 81, 82 -> WeatherCondition.RAIN_SHOWERS
        85, 86 -> WeatherCondition.SNOW_SHOWERS
        95 -> WeatherCondition.THUNDERSTORM
        96, 99 -> WeatherCondition.THUNDERSTORM_WITH_HAIL
        else -> WeatherCondition.UNKNOWN
    }
}
