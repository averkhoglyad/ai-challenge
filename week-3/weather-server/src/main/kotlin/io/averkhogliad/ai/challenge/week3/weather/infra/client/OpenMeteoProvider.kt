package io.averkhogliad.ai.challenge.week3.weather.infra.client

interface OpenMeteoProvider {
    fun geocode(city: String, country: String?): GeocodingResponse
    fun getCurrentWeather(lat: Double, lon: Double): OpenMeteoCurrentResponse
    fun getForecast(lat: Double, lon: Double, days: Int): OpenMeteoForecastResponse
}
