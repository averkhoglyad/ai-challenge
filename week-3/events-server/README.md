# Calendar Event Storage (Events Server)

Микросервис для хранения событий календаря с REST API и MCP-интерфейсом для AI-агентов.

## Технологии

- **Язык:** Kotlin
- **Фреймворк:** Spring Boot 4 (WebMVC, Data JDBC, Validation)
- **База данных:** SQLite (основная), H2 (тесты)
- **AI-интеграция:** Spring AI MCP Server (STREAMABLE протокол)
- **Сериализация:** kotlinx-serialization
- **Тестирование:** Kotest, MockK, Spring Boot Test

## Запуск

```bash
./gradlew :week-3:events-server:bootRun
```

Сервер запускается на порту **8080**.

### Docker

```bash
cd week-3/events-server
docker compose up --build
```

## REST API

Базовый путь: `/api/v1`

### Эндпоинты

| Метод  | Путь                  | Описание                            | Код ответа           |
|--------|-----------------------|-------------------------------------|----------------------|
| POST   | `/api/v1/events`      | Создать событие                     | 201 Created          |
| GET    | `/api/v1/events`      | Список событий (пагинация + фильтр) | 200 OK               |
| GET    | `/api/v1/events/{id}` | Получить событие по ID              | 200 OK / 404         |
| PATCH  | `/api/v1/events/{id}` | Обновить событие                    | 200 OK / 404         |
| DELETE | `/api/v1/events/{id}` | Удалить событие                     | 204 No Content / 404 |

### Примеры запросов

**Создание события:**

```bash
curl -X POST http://localhost:8080/api/v1/events \
  -H "Content-Type: application/json" \
  -d '{"date":"2026-06-27T14:30:00Z","title":"Встреча"}'
```

**Список событий с пагинацией:**

```bash
curl "http://localhost:8080/api/v1/events?limit=10&offset=0"
```

**Список событий с фильтрацией по датам:**

```bash
curl "http://localhost:8080/api/v1/events?from_date=2026-06-01T00:00:00Z&to_date=2026-06-30T23:59:59Z"
```

**Получение события по ID:**

```bash
curl http://localhost:8080/api/v1/events/{id}
```

**Частичное обновление события:**

```bash
curl -X PATCH http://localhost:8080/api/v1/events/{id} \
  -H "Content-Type: application/json" \
  -d '{"title":"Новое название"}'
```

**Удаление события:**

```bash
curl -X DELETE http://localhost:8080/api/v1/events/{id}
```

## Формат дат

Все даты передаются **только в ISO 8601**. Поддерживаются два варианта:

- **Только дата:** `YYYY-MM-DD` (например, `2026-06-27`)
- **Дата и время:** `YYYY-MM-DDTHH:mm:ssZ` (например, `2026-06-27T14:30:00Z`)

Все времена указываются в UTC (суффикс `Z`).

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

| Код                   | HTTP | Описание                           |
|-----------------------|------|------------------------------------|
| `VALIDATION_ERROR`    | 400  | Ошибка валидации полей запроса     |
| `INVALID_DATE_FORMAT` | 400  | Неверный формат даты (не ISO 8601) |
| `NOT_FOUND`           | 404  | Событие с указанным ID не найдено  |
| `INTERNAL_ERROR`      | 500  | Внутренняя ошибка сервера          |

### Пример ответа с ошибкой

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": {
      "date": "Invalid ISO 8601 date format",
      "title": "must not be blank"
    }
  }
}
```

## MCP-интерфейс

Микросервис предоставляет MCP-интерфейс для AI-агентов (Spring AI MCP Server, протокол STREAMABLE).

### Инструменты (5)

| Инструмент          | Описание                                                    |
|---------------------|-------------------------------------------------------------|
| `create_event`      | Создать новое событие календаря                             |
| `list_events`       | Получить список событий с пагинацией и фильтрацией по датам |
| `get_event_details` | Получить детальную информацию о событии по UUID             |
| `update_event`      | Изменить существующее событие                               |
| `delete_event`      | Удалить событие по UUID                                     |

### Ресурсы (3)

| URI                  | Описание                                  |
|----------------------|-------------------------------------------|
| `docs://api`         | Полная документация REST API для LLM      |
| `docs://date-format` | Правила форматирования дат в ISO 8601     |
| `docs://pagination`  | Инструкция по работе с пагинацией для LLM |

### Промпты (2)

| Промпт           | Описание                                                                                     |
|------------------|----------------------------------------------------------------------------------------------|
| `plan-meeting`   | Шаблон для планирования новой встречи: запрашивает дату, заголовок, участников, длительность |
| `weekly-summary` | Шаблон для получения сводки событий за неделю                                                |

> **Примечание:** TODO-аннотации в MCP-классах (`EventTools`, `EventResources`, `EventPrompts`) ожидают апгрейда Spring
> AI до версии 2.0+, когда появятся аннотации `@McpTool`, `@McpResource`, `@McpPrompt`. Пока методы зарегистрированы
> программно через `McpToolCallbackProvider` и аналогичные API.

## Тестирование

```bash
# Unit-тесты (31 тест)
./gradlew :week-3:events-server:test

# Интеграционные тесты (7 тестов)
./gradlew :week-3:events-server:integrationTest

# Все тесты
./gradlew :week-3:events-server:build
```

## Структура проекта

```
week-3/events-server/
├── build.gradle.kts
├── Dockerfile
├── docker-compose.yml
└── src/
    ├── main/
    │   ├── kotlin/io/averkhogliad/ai/challenge/week3/events/
    │   │   ├── Application.kt                    # Точка входа Spring Boot
    │   │   ├── model/
    │   │   │   └── Event.kt                      # Модель события (@Table)
    │   │   ├── repository/
    │   │   │   ├── EventRepository.kt             # Spring Data JDBC репозиторий
    │   │   │   ├── EventRepositoryCustom.kt       # Кастомные методы репозитория
    │   │   │   └── EventRepositoryImpl.kt         # Реализация кастомных методов
    │   │   ├── rest/
    │   │   │   ├── dto/
    │   │   │   │   ├── CreateEventRequest.kt      # DTO создания события
    │   │   │   │   ├── UpdateEventRequest.kt      # DTO обновления события
    │   │   │   │   └── PaginatedResponse.kt       # DTO пагинированного ответа
    │   │   │   ├── ErrorResponse.kt               # Модель ошибки
    │   │   │   ├── EventController.kt             # REST контроллер (5 эндпоинтов)
    │   │   │   ├── EventNotFoundException.kt      # Исключение «не найдено»
    │   │   │   ├── EventService.kt                # Бизнес-логика
    │   │   │   └── GlobalExceptionHandler.kt      # Глобальный обработчик ошибок
    │   │   ├── validation/
    │   │   │   ├── Iso8601Date.kt                 # Аннотация валидации даты
    │   │   │   └── Iso8601DateValidator.kt        # Валидатор ISO 8601
    │   │   └── mcp/
    │   │       ├── EventTools.kt                  # 5 MCP-инструментов
    │   │       ├── EventResources.kt              # 3 MCP-ресурса
    │   │       └── EventPrompts.kt                # 2 MCP-промпта
    │   └── resources/
    │       ├── application.properties             # Конфигурация приложения
    │       └── schema.sql                         # DDL создание таблицы event
    └── test/
        ├── kotlin/io/averkhogliad/ai/challenge/week3/events/
        │   ├── unit/
        │   │   ├── rest/
        │   │   │   ├── ErrorResponseTest.kt       # Тесты ErrorResponse
        │   │   │   └── EventServiceTest.kt         # Unit-тесты EventService
        │   │   └── validation/
        │   │       └── Iso8601DateValidatorTest.kt # Тесты валидатора дат
        │   └── it/
        │       └── rest/
        │           ├── EventControllerIT.kt        # Интеграционные тесты API
        │           └── EventControllerTestConfig.kt # Конфигурация интеграционных тестов
        └── resources/
            ├── application-test.properties        # Конфигурация для тестов (H2)
            ├── kotest.properties                   # Настройки Kotest
            └── schema.sql                         # DDL для тестов
```
