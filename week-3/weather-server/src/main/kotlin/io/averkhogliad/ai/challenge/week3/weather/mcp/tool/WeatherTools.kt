package io.averkhogliad.ai.challenge.week3.weather.mcp.tool

import io.averkhogliad.ai.challenge.week3.weather.core.exception.CityNotFoundException
import io.averkhogliad.ai.challenge.week3.weather.core.exception.InvalidParametersException
import io.averkhogliad.ai.challenge.week3.weather.core.exception.ProviderUnavailableException
import io.averkhogliad.ai.challenge.week3.weather.core.geocoding.CityResolver
import io.averkhogliad.ai.challenge.week3.weather.core.model.*
import io.averkhogliad.ai.challenge.week3.weather.core.service.WeatherService
import io.averkhogliad.ai.challenge.week3.weather.core.validation.WeatherInputValidator
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component

@Component
class WeatherTools(
    private val weatherService: WeatherService,
    private val cityResolver: CityResolver,
    private val json: Json
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @McpTool(
        name = "get_current_weather",
        description = "Получает актуальную текущую погоду в указанном городе. Возвращает температуру, осадки, давление, ветер и тип погодных условий. Названия города и страны должны быть на английском языке."
    )
    fun getCurrentWeather(
        @McpToolParam(description = "Название города на английском языке (например, 'Moscow' или 'London')")
        city: String,
        @McpToolParam(
            description = "Страна для уточнения на английском языке (ISO-код или название, опционально). Используйте, если название города неоднозначно.",
            required = false
        )
        country: String?
    ): String = handleToolCall("get_current_weather") {
        val result = weatherService.getCurrentWeather(city, country)
        json.encodeToString(CurrentWeatherData.serializer(), result)
    }

    @McpTool(
        name = "get_weather_forecast",
        description = "Возвращает дневной прогноз погоды на указанное количество дней (максимум 14). Включает min/max температуры, осадки, ветер и тип погоды для каждого дня. Названия города и страны должны быть на английском языке."
    )
    fun getWeatherForecast(
        @McpToolParam(description = "Название города на английском языке")
        city: String,
        @McpToolParam(description = "Страна для уточнения на английском языке (опционально)", required = false)
        country: String?,
        @McpToolParam(description = "Количество дней прогноза (от 1 до 14). По умолчанию 7.", required = false)
        days: Int? = null
    ): String = handleToolCall("get_weather_forecast") {
        val normalizedDays = days ?: WeatherInputValidator.DEFAULT_FORECAST_DAYS
        val result = weatherService.getForecast(city, country, normalizedDays)
        json.encodeToString(ForecastData.serializer(), result)
    }

    @McpTool(
        name = "resolve_city",
        description = "Проверяет, как сервис распознает название города, и показывает количество альтернатив. Не возвращает погоду. Используйте для проверки неоднозначности перед планированием. Названия города и страны должны быть на английском языке."
    )
    fun resolveCity(
        @McpToolParam(description = "Название города для проверки на английском языке")
        city: String,
        @McpToolParam(description = "Страна для уточнения на английском языке (опционально)", required = false)
        country: String?
    ): String = handleToolCall("resolve_city") {
        val result = cityResolver.resolveCity(city, country)
        json.encodeToString(CityInfo.serializer(), result)
    }

    private fun handleToolCall(toolName: String, block: () -> String): String {
        try {
            logger.debug("MCP tool: $toolName")
            return block()
        } catch (e: CityNotFoundException) {
            logger.warn("MCP tool: city not found in $toolName", e)
            return errorJson(
                code = ErrorCode.NOT_FOUND,
                message = e.message ?: "City not found",
                details = mapOf("city" to e.city) + (e.country?.let { mapOf("country" to it) } ?: emptyMap())
            )
        } catch (e: InvalidParametersException) {
            logger.warn("MCP tool: invalid parameters in $toolName", e)
            return errorJson(
                ErrorCode.VALIDATION_ERROR,
                e.message ?: "Invalid parameters",
                e.parameter?.let { mapOf("parameter" to it) })
        } catch (e: ProviderUnavailableException) {
            logger.error("MCP tool: provider unavailable in $toolName", e)
            return errorJson(
                ErrorCode.PROVIDER_UNAVAILABLE,
                e.message ?: "Provider unavailable",
                e.message?.let { mapOf("reason" to it) })
        } catch (e: Exception) {
            logger.error("MCP tool error: $toolName", e)
            return errorJson(ErrorCode.INTERNAL_ERROR, e.message ?: "Unknown error")
        }
    }

    private fun errorJson(code: ErrorCode, message: String, details: Map<String, String>? = null): String =
        json.encodeToString(
            ErrorResponse.serializer(),
            ErrorResponse(error = ErrorDetail(code = code, message = message, details = details))
        )
}
