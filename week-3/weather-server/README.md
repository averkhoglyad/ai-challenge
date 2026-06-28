# Weather Server

Микросервис для получения текущей погоды и прогноза с REST API и MCP-интерфейсом для AI-агентов. Использует Open-Meteo
как провайдер погоды.

## Технологии

- **Язык:** Kotlin
- **Фреймворк:** Spring Boot 4 (WebMVC, Validation)
- **База данных:** SQLite (кэш геокодинга через Exposed ORM)
- **Кэширование:** Caffeine (in-memory) + SQLite (persistent geo-cache)
- **HTTP-клиент:** Ktor Client (coroutine-based)
- **AI-интеграция:** Spring AI MCP Server (STREAMABLE протокол, аннотации @McpTool/@McpResource/@McpPrompt)
- **Сериализация:** kotlinx-serialization, kotlinx-datetime
- **Тестирование:** Kotest, MockK, Ktor Client Mock, Spring Boot Test

## Запуск

```bash
./gradlew :week-3:weather-server:bootRun
```

Сервер запускается на порту **8081**.

### Docker

```bash
cd week-3/weather-server
docker compose up --build
```

## REST API

Базовый путь: `/api/v1/weather`

### Эндпоинты

| Метод | Путь                       | Описание                 | Код ответа               |
|-------|----------------------------|--------------------------|--------------------------|
| GET   | `/api/v1/weather/current`  | Текущая погода в городе  | 200 OK / 400 / 404 / 503 |
| GET   | `/api/v1/weather/forecast` | Прогноз погоды на N дней | 200 OK / 400 / 404 / 503 |

### Параметры запроса

**Текущая погода:**

| Параметр  | Тип    | Обязательный | Описание                              |
|-----------|--------|--------------|---------------------------------------|
| `city`    | String | Да           | Название города (2–100 символов)      |
| `country` | String | Нет          | Страна для уточнения (2–100 символов) |

**Прогноз погоды:**

| Параметр  | Тип    | Обязательный | Описание                               |
|-----------|--------|--------------|----------------------------------------|
| `city`    | String | Да           | Название города (2–100 символов)       |
| `country` | String | Нет          | Страна для уточнения (2–100 символов)  |
| `days`    | Int    | Нет          | Количество дней (1–14, по умолчанию 7) |

### Примеры запросов

**Текущая погода:**

```bash
curl "http://localhost:8081/api/v1/weather/current?city=London&country=GB"
```

**Прогноз погоды на 5 дней:**

```bash
curl "http://localhost:8081/api/v1/weather/forecast?city=Paris&days=5"
```

### Заголовок X-Stale

Если данные получены из кэша и провайдер погоды недоступен, ответ содержит заголовок `X-Stale: true`. Это означает, что
данные устаревшие, но всё ещё могут быть полезны.

### Пример ответа (текущая погода)

```json
{
  "city": {
    "name": "London",
    "country": "United Kingdom",
    "countryCode": "GB",
    "latitude": 51.5085,
    "longitude": -0.1257,
    "geonameId": 2643743,
    "admin1": "England",
    "population": 7556900
  },
  "temperature": 18.5,
  "feelsLike": 17.2,
  "humidity": 65,
  "windSpeed": 4.2,
  "windGusts": 8.1,
  "pressure": 1013.0,
  "weatherCondition": "PARTLY_CLOUDY",
  "weatherDescription": "Partly cloudy",
  "observationTime": "2026-06-27T14:00",
  "stale": false
}
```

### Пример ответа (прогноз)

```json
{
  "city": {
    "name": "Paris",
    "country": "France",
    "countryCode": "FR",
    "latitude": 48.8534,
    "longitude": 2.3488,
    "geonameId": 2988507
  },
  "days": [
    {
      "date": "2026-06-27",
      "weatherCondition": "RAIN",
      "weatherDescription": "Rain",
      "temperatureMax": 24.3,
      "temperatureMin": 16.1,
      "precipitationSum": 5.2,
      "windSpeedMax": 6.8
    }
  ],
  "generatedAt": "2026-06-27",
  "stale": false
}
```

## Единицы измерения

| Параметр       | Поле                                                           | Единица            |
|----------------|----------------------------------------------------------------|--------------------|
| Температура    | `temperature`, `temperatureMin`, `temperatureMax`, `feelsLike` | °C                 |
| Давление       | `pressure`                                                     | гПа (гектопаскали) |
| Скорость ветра | `windSpeed`, `windGusts`, `windSpeedMax`                       | м/с                |
| Влажность      | `humidity`                                                     | % (0–100)          |
| Осадки         | `precipitationSum`                                             | мм                 |

## Погодные условия

| WMO-код                | Константа                | Описание              |
|:-----------------------|:-------------------------|:----------------------|
| `0`                    | `CLEAR_SKY`              | Ясное небо            |
| `1`                    | `MAINLY_CLEAR`           | Преимущественно ясно  |
| `2`                    | `PARTLY_CLOUDY`          | Переменная облачность |
| `3`                    | `OVERCAST`               | Пасмурно              |
| `45`, `48`             | `FOG`                    | Туман и изморось      |
| `51`, `53`, `55`       | `DRIZZLE`                | Морось                |
| `56`, `57`             | `FREEZING_DRIZZLE`       | Замерзающая морось    |
| `61`, `63`, `65`       | `RAIN`                   | Дождь                 |
| `66`, `67`             | `FREEZING_RAIN`          | Замерзающий дождь     |
| `71`, `73`, `75`, `77` | `SNOW`                   | Снег                  |
| `80`, `81`, `82`       | `RAIN_SHOWERS`           | Ливневый дождь        |
| `85`, `86`             | `SNOW_SHOWERS`           | Снегопад ливневой     |
| `95`                   | `THUNDERSTORM`           | Гроза                 |
| `96`, `99`             | `THUNDERSTORM_WITH_HAIL` | Гроза с градом        |
| *Любой другой*         | `UNKNOWN`                | Неизвестный код       |

## Ошибки

При ошибках возвращается ответ в формате:

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Человекочитаемое описание",
    "details": {}
  }
}
```

### Коды ошибок

| Код                    | HTTP | Описание                    |
|------------------------|------|-----------------------------|
| `VALIDATION_ERROR`     | 400  | Неверные параметры запроса  |
| `NOT_FOUND`            | 404  | Город не найден             |
| `PROVIDER_UNAVAILABLE` | 503  | Провайдер погоды недоступен |
| `INTERNAL_ERROR`       | 500  | Внутренняя ошибка сервера   |

### Пример ответа с ошибкой

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "City not found: 'Atlantis'",
    "details": {
      "city": "Atlantis"
    }
  }
}
```

## MCP-интерфейс

Микросервис предоставляет MCP-интерфейс для AI-агентов (Spring AI MCP Server, протокол STREAMABLE).

### Инструменты (3)

| Инструмент             | Описание                                                                            |
|------------------------|-------------------------------------------------------------------------------------|
| `get_current_weather`  | Получить текущую погоду в городе (температура, влажность, ветер, давление, условия) |
| `get_weather_forecast` | Получить прогноз погоды на N дней (min/max температура, осадки, ветер)              |
| `resolve_city`         | Проверить распознавание города без запроса погоды (координаты, страна, geonameId)   |

### Ресурсы (3)

| URI                         | Описание                                                  |
|-----------------------------|-----------------------------------------------------------|
| `docs://weather/api`        | Полная документация REST API и MCP-инструментов для LLM   |
| `docs://weather/units`      | Единицы измерения, используемые в Weather API             |
| `docs://weather/conditions` | Таблица погодных условий (WMO-коды → строковые константы) |

### Промпты (2)

| Промпт             | Описание                                                                          |
|--------------------|-----------------------------------------------------------------------------------|
| `weather-current`  | Шаблон для получения текущей погоды: запрашивает город и опционально страну       |
| `weather-forecast` | Шаблон для получения прогноза погоды: запрашивает город, страну и количество дней |

## Конфигурация

Основные параметры в `application.yml`:

| Параметр                             | По умолчанию                           | Описание                            |
|--------------------------------------|----------------------------------------|-------------------------------------|
| `server.port`                        | 8081                                   | Порт сервера                        |
| `weather.database.url`               | `jdbc:sqlite:weather-cache.db`         | URL базы данных для кэша геокодинга |
| `weather.open-meteo.base-url`        | `https://api.open-meteo.com`           | Базовый URL Open-Meteo API          |
| `weather.open-meteo.geocoding-url`   | `https://geocoding-api.open-meteo.com` | URL геокодинга Open-Meteo           |
| `weather.open-meteo.connect-timeout` | `PT3S`                                 | Таймаут подключения к провайдеру    |
| `weather.open-meteo.read-timeout`    | `PT5S`                                 | Таймаут чтения ответа               |
| `weather.cache.geo-ttl`              | `P30D`                                 | TTL кэша геокодинга (30 дней)       |
| `weather.cache.current-ttl`          | `PT15M`                                | TTL кэша текущей погоды (15 минут)  |
| `weather.cache.forecast-ttl`         | `PT3H`                                 | TTL кэша прогноза (3 часа)          |

## Тестирование

```bash
# Unit-тесты
./gradlew :week-3:weather-server:test

# Интеграционные тесты
./gradlew :week-3:weather-server:integrationTest

# Все тесты
./gradlew :week-3:weather-server:build
```

## Структура проекта

```
week-3/weather-server/
├── build.gradle.kts
├── Dockerfile
├── docker-compose.yml
└── src/
    ├── main/
    │   ├── kotlin/io/averkhogliad/ai/challenge/week3/weather/
    │   │   ├── App.kt                                        # Точка входа Spring Boot
    │   │   ├── core/
    │   │   │   ├── exception/
    │   │   │   │   ├── CityNotFoundException.kt              # Исключение «город не найден»
    │   │   │   │   ├── InvalidParametersException.kt         # Исключение «неверные параметры»
    │   │   │   │   └── ProviderUnavailableException.kt       # Исключение «провайдер недоступен»
    │   │   │   ├── geocoding/
    │   │   │   │   └── CityResolver.kt                       # Разрешение названия города в координаты
    │   │   │   ├── model/
    │   │   │   │   ├── CacheModels.kt                        # Модели кэша
    │   │   │   │   ├── ErrorModels.kt                        # Модели ошибок (ErrorResponse, ErrorCode)
    │   │   │   │   ├── WeatherCondition.kt                   # Enum погодных условий (WMO → Enum)
    │   │   │   │   ├── WeatherModels.kt                      # Доменные модели погоды
    │   │   │   │   └── WmoMapper.kt                          # Маппинг WMO-кодов в WeatherCondition
    │   │   │   ├── repository/
    │   │   │   │   ├── ExposedGeoCacheRepository.kt          # Реализация репозитория через Exposed
    │   │   │   │   ├── GeoCacheRepository.kt                 # Интерфейс репозитория гео-кэша
    │   │   │   │   └── GeoCacheTable.kt                      # Exposed-таблица гео-кэша
    │   │   │   ├── service/
    │   │   │   │   ├── GeocodingService.kt                   # Сервис геокодинга с кэшированием
    │   │   │   │   └── WeatherService.kt                     # Сервис погоды с кэшированием
    │   │   │   └── validation/
    │   │   │       └── WeatherInputValidator.kt              # Валидация входных параметров
    │   │   ├── infra/
    │   │   │   ├── cache/
    │   │   │   │   ├── CacheKeyNormalizer.kt                 # Нормализация ключей кэша
    │   │   │   │   ├── CaffeineWeatherCache.kt               # Реализация кэша на Caffeine
    │   │   │   │   └── WeatherCache.kt                       # Интерфейс кэша погоды
    │   │   │   ├── client/
    │   │   │   │   ├── OpenMeteoClient.kt                    # HTTP-клиент Open-Meteo (Ktor)
    │   │   │   │   ├── OpenMeteoModels.kt                    # DTO ответов Open-Meteo
    │   │   │   │   ├── OpenMeteoProvider.kt                  # Провайдер конфигурации Ktor
    │   │   │   │   └── UnitConverter.kt                      # Конвертер единиц (км/ч → м/с)
    │   │   │   └── config/
    │   │   │       ├── CacheConfig.kt                        # Конфигурация Caffeine-кэша
    │   │   │       ├── CurrentWeatherCache.kt                # Аннотация кэша текущей погоды
    │   │   │       ├── DatabaseConfig.kt                     # Конфигурация SQLite + Exposed
    │   │   │       ├── DurationProperties.kt                 # Маппинг Duration из конфига
    │   │   │       ├── ForecastWeatherCache.kt               # Аннотация кэша прогноза
    │   │   │       ├── KtorClientConfig.kt                   # Конфигурация Ktor HttpClient
    │   │   │       ├── SerializationConfig.kt                # Конфигурация kotlinx-serialization
    │   │   │       └── WeatherDurationProperties.kt          # Свойства TTL кэша
    │   │   ├── mcp/
    │   │   │   ├── prompt/
    │   │   │   │   └── WeatherPrompts.kt                     # 2 MCP-промпта
    │   │   │   ├── resource/
    │   │   │   │   └── WeatherResources.kt                   # 3 MCP-ресурса (документация для LLM)
    │   │   │   └── tool/
    │   │   │       └── WeatherTools.kt                       # 3 MCP-инструмента
    │   │   └── rest/
    │   │       ├── controller/
    │   │       │   └── WeatherController.kt                  # REST контроллер (2 эндпоинта)
    │   │       ├── dto/
    │   │       │   ├── CityDto.kt                            # DTO города
    │   │       │   ├── CurrentWeatherResponse.kt             # DTO текущей погоды
    │   │       │   └── ForecastResponse.kt                   # DTO прогноза погоды
    │   │       └── handler/
    │   │           └── GlobalExceptionHandler.kt             # Глобальный обработчик ошибок
    │   └── resources/
    │       └── application.yml                               # Конфигурация приложения
    └── test/
        ├── kotlin/io/averkhogliad/ai/challenge/week3/weather/
        │   ├── TestApplication.kt                            # Тестовое приложение
        │   ├── unit/
        │   │   ├── core/
        │   │   │   ├── model/
        │   │   │   │   └── WmoMapperTest.kt                  # Тесты маппинга WMO-кодов
        │   │   │   ├── service/
        │   │   │   │   ├── GeocodingServiceTest.kt           # Unit-тесты GeocodingService
        │   │   │   │   └── WeatherServiceTest.kt             # Unit-тесты WeatherService
        │   │   │   └── validation/
        │   │   │       └── WeatherValidatorTest.kt           # Тесты валидатора
        │   │   └── infra/
        │   │       ├── cache/
        │   │       │   └── CaffeineWeatherCacheTest.kt       # Тесты кэша
        │   │       └── client/
        │   │           └── OpenMeteoClientTest.kt            # Тесты HTTP-клиента (Ktor Mock)
        │   └── it/
        │       ├── IntegrationTest.kt                        # Базовый класс интеграционных тестов
        │       ├── core/repository/
        │       │   └── ExposedGeoCacheRepositoryIT.kt        # Интеграционные тесты репозитория
        │       └── infra/rest/
        │           └── WeatherControllerIT.kt                # Интеграционные тесты REST API
        └── resources/
            ├── application-test.yml                          # Конфигурация для тестов (SQLite)
            └── kotest.properties                             # Настройки Kotest
```
