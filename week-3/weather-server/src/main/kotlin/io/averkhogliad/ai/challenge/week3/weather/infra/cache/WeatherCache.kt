package io.averkhogliad.ai.challenge.week3.weather.infra.cache

import io.averkhogliad.ai.challenge.week3.weather.core.model.CachedWeatherData
import io.averkhogliad.ai.challenge.week3.weather.core.model.CurrentWeatherData
import io.averkhogliad.ai.challenge.week3.weather.core.model.ForecastData

interface WeatherCache {
    fun getCurrent(key: String): CachedWeatherData<CurrentWeatherData>?
    fun putCurrent(key: String, data: CurrentWeatherData)
    fun getForecast(key: String): CachedWeatherData<ForecastData>?
    fun putForecast(key: String, data: ForecastData)
}
