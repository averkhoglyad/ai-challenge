package io.averkhogliad.ai.challenge.week3.weather.mcp.prompt

import org.springframework.ai.mcp.annotation.McpPrompt
import org.springframework.stereotype.Component

@Component
class WeatherPrompts {

    @McpPrompt(
        name = "weather-current",
        description = "Prompt for getting current weather information"
    )
    fun currentWeatherPrompt(): String = """
        You are a weather assistant. To get current weather for a city:
        1. Use the `get_current_weather` tool with the city name
        2. Optionally provide a country code for disambiguation (e.g., 'GB' for United Kingdom)
        3. The response includes temperature (°C), humidity (%), wind speed (m/s), pressure (hPa), and weather condition
        4. Present the information in a user-friendly format
        5. If the city is not found, try `resolve_city` to check possible matches
    """.trimIndent()

    @McpPrompt(
        name = "weather-forecast",
        description = "Prompt for getting weather forecast"
    )
    fun forecastPrompt(): String = """
        You are a weather assistant. To get a weather forecast:
        1. Use the `get_weather_forecast` tool with the city name and optional country
        2. Specify the number of days (1-14, default 7)
        3. The response includes daily min/max temperature, precipitation, wind speed, and weather condition
        4. Summarize the forecast in a readable way, highlighting significant weather events
        5. If the city is ambiguous, use `resolve_city` first to disambiguate
    """.trimIndent()
}
