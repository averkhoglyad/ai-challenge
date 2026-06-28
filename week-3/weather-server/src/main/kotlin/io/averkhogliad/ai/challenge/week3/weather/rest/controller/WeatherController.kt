package io.averkhogliad.ai.challenge.week3.weather.rest.controller

import io.averkhogliad.ai.challenge.week3.weather.core.service.WeatherService
import io.averkhogliad.ai.challenge.week3.weather.rest.dto.CurrentWeatherResponse
import io.averkhogliad.ai.challenge.week3.weather.rest.dto.DailyForecastDto
import io.averkhogliad.ai.challenge.week3.weather.rest.dto.ForecastResponse
import io.averkhogliad.ai.challenge.week3.weather.rest.dto.toDto
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.slf4j.LoggerFactory
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val STALE_HEADER = "X-Stale"
private const val STALE_HEADER_VALUE = "true"

@RestController
@RequestMapping("/api/v1/weather")
@Validated
class WeatherController(
    private val weatherService: WeatherService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/current")
    fun getCurrentWeather(
        @RequestParam @NotBlank @Size(min = 2, max = 100) city: String,
        @RequestParam(required = false) @Size(min = 2, max = 100) country: String?,
        response: HttpServletResponse
    ): CurrentWeatherResponse {
        logger.debug("GET /api/v1/weather/current?city=$city&country=$country")

        val data = weatherService.getCurrentWeather(city, country)
        if (data.stale) {
            response.setHeader(STALE_HEADER, STALE_HEADER_VALUE)
        }
        return CurrentWeatherResponse(
            city = data.city.toDto(),
            temperature = data.temperature,
            feelsLike = data.feelsLike,
            humidity = data.humidity,
            windSpeed = data.windSpeed,
            windGusts = data.windGusts,
            pressure = data.pressure,
            weatherCondition = data.weatherCondition,
            weatherDescription = data.weatherDescription,
            observationTime = data.observationTime,
            stale = data.stale
        )
    }

    @GetMapping("/forecast")
    fun getForecast(
        @RequestParam @NotBlank @Size(min = 2, max = 100) city: String,
        @RequestParam(required = false) @Size(min = 2, max = 100) country: String?,
        @RequestParam(defaultValue = "7") @Min(1) @Max(14) days: Int,
        response: HttpServletResponse
    ): ForecastResponse {
        logger.debug("GET /api/v1/weather/forecast?city=$city&country=$country&days=$days")

        val data = weatherService.getForecast(city, country, days)
        if (data.stale) {
            response.setHeader(STALE_HEADER, STALE_HEADER_VALUE)
        }
        return ForecastResponse(
            city = data.city.toDto(),
            days = data.days.map { day ->
                DailyForecastDto(
                    date = day.date,
                    weatherCondition = day.weatherCondition,
                    weatherDescription = day.weatherDescription,
                    temperatureMax = day.temperatureMax,
                    temperatureMin = day.temperatureMin,
                    precipitationSum = day.precipitationSum,
                    windSpeedMax = day.windSpeedMax
                )
            },
            generatedAt = data.generatedAt,
            stale = data.stale
        )
    }
}
