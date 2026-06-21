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

## Архитектура

Проект `week-0` построен по принципам **Clean Architecture** с разделением на слои:

| Слой               | Пакет             | Ответственность                                   |
|--------------------|-------------------|---------------------------------------------------|
| **Domain**         | `domain/`         | Бизнес-логика, модели, порты (интерфейсы)         |
| **Infrastructure** | `infrastructure/` | Адаптеры к внешним системам (utils, LLM API)      |
| **Application**    | `application/`    | Use cases (TaskExecutors)                         |
| **CLI**            | `cli/`            | Пользовательский интерфейс (typed commands, REPL) |
| **Bootstrap**      | `bootstrap/`      | Composition root (сборка зависимостей)            |

### Ключевые порты (Hexagonal Architecture)

| Порт                                                                          | Назначение                                | Реализация                                                                                          |
|-------------------------------------------------------------------------------|-------------------------------------------|-----------------------------------------------------------------------------------------------------|
| [`LlmPort`](week-0/src/main/kotlin/domain/service/LlmPort.kt)                 | Абстракция LLM-клиента                    | [`LlmAdapter`](week-0/src/main/kotlin/infrastructure/llm/LlmAdapter.kt)                             |
| [`ConfigPort`](week-0/src/main/kotlin/domain/service/ConfigPort.kt)           | Абстракция конфигурации                   | [`ConfigAdapter`](week-0/src/main/kotlin/infrastructure/config/ConfigAdapter.kt)                    |
| [`ResourceManager`](week-0/src/main/kotlin/domain/service/ResourceManager.kt) | Управление ресурсами (в т.ч. `LlmClient`) | [`LlmClientResourceManager`](week-0/src/main/kotlin/infrastructure/llm/LlmClientResourceManager.kt) |

**Важно:** CLI-слой зависит только от domain-интерфейсов (включая `ResourceManager`) и не импортирует `utils.llm`
напрямую.

Подробности архитектуры см. в [`arch/refactoring-completion-report.md`](arch/refactoring-completion-report.md).

## Дополнительные ссылки

- [Gradle Wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html)
- [Gradle Tasks](https://docs.gradle.org/current/userguide/command_line_interface.html#common_tasks)
- [Version Catalog](gradle/libs.versions.toml) — управление зависимостями

---

## Stateless Strategy API (после рефакторинга)

### Обзор

Все стратегии управления контекстом теперь **stateless** (не имеют внутреннего состояния). Состояние передаётся явно
через параметр `state` и возвращается в `metadata` результата.

### Преимущества

- **Потокобезопасность** — стратегии можно безопасно использовать в многопоточной среде
- **Тестируемость** — легко тестировать, передавая нужное состояние
- **Предсказуемость** — нет скрытых побочных эффектов
- **Гибкость** — можно сериализовать и сохранять состояние между сессиями

### Пример использования

#### Базовый паттерн

```kotlin
// 1. Создаём начальное состояние (или используем null для legacy behavior)
var strategyState: StrategyState? = null

// 2. Обрабатываем сообщение пользователя
val actionResult = strategy.processUserMessage(
    dialog = dialog,
    userMessage = "Привет, меня зовут Alice",
    config = config,
    state = strategyState
)

// 3. Извлекаем обновлённое состояние из metadata
strategyState = actionResult.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState

// 4. Подготавливаем контекст для LLM
val preparedContext = strategy.prepareContext(
    dialog = dialog,
    systemPrompt = "You are a helpful assistant",
    config = config,
    state = strategyState
)

// 5. Извлекаем обновлённое состояние (если prepareContext его обновил)
strategyState = preparedContext.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState

// 6. Отправляем контекст в LLM
val response = llmClient.chat(preparedContext.messages)
```

#### Пример с BranchingStrategy

```kotlin
val branchingStrategy = BranchingStrategy(
    configProvider = { BranchingConfig(autoDetectTopicChange = true) },
    topicChangeDetector = TopicChangeDetector()
)

// Создаём начальное состояние для диалога
var state: StrategyState? = StrategyState.BranchingState.createInitial(dialog.id)

// Обрабатываем серию сообщений
for (userMessage in userMessages) {
    val result = branchingStrategy.processUserMessage(
        dialog = dialog,
        userMessage = userMessage,
        config = config,
        state = state
    )

    // Проверяем, какие действия были выполнены
    result.actionsPerformed.forEach { action ->
        when (action) {
            is StrategyAction.CheckpointCreated -> println("Создан чекпоинт: ${action.checkpointId}")
            is StrategyAction.BranchCreated -> println("Создана ветка: ${action.branchName}")
            is StrategyAction.BranchSwitched -> println("Переключение на ветку: ${action.branchName}")
            else -> {}
        }
    }

    // Обновляем состояние
    state = result.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState
}

// Получаем список всех веток
val branchingState = state as StrategyState.BranchingState
println("Всего веток: ${branchingState.branches.size}")
println("Текущая ветка: ${branchingState.currentBranch.name}")
```

#### Пример с StickyFactsStrategy

```kotlin
val stickyFactsStrategy = StickyFactsStrategy(
    factsExtractor = FactsExtractor(llmPort)
)

var state: StrategyState? = StrategyState.StickyFactsState.createInitial()

// Обрабатываем сообщение — факты извлекаются автоматически
val result = stickyFactsStrategy.processUserMessage(
    dialog = dialog,
    userMessage = "Меня зовут Alice, я живу в Москве",
    config = config,
    state = state
)

state = result.metadata[StrategyMetadataKeys.STRATEGY_STATE] as? StrategyState

// Подготавливаем контекст — факты включаются в system prompt
val context = stickyFactsStrategy.prepareContext(
    dialog = dialog,
    systemPrompt = "You are a helpful assistant",
    config = config,
    state = state
)

// Проверяем извлечённые факты
val stickyState = state as StrategyState.StickyFactsState
println("Извлечено фактов: ${stickyState.factsStore.facts.size}")
stickyState.factsStore.facts.forEach { (key, fact) ->
    println("  $key: ${fact.value}")
}
```

#### Обратная совместимость (legacy behavior)

Если передать `state = null`, стратегия будет использовать внутреннее состояние (как до рефакторинга):

```kotlin
// Legacy mode — состояние хранится внутри стратегии
val result = strategy.processUserMessage(
    dialog = dialog,
    userMessage = "Сообщение",
    config = config,
    state = null  // Стратегия использует внутреннее состояние
)
```

**Важно:** Legacy mode не рекомендуется для нового кода, так как нарушает потокобезопасность.

### Типы состояний

| Стратегия               | Тип состояния                      | Описание                                   |
|-------------------------|------------------------------------|--------------------------------------------|
| `SlidingWindowStrategy` | `StrategyState.SlidingWindowState` | Пустой объект (стратегия stateless)        |
| `StickyFactsStrategy`   | `StrategyState.StickyFactsState`   | Хранит `FactsStore` с извлечёнными фактами |
| `BranchingStrategy`     | `StrategyState.BranchingState`     | Хранит ветки, чекпоинты, текущую ветку     |

### Metadata keys

Все ключи metadata определены в [
`StrategyMetadataKeys`](week-1/src/main/kotlin/domain/strategy/StrategyMetadataKeys.kt):

```kotlin
object StrategyMetadataKeys {
    const val STRATEGY = "strategy"
    const val WINDOW_SIZE = "windowSize"
    const val BLOCK_SIZE = "blockSize"
    const val COMPRESSED_MESSAGE_COUNT = "compressedMessageCount"
    const val NEW_ACCUMULATED_SUMMARY = "newAccumulatedSummary"
    const val CURRENT_BRANCH = "currentBranch"
    const val TOTAL_BRANCHES = "totalBranches"
    const val TOTAL_CHECKPOINTS = "totalCheckpoints"
    const val BRANCH_MESSAGE_COUNT = "branchMessageCount"
    const val FACTS_COUNT = "factsCount"
    const val FACTS = "facts"
    const val EXTRACTED_FACTS = "extractedFacts"
    const val STRATEGY_STATE = "strategyState"  // Ключ для извлечения обновлённого состояния
}
```

### Конфигурация таймаутов

Таймауты LLM-вызовов внутри стратегий теперь конфигурируются через [
`TimeoutsConfig`](week-1/src/main/kotlin/domain/strategy/ContextManagementConfig.kt) в составе
`ContextManagementConfig`:

```kotlin
val config = ContextManagementConfig(
    timeouts = TimeoutsConfig(
        factExtractionTimeoutMs = 30_000L,  // Таймаут извлечения фактов
        compressionTimeoutMs = 30_000L       // Таймаут компрессии контекста
    )
)

// Передача config в стратегию
val result = strategy.processUserMessage(dialog, userMessage, config, state)
val context = strategy.prepareContext(dialog, systemPrompt, config, state)
```

Через `application.properties`:

```properties
context.strategy.timeouts.fact-extraction-ms=30000
context.strategy.timeouts.compression-ms=30000
```