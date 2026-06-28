package io.averkhogliad.ai.challenge.week3.weather.mcp.resource

import io.averkhogliad.ai.challenge.week3.weather.core.validation.WeatherInputValidator.DEFAULT_FORECAST_DAYS
import io.averkhogliad.ai.challenge.week3.weather.core.validation.WeatherInputValidator.MAX_FORECAST_DAYS
import io.averkhogliad.ai.challenge.week3.weather.core.validation.WeatherInputValidator.MIN_FORECAST_DAYS
import org.springframework.ai.mcp.annotation.McpResource
import org.springframework.stereotype.Component

@Component
class WeatherResources {

    @McpResource(
        uri = "docs://weather/api",
        name = "weather-api-docs",
        description = "Полное описание Weather REST API и MCP-инструментов",
        mimeType = "text/markdown"
    )
    fun apiDocs(): String = """
        |# Weather Server API Documentation
        |
        |## Available MCP Tools
        |
        |Tools return JSON strings: success payload or structured error payload.
        |
        |### get_current_weather
        |- **Description**: Получает актуальную текущую погоду в указанном городе
        |- **Parameters**:
        |  - `city` (required): Название города, e.g. 'London'
        |  - `country` (optional): Страна для уточнения, e.g. 'GB'
        |- **Returns**: CurrentWeatherData — температура (°C), влажность (%), ветер (м/с), давление (гПа), погодные условия
        |
        |### get_weather_forecast
        |- **Description**: Дневной прогноз погоды на заданное количество дней
        |- **Parameters**:
        |  - `city` (required): Название города, e.g. 'Paris'
        |  - `country` (optional): Страна для уточнения, e.g. 'FR'
        |  - `days` (optional, default=$DEFAULT_FORECAST_DAYS): Количество дней ($MIN_FORECAST_DAYS-$MAX_FORECAST_DAYS)
        |- **Returns**: ForecastData с min/max температурой, осадками (мм), ветром (м/с)
        |
        |### resolve_city
        |- **Description**: Проверяет распознавание названия города без запроса погоды
        |- **Parameters**:
        |  - `city` (required): Название города, e.g. 'Berlin'
        |  - `country` (optional): Страна для уточнения
        |- **Returns**: CityInfo с координатами, страной и geonameId
        |
        |## REST API Endpoints
        |
        || Endpoint | Method | Description |
        ||----------|--------|-------------|
        || `/api/v1/weather/current?city={city}&country={country}` | GET | Текущая погода |
        || `/api/v1/weather/forecast?city={city}&country={country}&days={days}` | GET | Прогноз погоды, `days` default=$DEFAULT_FORECAST_DAYS, range $MIN_FORECAST_DAYS-$MAX_FORECAST_DAYS |
        |
        |REST weather responses expose city as REST DTO (`CityDto`), not an internal domain model.
        |
        |## Error Responses
        |
        |Error contract:
        |```json
        |{"error":{"code":"VALIDATION_ERROR","message":"Invalid parameters","details":{"parameter":"days"}}}
        |```
        |`details` is an optional structured object (`Map<String,String>`), not a string.
        |
        || Code | HTTP Status | Description |
        ||------|-------------|-------------|
        || NOT_FOUND | 404 | Город не найден |
        || VALIDATION_ERROR | 400 | Неверные параметры запроса |
        || PROVIDER_UNAVAILABLE | 503 | Провайдер погоды недоступен |
        || INTERNAL_ERROR | 500 | Внутренняя ошибка сервера |
    """.trimMargin()

    @McpResource(
        uri = "docs://weather/units",
        name = "weather-units",
        description = "Единицы измерения, используемые в Weather API",
        mimeType = "text/markdown"
    )
    fun unitsDocs(): String = """
        |# Weather API — Единицы измерения
        |
        |Все данные возвращаются в фиксированных единицах измерения:
        |
        || Параметр | Поле | Единица |
        ||----------|------|---------|
        || Температура | `temperature`, `temperatureMin`, `temperatureMax` | °C (градусы Цельсия) |
        || Давление | `pressure` | гПа (гектопаскали = миллибары) |
        || Скорость ветра | `windSpeed`, `windSpeedMax` | м/с (метры в секунду) |
        || Влажность | `humidity` | % (проценты, 0-100) |
        || Осадки | `precipitationSum` | мм (миллиметры) |
        |
        |**Примечание:** значения конвертируются из ответов Open-Meteo (км/ч → м/с для ветра).
    """.trimMargin()

    @McpResource(
        uri = "docs://weather/conditions",
        name = "weather-conditions",
        description = "Таблица погодных условий (WMO-коды → строковые константы)",
        mimeType = "text/markdown"
    )
    fun conditionsDocs(): String = """
        |# Weather API — Погодные условия (WMO → Enum)
        |
        || WMO Code(s) | Константа | Описание |
        ||:---|:---|:---|
        || `0` | `CLEAR_SKY` | Ясное небо |
        || `1` | `MAINLY_CLEAR` | Преимущественно ясно |
        || `2` | `PARTLY_CLOUDY` | Переменная облачность |
        || `3` | `OVERCAST` | Пасмурно |
        || `45`, `48` | `FOG` | Туман и изморось |
        || `51`, `53`, `55` | `DRIZZLE` | Морось |
        || `56`, `57` | `FREEZING_DRIZZLE` | Замерзающая морось |
        || `61`, `63`, `65` | `RAIN` | Дождь |
        || `66`, `67` | `FREEZING_RAIN` | Замерзающий дождь |
        || `71`, `73`, `75`, `77` | `SNOW` | Снег |
        || `80`, `81`, `82` | `RAIN_SHOWERS` | Ливневый дождь |
        || `85`, `86` | `SNOW_SHOWERS` | Снегопад ливневой |
        || `95` | `THUNDERSTORM` | Гроза |
        || `96`, `99` | `THUNDERSTORM_WITH_HAIL` | Гроза с градом |
        || *Любой другой* | `UNKNOWN` | Неизвестный код |
        |
        |**Примечание:** константы возвращаются без локализации. Клиент сам отвечает за перевод.
    """.trimMargin()
}
