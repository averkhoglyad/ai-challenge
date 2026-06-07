# AI Challenge

Учебный проект для изучения работы с LLM API через OpenAI-совместимый интерфейс.

## Структура проекта

Проект использует [Gradle](https://gradle.org/) и состоит из двух модулей:

- **`week-0`** — основное приложение с интерактивным меню и учебными задачами
- **`utils`** — общие утилиты: клиент LLM API, система конфигурации, вспомогательные функции

Общая логика сборки вынесена в convention plugin в `buildSrc`.

## Учебные задачи

### Task 1: Простой chat-completion
Минимальная реализация — один запрос, один ответ. Демонстрирует базовое взаимодействие с LLM API.

### Task 2: Расширенный chat-completion с параметрами
Добавляет интерактивный контроль параметров генерации:
- `temperature` — контроль случайности (0.0–2.0)
- `maxTokens` — ограничение длины ответа
- `stop` — стоп-последовательности

### Task 3: Промпт-инжиниринг с модульными модификаторами
Демонстрирует техники промпт-инжиниринга:
- **Zero-shot** — прямой запрос без модификаторов
- **Chain-of-thought** — пошаговое решение (`:step`)
- **Meta-prompting** — генерация оптимального промпта (`:meta`)
- **Role-playing** — установка роли (`:role`)
- **Multi-persona** — группа экспертов (`:mode experts`)
- **Synthesis** — итоговое заключение (`:summary`)

### Task 4: Влияние temperature на генерацию
Демонстрирует, как параметр `temperature` влияет на детерминированность и креативность ответов LLM:
- Выполняет один и тот же запрос с разными значениями temperature
- Показывает статистику использования токенов для каждого ответа
- Автоматически сравнивает результаты и выводит итоговое заключение

Интерактивные команды:
- `:temp <t1,t2,...>` — установить список значений temperature (0.0-2.0)
- `:maxTokens <value>` — установить max tokens для всех запросов
- `:reset` — сбросить параметры к значениям по умолчанию
- `:params` — показать текущую конфигурацию

### Task 5: Сравнение производительности моделей

Сравнивает производительность нескольких LLM моделей на одном и том же запросе:

- Выполняет параллельные запросы к выбранным моделям
- Измеряет время ответа, количество токенов и стоимость
- Выводит итоговое сравнение в виде таблицы
- Даёт рекомендации по выбору модели

Интерактивные команды:

- `:models` — показать список доступных моделей
- `:models <idx1,idx2,...>` — выбрать модели по индексам
- `:maxTokens <value>` — установить лимит токенов
- `:reset` — сбросить параметры к значениям по умолчанию
- `:params` — показать текущую конфигурацию

## Запуск

Используйте Gradle Wrapper (`./gradlew`) для сборки и запуска:

* `./gradlew run` — собрать и запустить приложение
* `./gradlew build` — только сборка
* `./gradlew check` — все проверки, включая тесты
* `./gradlew clean` — очистить артефакты сборки

## Конфигурация

Параметры API задаются в файле `application.properties`:

```properties
api.base-url=https://api.openai.com
api.key=your-api-key
api.model=gpt-4
api.connect-timeout=PT10S
api.request-timeout=PT30S

# Rate limiting (опционально)
api.rate-limit.enabled=true
api.rate-limit.min-interval=PT0.5S
api.rate-limit.max-requests-per-minute=60
# Список моделей для Task 5 (опционально)
# Формат: {id}[:{name}][({costIn},{costOut})]
# Стоимость указывается в ₽ за 1 000 000 (1M) токенов
models=minimax/minimax-m3:Minimax M3(28,114),openai/gpt-4o:GPT-4o(238,955)
```

### Rate Limiting

Клиент поддерживает встроенный rate limiting для защиты от превышения лимитов API:
- **min-interval** — минимальный интервал между последовательными запросами (ISO-8601 Duration)
- **max-requests-per-minute** — максимальное количество запросов в скользящем окне 1 минуты

Rate limiting потокобезопасен и работает корректно при использовании из нескольких корутин.

Файлы конфигурации ищутся в следующем порядке (каждый следующий переопределяет предыдущие):
1. `classpath:application.properties` (в ресурсах)
2. `~/.ai-challenge/application.properties` (user-level)
3. `./application.properties` (project-level)
4. `./config/application.properties` (project config dir)
5. `--config=/path/to/file.properties` (CLI аргумент, высший приоритет)

## Дополнительные ссылки

- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Gradle Tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks)
- [Version Catalog](gradle/libs.versions.toml) — управление зависимостями