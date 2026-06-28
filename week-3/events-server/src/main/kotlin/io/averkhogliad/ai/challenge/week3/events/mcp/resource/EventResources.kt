package io.averkhogliad.ai.challenge.week3.events.mcp.resource

import org.springframework.ai.mcp.annotation.McpResource
import org.springframework.stereotype.Component

/**
 * MCP Resources providing documentation for the LLM about the Events API.
 *
 * Provides 3 resources accessible via the MCP protocol:
 * - docs://api — REST API reference
 * - docs://date-format — ISO 8601 date format rules
 * - docs://pagination — pagination instructions for LLM
 */
@Component
class EventResources {

    @McpResource(
        uri = "docs://api",
        name = "api-docs",
        description = "Описание REST API для управления событиями календаря",
        mimeType = "text/markdown"
    )
    fun apiDocs(): String = """
        |# Events Calendar REST API
        |
        |## Обзор
        |
        |API предоставляет полный CRUD для управления событиями календаря.
        |Все эндпоинты доступны по базовому пути `/api/v1/events`.
        |
        |## Формат дат
        |
        |Все даты передаются в формате YYYY-MM-DD (LocalDate).
        |Пример: `2025-06-15`
        |
        |## Эндпоинты
        |
        |### POST /api/v1/events
        |Создает новое событие.
        |
        |**Тело запроса:**
        |```json
        |{
        |  "date": "2025-06-15",
        |  "title": "Встреча с командой",
        |  "description": "Обсуждение спринта"
        |}
        |```
        |
        |**Ответ:** 201 Created с JSON созданного события, включая его UUID.
        |
        |### GET /api/v1/events
        |Возвращает список событий с пагинацией и фильтрацией по датам.
        |
        |**Параметры запроса:**
        |- `from_date` (опционально) — начальная дата в формате YYYY-MM-DD
        |- `to_date` (опционально) — конечная дата в формате YYYY-MM-DD
        |- `limit` (опционально, по умолчанию 50) — размер страницы
        |- `offset` (опционально, по умолчанию 0) — смещение
        |
        |**Ответ:** 200 OK с JSON, содержащим `items` (массив событий) и `meta` (total, limit, offset).
        |
        |### GET /api/v1/events/{id}
        |Возвращает событие по его UUID.
        |
        |**Ответ:** 200 OK с JSON события, или 404 если не найдено.
        |
        |### PATCH /api/v1/events/{id}
        |Обновляет существующее событие. Все поля опциональны — передаются только изменяемые.
        |
        |**Тело запроса:**
        |```json
        |{
        |  "date": "2025-06-16",
        |  "title": "Перенесенная встреча"
        |}
        |```
        |
        |**Ответ:** 200 OK с JSON обновленного события, или 404 если не найдено.
        |
        |### DELETE /api/v1/events/{id}
        |Удаляет событие по его UUID.
        |
        |**Ответ:** 204 No Content при успехе, или 404 если не найдено.
        |
        |## Ошибки
        |
        |Все ошибки возвращаются в формате:
        |```json
        |{
        |  "error": {
        |    "code": "ERROR_CODE",
        |    "message": "Человекочитаемое описание",
        |    "details": {}
        |  }
        |}
        |```
        |
        |Коды ошибок: `VALIDATION_ERROR`, `INVALID_DATE_FORMAT`, `NOT_FOUND`, `INTERNAL_ERROR`.
    """.trimMargin()

    @McpResource(
        uri = "docs://date-format",
        name = "date-format-docs",
        description = "Правила форматирования дат в ISO 8601",
        mimeType = "text/markdown"
    )
    fun dateFormatDocs(): String = """
        |# Формат дат: YYYY-MM-DD
        |
        |Все даты в API должны быть в формате YYYY-MM-DD (LocalDate).
        |
        |## Базовый формат
        |
        |`YYYY-MM-DD`
        |
        |Где:
        |- `YYYY` — четыре цифры года (например, 2025)
        |- `MM` — две цифры месяца (01-12)
        |- `DD` — две цифры дня (01-31)
        |
        |## Примеры корректных дат
        |
        |- `2025-01-15` — 15 января 2025
        |- `2025-12-31` — 31 декабря 2025
        |- `2025-06-01` — 1 июня 2025
        |
        |## Примеры НЕКОРРЕКТНЫХ дат
        |
        |- `2025-1-15` — не хватает ведущих нулей
        |- `15.01.2025` — неверный формат (день.месяц.год)
        |- `2025/01/15` — неверный разделитель (должен быть дефис)
        |- `завтра` — относительная дата, не поддерживается
        |
        |## Важные правила
        |
        |1. **Только дата**: время не указывается, только YYYY-MM-DD
        |2. **Ведущие нули обязательны**: месяц 3 должен быть записан как `03`
        |3. **Никаких относительных дат**: "завтра", "через неделю" — сначала вычислите абсолютную дату
    """.trimMargin()

    @McpResource(
        uri = "docs://pagination",
        name = "pagination-docs",
        description = "Инструкция по работе с пагинацией для LLM",
        mimeType = "text/markdown"
    )
    fun paginationDocs(): String = """
        |# Пагинация в Events API
        |
        |## Как это работает
        |
        |API использует offset-based пагинацию. Каждый ответ содержит:
        |
        |```json
        |{
        |  "items": [...],
        |  "meta": {
        |    "total": 42,
        |    "limit": 50,
        |    "offset": 0
        |  }
        |}
        |```
        |
        |Где:
        |- `total` — общее количество событий, удовлетворяющих фильтру
        |- `limit` — максимальное количество событий в текущем ответе
        |- `offset` — сколько событий было пропущено от начала
        |
        |## Проверка наличия следующей страницы
        |
        |**Правило:** Если `total > offset + limit`, значит есть еще события на следующей странице.
        |
        |Пример: `total = 42`, `offset = 0`, `limit = 50`
        |→ 42 > 0 + 50 = 42 > 50 = false → это единственная страница
        |
        |Пример: `total = 120`, `offset = 0`, `limit = 50`
        |→ 120 > 0 + 50 = true → запросите следующую страницу с `offset = 50`
        |
        |## Алгоритм для LLM
        |
        |1. Сделайте первый запрос с `offset = 0` и нужным `limit`
        |2. Покажите пользователю полученные события
        |3. Проверьте условие: если `total > offset + limit`:
        |   - Сообщите пользователю, что есть еще события
        |   - Если пользователь хочет видеть больше, сделайте новый запрос с `offset = offset + limit`
        |4. Повторяйте шаги 2-3, пока пользователь не увидит все нужные события или не закончатся страницы
        |
        |## Параметры по умолчанию
        |
        |- `limit` по умолчанию: 50
        |- `offset` по умолчанию: 0
        |- Максимальный `limit` не ограничен, но рекомендуется не превышать 100 за запрос
        |
        |## Пример диалога
        |
        |Пользователь: "Покажи все мои события"
        |LLM: вызывает list_events(limit=50, offset=0)
        |Ответ: total=35, limit=50, offset=0, items=[35 событий]
        |LLM пользователю: "Вот все 35 событий."
    """.trimMargin()
}