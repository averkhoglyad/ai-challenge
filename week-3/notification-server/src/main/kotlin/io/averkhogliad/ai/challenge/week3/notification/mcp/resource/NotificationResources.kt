package io.averkhogliad.ai.challenge.week3.notification.mcp.resource

import org.springframework.ai.mcp.annotation.McpResource
import org.springframework.stereotype.Component

/**
 * MCP Resources providing documentation for the LLM about the Notifications API.
 *
 * Provides 3 resources accessible via the MCP protocol:
 * - docs://api — REST API reference
 * - docs://pagination — pagination instructions for LLM
 */
@Component
class NotificationResources {

    @McpResource(
        uri = "docs://api",
        name = "api-docs",
        description = "Описание REST API для управления уведомлениями",
        mimeType = "text/markdown"
    )
    fun apiDocs(): String = """
        |# Notifications REST API
        |
        |## Обзор
        |
        |API предоставляет создание и просмотр уведомлений.
        |Все эндпоинты доступны по базовому пути `/api/v1/notifications`.
        |
        |## Эндпоинты
        |
        |### POST /api/v1/notifications
        |Создает новое уведомление.
        |
        |**Тело запроса:**
        |```json
        |{
        |  "title": "Новое сообщение",
        |  "message": "Текст уведомления"
        |}
        |```
        |
        |**Ответ:** 201 Created с JSON созданного уведомления, включая его UUID и время создания.
        |
        |### GET /api/v1/notifications
        |Возвращает список уведомлений с пагинацией, отсортированный по времени создания (новые первыми).
        |
        |**Параметры запроса:**
        |- `limit` (опционально, по умолчанию 50) — размер страницы
        |- `offset` (опционально, по умолчанию 0) — смещение
        |
        |**Ответ:** 200 OK с JSON, содержащим `items` (массив уведомлений) и `meta` (total, limit, offset).
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
        |Коды ошибок: `VALIDATION_ERROR`, `NOT_FOUND`, `INTERNAL_ERROR`.
    """.trimMargin()

    @McpResource(
        uri = "docs://pagination",
        name = "pagination-docs",
        description = "Инструкция по работе с пагинацией для LLM",
        mimeType = "text/markdown"
    )
    fun paginationDocs(): String = """
        |# Пагинация в Notifications API
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
        |- `total` — общее количество уведомлений
        |- `limit` — максимальное количество уведомлений в текущем ответе
        |- `offset` — сколько уведомлений было пропущено от начала
        |
        |## Проверка наличия следующей страницы
        |
        |**Правило:** Если `total > offset + limit`, значит есть еще уведомления на следующей странице.
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
        |2. Покажите пользователю полученные уведомления
        |3. Проверьте условие: если `total > offset + limit`:
        |   - Сообщите пользователю, что есть еще уведомления
        |   - Если пользователь хочет видеть больше, сделайте новый запрос с `offset = offset + limit`
        |4. Повторяйте шаги 2-3, пока пользователь не увидит все нужные уведомления или не закончатся страницы
        |
        |## Параметры по умолчанию
        |
        |- `limit` по умолчанию: 50
        |- `offset` по умолчанию: 0
        |- Максимальный `limit` не ограничен, но рекомендуется не превышать 100 за запрос
        |
        |## Пример диалога
        |
        |Пользователь: "Покажи все мои уведомления"
        |LLM: вызывает list_notifications(limit=50, offset=0)
        |Ответ: total=35, limit=50, offset=0, items=[35 уведомлений]
        |LLM пользователю: "Вот все 35 уведомлений."
    """.trimMargin()
}
