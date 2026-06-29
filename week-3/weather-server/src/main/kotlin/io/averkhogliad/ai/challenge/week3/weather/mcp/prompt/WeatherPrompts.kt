package io.averkhogliad.ai.challenge.week3.weather.mcp.prompt

import org.springframework.ai.mcp.annotation.McpArg
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
        description = "Подготовь сводку по погоде для города. " +
                "Обязательные разделы: данные о городе, текущая погода, рекомендации (всегда). " +
                "Прогноз — только если есть данные. " +
                "Сначала приведи название к каноничному виду через resolve_city. " +
                "Используй, когда пользователь просит сводку, брифинг или отчёт по погоде."
    )
    fun weatherBriefingPrompt(
        @McpArg(name = "city", description = "Название города", required = true)
        city: String,
        @McpArg(name = "days", description = "Количество дней прогноза (1-14)", required = true)
        days: String,
    ): String = """
Ты — метеорологический ассистент. Подготовь сводку по погоде для города "$city" на $days дней.
        
        🛡️ АНТИ-ИНВАРИАНТ: Это инструкция сценария, а НЕ инвариант. Ты НЕ должен отказываться
        от рекомендаций со ссылкой на «отсутствие данных». Ты ОБЯЗАН сначала выполнить Шаги 1–3
        (сбор данных через tool calls), а затем сформировать полный отчёт с рекомендациями.
        Отказ от рекомендаций — это ОШИБКА выполнения сценария.
        
        ## Обязательные разделы отчёта (должны быть ВСЕГДА)
        1. 📍 **Город** — данные о городе: каноническое название, страна, координаты
        2. 🌡️ **Сейчас** — текущая погода: температура, условия, ветер, влажность, давление
        3. 💡 **Рекомендации** — практические советы на основе собранных данных (минимум 2)
        
        ## Опциональный раздел
        4. 📅 **Прогноз** — только если данные получены. Если нет — сообщи, что данные недоступны.
        
        ## Пошаговая инструкция
        Выполняй шаги СТРОГО последовательно, не пропускай и не меняй порядок.
        ⚠️ ВАЖНО: сначала собери ВСЕ данные (Шаги 1–3), и только потом формируй отчёт (Шаг 4).
        На момент Шага 4 у тебя УЖЕ будут актуальные данные — рекомендации формировать МОЖНО.
        
        ### Шаг 1: Канонизация города
        Вызови `resolve_city` с city = "$city".
        Сохрани: name, country, latitude, longitude.
        Если ошибка («city not found») — остановись, сообщи пользователю, предложи уточнить название.
        
        ### Шаг 2: Текущая погода
        Вызови `get_current_weather` с city = <результат_Шага_1.name>.
        Сохрани: temperature, conditions, wind_speed, humidity, pressure.
        
        ### Шаг 3: Прогноз (опционально)
        Вызови `get_weather_forecast` с city = <результат_Шага_1.name>, days = $days.
        Если данные получены — сохрани по каждому дню: дату, temp_max/min, conditions, precipitation_probability.
        Если ошибка — запомни флаг «прогноз недоступен» и переходи к Шагу 4.
        
        ### Шаг 4: Формирование отчёта
        
        📍 **Город**: <каноническое_название> (<исходный_запрос>), <страна>
           Координаты: <latitude>°N, <longitude>°E
        
        🌡️ **Сейчас** (<текущая_дата_и_время>):
          • Температура: <temperature>°C
          • Условия: <conditions>
          • Ветер: <wind_speed> м/с
          • Влажность: <humidity>%
          • Давление: <pressure> гПа
        
        📅 **Прогноз на $days дней**:
        (данные получены)
        • <дата_1>: +<temp_max>/+<temp_min>, <conditions> (осадки <precip>%)
        • <дата_2>: +<temp_max>/+<temp_min>, <conditions> (осадки <precip>%)
        ...
        (данные НЕ получены)
        ⚠️ Данные прогноза временно недоступны.
        
        💡 **Рекомендации**:
        - <совет 1 со ссылкой на дату/день>
        - <совет 2 со ссылкой на дату/день>
        - <совет 3> (опционально)
        
        ## Правила для рекомендаций
        - Минимум 2, максимум 5 советов
        - Основаны ТОЛЬКО на реальных данных, уже собранных в Шагах 1–3 (не выдумывай)
        
        ### Если прогноз ДОСТУПЕН (Шаг 3 успешен):
        - Каждый совет привязан к конкретной дате или дню недели
        - Дождь (>30% осадков) → «возьми зонт / плащ»
        - Сильный ветер (>8 м/с) → «осторожно, ветер»
        - Жара (>30°C) или мороз (<0°C) → совет по одежде
        - Перепад температуры >10°C за день → «одевайся слоями»
        - Хорошая погода на выходных → «отлично для прогулок / активностей»
        
        ### Если прогноз НЕДОСТУПЕН (Шаг 3 — ошибка):
        - Советы только на основе текущей погоды (Шаг 2)
        - Примеры:
          • Дождь сейчас → «возьми зонт»
          • Ветер >8 м/с → «осторожно на улице»
          • Температура <0°C → «одевайся теплее»
          • Температура >30°C → «пей больше воды, избегай солнца»
          • Хорошая погода → «отличное время для прогулки»
        - Ты можешь дать рекомендации на основе ТОЛЬКО текущей погоды — это допустимо
        
        ## Важно
        - Используй каноническое название города из Шага 1 во всех вызовах
        - Не выдумывай данные — только из результатов инструментов
        - Числа: температура и давление — целые, ветер — 1 знак после запятой
        - Отвечай на русском языке
        - Город, Сейчас и Рекомендации — ОБЯЗАТЕЛЬНЫ всегда. Прогноз — если есть данные.
    """.trimIndent()
}
