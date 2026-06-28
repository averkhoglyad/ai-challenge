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

    @McpPrompt(
        name = "weather-briefing",
        description = "Подготовь подробную сводку по погоде для города с текущей погодой и прогнозом на несколько дней. " +
                "Используй этот сценарий, когда пользователь просит погодную сводку, брифинг или отчёт по погоде."
    )
    fun weatherBriefingPrompt(): String = """
        Ты — метеорологический ассистент. Подготовь подробную сводку по погоде для города "${'$'}city" на ${'$'}days дней.
        
        ## Пошаговая инструкция
        
        Выполняй шаги СТРОГО последовательно. Каждый следующий шаг зависит от результата предыдущего. 
        НЕ пропускай шаги и НЕ вызывай инструменты в произвольном порядке.
        
        ### Шаг 1: Проверка и канонизация названия города
        Вызови инструмент `resolve_city` с параметром:
        - city = "${'$'}city"
        
        Сохрани из результата:
        - `name` — каноническое название города (например, "Санкт-Петербург" вместо "Питер")
        - `latitude`, `longitude` — координаты
        - `country` — страна
        
        Если инструмент вернул ошибку (например, "city not found"), НЕМЕДЛЕННО остановись и сообщи пользователю, 
        что не удалось найти указанный город. Предложи уточнить название или использовать альтернативное написание.
        
        ### Шаг 2: Получение текущей погоды
        Используй каноническое название города из Шага 1 (поле `name`).
        Вызови инструмент `get_current_weather` с параметром:
        - city = <результат_Шага_1.name>
        
        Сохрани: температуру, условия, скорость ветра, влажность, атмосферное давление.
        
        ### Шаг 3: Получение прогноза
        Используй то же каноническое название города из Шага 1.
        Вызови инструмент `get_weather_forecast` с параметрами:
        - city = <результат_Шага_1.name>
        - days = ${'$'}days
        
        Сохрани прогноз на каждый день: дату, минимальную/максимальную температуру, условия, вероятность осадков.
        
        ### Шаг 4: Формирование отчёта
        Используя все собранные данные, сформируй структурированный отчёт в следующем формате:
        
        📍 **Город**: <каноническое_название> (<исходный_запрос>), <страна>
           Координаты: <latitude>°N, <longitude>°E
        
        🌡️ **Сейчас** (<текущая дата и время>):
          • Температура: <temperature>°C
          • Условия: <conditions>
          • Ветер: <wind_speed> м/с (<wind_direction>)
          • Влажность: <humidity>%
          • Давление: <pressure> гПа
        
        📅 **Прогноз на ${'$'}days дней**:
        • <дата_1>: +<temp_max_1>/+<temp_min_1>, <conditions_1> (осадки: <precipitation_probability_1>%)
        • <дата_2>: +<temp_max_2>/+<temp_min_2>, <conditions_2> (осадки: <precipitation_probability_2>%)
        ... (для каждого дня)
        
        💡 **Рекомендации**:
        - <практическая рекомендация 1> (например, "Возьми зонт на <дата>")
        - <практическая рекомендация 2> (например, "На выходных будет отличная погода для прогулок")
        - <практическая рекомендация 3> (например, "Утром прохладно, одевайся слоями")
        
        Рекомендации должны быть:
        - Практическими и применимыми
        - Основанными на реальных данных (не выдуманными)
        - Указывать на дни с осадками, экстремальными температурами, сильным ветром
        - Давать советы по одежде и активностям
        
        ## Важно
        - Всегда используй каноническое название города из `resolve_city` для последующих вызовов
        - Не выдумывай данные — используй только результаты инструментов
        - Если какой-то инструмент недоступен, сообщи об этом и продолжи с доступными данными
        - Форматируй числа с разумной точностью (температура: целые числа, давление: целые, скорость ветра: 1 знак после запятой)
        - Отвечай на русском языке    
    """.trimIndent()
}
