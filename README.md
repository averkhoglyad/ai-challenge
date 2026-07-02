# AI Challenge

Учебный проект для изучения работы с LLM API через OpenAI-совместимый интерфейс.

## Структура проекта

Проект использует [Gradle](https://gradle.org/) и собран как multi-module build.

### Учебные модули

- **`week-0`** — базовое CLI-приложение: интерактивное меню, работа с LLM API, учебные задачи 1–5
- **`week-1`** — управление диалогами и стратегиями контекста (`SlidingWindow`, `StickyFacts`, `Branching`)
- **`week-2`** — FSM-планирование, инварианты, профили, память и todo-менеджер
- **`week-3:cli`** — актуальный пользовательский CLI для задач, шагов, памяти, MCP, событий и уведомлений
- **`week-3:events-server`** — сервер календарных событий с REST API и MCP-интерфейсом
- **`week-3:weather-server`** — погодный сервис с REST API, кэшированием и MCP-интерфейсом
- **`week-3:notification-server`** — сервис уведомлений, который использует CLI и интеграции week-3

### Общие модули

- **`common-core`** — переиспользуемое ядро: конфигурация, LLM-клиент, feature flags и общие test fixtures
- **`common-test`** — общая тестовая инфраструктура: Kotest, MockK, Spring extensions, SQL-утилиты и matchers

Общая логика сборки вынесена в convention plugin в `buildSrc`. Новые Spring/Kotlin модули week-3 опираются на
`common-core` и `common-test`, чтобы не дублировать инфраструктурный код.

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

Используйте Gradle Wrapper (`./gradlew`) для сборки и запуска.

### Базовые команды

* `./gradlew build` — собрать все модули проекта
* `./gradlew check` — все проверки, включая тесты
* `./gradlew clean` — очистить артефакты сборки

### Актуальные entry points week-3

* `./gradlew :week-3:cli:run` — запустить CLI-приложение week-3
* `./gradlew :week-3:events-server:bootRun` — запустить Events Server
* `./gradlew :week-3:weather-server:bootRun` — запустить Weather Server

Для отдельных модулей также доступны `test`, `integrationTest` и `build` в стандартном Gradle-формате.

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

Подробности архитектуры см. в отчётах в директории [`docs/reports/`](docs/reports/).

## Модель памяти

Проект `week-2` расширяет архитектуру, добавляя слой **Profile Memory (PM)** — четвёртый компонент модели памяти
диалоговой системы:

| Слой    | Название           | Описание                                               | Область действия            |
|---------|--------------------|--------------------------------------------------------|-----------------------------|
| **STM** | Short-Term Memory  | История текущего диалога                               | Один диалог                 |
| **WM**  | Working Memory     | Текущий контекст работы (активные факты, ветки)        | Один диалог                 |
| **LTM** | Long-Term Memory   | База знаний (sticky facts, извлечённые факты)          | Все диалоги                 |
| **PM**  | **Profile Memory** | Профиль пользователя — стиль, роль, инструкции для LLM | Глобален, инжектится всегда |

**PM (Profile Memory)** — глобальный слой, определяющий **стиль ответов LLM**. Профиль содержит текстовое описание (
system prompt), которое автоматически добавляется в каждый запрос к LLM, независимо от активного диалога или задачи.
Пользователь может создавать несколько профилей, переключаться между ними, редактировать и удалять.

## Команды профилей

В актуальном CLI (`week-3:cli`) используются следующие команды управления профилями:

| Команда           | Синтаксис                | Описание                                                                                                  |
|-------------------|--------------------------|-----------------------------------------------------------------------------------------------------------|
| `:profile-new`    | `:profile-new <name>`    | Создать новый профиль с указанным именем. После ввода команды открывается многострочный ввод содержимого. |
| `:profile-list`   | `:profile-list`          | Показать список всех профилей. Активный профиль отмечен символом `*`.                                     |
| `:profile-use`    | `:profile-use <name>`    | Активировать профиль по имени. `:profile-use none` деактивирует текущий профиль.                          |
| `:profile-edit`   | `:profile-edit <name>`   | Редактировать содержимое существующего профиля.                                                           |
| `:profile-delete` | `:profile-delete <name>` | Удалить профиль по имени.                                                                                 |
| `:profile-show`   | `:profile-show [name]`   | Показать содержимое профиля. Если имя не указано, показывается активный профиль.                          |

Команда `:status` в `week-3:cli` отображает имя активного профиля, а при наличии активной FSM-команды — и её текущее
состояние.

## Команды управления задачами (FSM)

Week-2 task-3 добавляет систему формализованного состояния команд с использованием паттерна FSM (Finite State Machine).
Это позволяет реализовать многошаговые команды с явным управлением состоянием.

### Команда `:plan`

Команда `:plan` запускает многошаговый процесс планирования задачи:

```
> :plan Создать REST API для управления пользователями
Шаг 1/3: Введите описание задачи
> Задача описана
Шаг 2/3: Введите критерии приёмки
> Критерии указаны
Шаг 3/3: Подтвердите план (да/нет)
> да
План создан и сохранён
```

**Состояния FSM:**

- `PLANNING` — сбор информации о задаче (описание, критерии)
- `EXECUTION` — выполнение плана
- `VALIDATION` — проверка результатов
- `DONE` — завершение
- `TERMINATED` — принудительное завершение (через `:abort` или `:goto TERMINATED`)

### Команда `:goto` (граф состояний)

Команда `:goto` позволяет просматривать и управлять графом состояний FSM:

```
# Просмотр всех состояний и доступных переходов
> :goto

Карта состояний команды :plan:
  ● PLANNING (текущее)
  → EXECUTION (описание заполнено)
    DESCRIPTION: Начать выполнение плана
  ○ EXECUTION
    REASON: Переход на этап выполнения
    ⚠ REQUIRES: generatedSteps
  ○ DONE
    REASON: Пропуск этапов — требуется сначала пройти EXECUTION
    ⚠ BLOCKED: пропуск этапов
```

**Переход в указанное состояние:**

```
# Допустимый переход
> :goto EXECUTION
[OK] Переход PLANNING → EXECUTION выполнен.

# Недопустимый переход (пропуск этапа)
> :goto DONE
[ОШИБКА] Переход EXECUTION → DONE недопустим: пропуск этапа VALIDATION.
Доступные переходы:
  → VALIDATION (шаги сгенерированы)
  → PLANNING (откат)

# Переход в текущее состояние
> :goto PLANNING
[ОШИБКА] Вы уже находитесь в состоянии PLANNING.
```

#### Граф переходов для команды `:plan`

| Переход                  | Описание               | Предусловие                 |
|--------------------------|------------------------|-----------------------------|
| `PLANNING → EXECUTION`   | Начать выполнение      | `needsDescription ≠ "true"` |
| `EXECUTION → VALIDATION` | Проверить результаты   | `generatedSteps` не пуст    |
| `EXECUTION → PLANNING`   | Откат к планированию   | Всегда доступен             |
| `VALIDATION → DONE`      | Завершить команду      | `generatedSteps` не пуст    |
| `VALIDATION → EXECUTION` | Вернуться к выполнению | Всегда доступен (edit)      |
| `DONE → TERMINATED`      | Финализировать         | Всегда доступен             |

#### Сценарии отката

При ошибке на этапе EXECUTION:

```
> Ошибка выполнения: LLM API timeout
Команда остановлена. Доступные действия:
  :goto PLANNING — откатиться и повторить
  :goto         — посмотреть карту состояний
  :abort        — прервать команду

> :goto PLANNING
[OK] Переход EXECUTION → PLANNING выполнен.
Контекст сохранён: описание, taskId, промежуточные данные.
```

### Команда `:state`

Показывает текущее состояние активной FSM-команды:

```
> :state
Active command: plan
Stage: PLANNING
Step: 2/3
Expected action: Введите критерии приёмки
Context:
  description: Создать REST API для управления пользователями
```

Если нет активной команды:

```
> :state
No active command
```

### Команда `:abort`

Прерывает активную FSM-команду и сбрасывает её состояние:

```
> :abort
Command 'plan' aborted
```

Если нет активной команды:

```
> :abort
No active command to abort
```

### Команда `:status` (обновлена)

Теперь отображает дополнительную информацию об активной FSM-команде:

```
> :status
Active dialog: dialog-1
Messages in dialog: 12
Active profile: Эксперт (prof-1)
Active command: plan
Debug mode: disabled
```

### Архитектура FSM

**Ключевые компоненты:**

| Компонент              | Файл                                                                                        | Ответственность                                                                             |
|------------------------|---------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------|
| `CommandEngine`        | [`CommandEngine.kt`](week-2/src/main/kotlin/domain/service/CommandEngine.kt)                | Интерфейс для управления состоянием FSM                                                     |
| `DefaultCommandEngine` | [`DefaultCommandEngine.kt`](week-2/src/main/kotlin/application/DefaultCommandEngine.kt)     | Реализация FSM с поддержкой `hasActiveCommand()`, `getActiveState()`, `abortCommand()`      |
| `CommandState`         | [`CommandState.kt`](week-2/src/main/kotlin/domain/model/CommandState.kt)                    | Модель состояния: `commandName`, `currentStage`, `currentStep`, `expectedAction`, `context` |
| `CommandStage`         | [`CommandStage.kt`](week-2/src/main/kotlin/domain/model/CommandStage.kt)                    | Enum: `PLANNING`, `EXECUTION`, `VALIDATION`, `DONE`, `TERMINATED`                           |
| `PlanCommandHandler`   | [`PlanCommandHandler.kt`](week-2/src/main/kotlin/application/handler/PlanCommandHandler.kt) | FSM-оркестратор команды `:plan` (сбор фактов, LLM-планирование, валидация)                  |
| `TransitionValidator`  | [`TransitionValidator.kt`](week-2/src/main/kotlin/domain/service/TransitionValidator.kt)    | Валидация переходов между состояниями FSM                                                   |

**Принципы работы:**

1. Команда создаёт `CommandState` при начале выполнения
2. Состояние хранится в `CommandEngine` (in-memory)
3. Каждый шаг обновляет `currentStage`, `currentStep`, `expectedAction`
4. Команда `:state` читает состояние через `getActiveState()`
5. Команда `:abort` вызывает `abortCommand()` для сброса
6. После завершения (`DONE`) состояние автоматически очищается

### Примеры использования

#### Создание профиля

```
> :profile-create Эксперт
Введите содержимое профиля (пустая строка для завершения):
Ты — опытный Java-разработчик с 15-летним стажем.
Отвечай кратко, по делу, с примерами кода.
Используй терминологию из Spring и Kotlin.

Профиль "Эксперт" создан (ID: prof-1)
```

#### Переключение между профилями

```
> :profile-create Друг
Введите содержимое профиля (пустая строка для завершения):
Ты — дружелюбный собеседник. Общайся неформально, используй смайлики.
Отвечай с юмором и эмпатией.

Профиль "Друг" создан (ID: prof-2)

> :profile-list
Доступные профили:
* [prof-1] Эксперт
  [prof-2] Друг

> :profile-activate prof-2
Профиль "Друг" активирован

> :profile-list
Доступные профили:
  [prof-1] Эксперт
* [prof-2] Друг
```

#### Просмотр статуса с активным профилем

```
> :status
Активный диалог: dialog-1
Сообщений в диалоге: 12
Активный профиль: Друг (prof-2)
```

### Сообщения об ошибках

| Ошибка                                    | Причина                                                          | Решение                                                 |
|-------------------------------------------|------------------------------------------------------------------|---------------------------------------------------------|
| `Профиль с именем "X" уже существует`     | Попытка создать профиль с дублирующимся именем                   | Используйте другое имя или удалите существующий профиль |
| `Профиль с ID "X" не найден`              | Указан несуществующий ID при активации                           | Проверьте список профилей командой `:profile-list`      |
| `Профиль с именем "X" не найден`          | Указано несуществующее имя при редактировании/удалении/просмотре | Проверьте правильность имени через `:profile-list`      |
| `Не указано имя профиля`                  | Команда `:profile-create` вызвана без аргумента                  | Укажите имя: `:profile-create <name>`                   |
| `Содержимое профиля не может быть пустым` | При создании/редактировании введено пустое содержимое            | Введите хотя бы одну строку описания профиля            |

## Команды управления инвариантами

Инварианты — это правила, которые ограничивают поведение LLM и применяются ко всем запросам.

| Команда             | Синтаксис                | Описание                               |
|---------------------|--------------------------|----------------------------------------|
| `:invariant add`    | `:invariant add <text>`  | Добавить новый инвариант.              |
| `:invariant list`   | `:invariant list`        | Показать список всех инвариантов с ID. |
| `:invariant remove` | `:invariant remove <id>` | Удалить инвариант по ID.               |

**Пример использования:**

```
> :invariant add Отвечай только на русском языке
Инвариант добавлен (ID: inv-1)

> :invariant list
Инварианты (1):
  [inv-1] Отвечай только на русском языке

> :invariant remove 1
Инвариант удалён
```

## Команды управления задачами (Todo Manager)

`week-3:cli` содержит полноценный todo-менеджер с контекстно-зависимыми командами.

| Команда   | Синтаксис            | Описание                                                                |
|-----------|----------------------|-------------------------------------------------------------------------|
| `:add`    | `:add <title>`       | Добавить новую задачу с указанным заголовком.                           |
| `:tasks`  | `:tasks`             | Показать список всех задач. Активная задача отмечена символом `*`.      |
| `:open`   | `:open [id]`         | Открыть задачу. Если ID не указан, открывается первая доступная задача. |
| `:close`  | `:close [id]`        | Закрыть задачу. Если ID не указан, закрывается активная задача.         |
| `:cancel` | `:cancel [id]`       | Отменить задачу. Если ID не указан, отменяется активная задача.         |
| `:drop`   | `:drop [id]`         | Удалить задачу. Если ID не указан, удаляется текущая задача.            |
| `:edit`   | `:edit [id] <title>` | Редактировать задачу. Без ID редактируется текущая задача.              |

**Пример использования:**

```
> :add-task Изучить Kotlin Coroutines
Задача добавлена (ID: task-1)

> :list-tasks
Задачи:
* [task-1] Изучить Kotlin Coroutines (OPEN)
  [task-2] Написать тесты (OPEN)

> :open-task task-2
Задача "Написать тесты" открыта

> :close-task
Задача "Написать тесты" закрыта
```

## Команды управления шагами (Steps)

Для активной задачи можно управлять шагами выполнения.

| Команда      | Синтаксис          | Описание                               |
|--------------|--------------------|----------------------------------------|
| `:step-add`  | `:step-add <text>` | Добавить шаг к активной задаче.        |
| `:step-list` | `:step-list`       | Показать список шагов активной задачи. |
| `:step-done` | `:step-done <id>`  | Отметить шаг как выполненный.          |

**Пример использования:**

```
> :add-step Прочитать документацию по Flow
Шаг добавлен (ID: step-1)

> :list-steps
Шаги задачи "Изучить Kotlin Coroutines":
  [step-1] Прочитать документацию по Flow (PENDING)
  [step-2] Написать примеры кода (PENDING)

> :complete-step step-1
Шаг отмечен как выполненный
```

## Команды долгосрочной памяти (LTM)

Управление фактами в долгосрочной памяти (Long-Term Memory).

| Команда       | Синтаксис             | Описание                                 |
|---------------|-----------------------|------------------------------------------|
| `:ctx-save`   | `:ctx-save <text>`    | Сохранить факт в LTM.                    |
| `:ctx-list`   | `:ctx-list`           | Показать список всех сохранённых фактов. |
| `:ctx-forget` | `:ctx-forget <id>`    | Удалить факт из LTM по его ID.           |
| `:ctx-search` | `:ctx-search <query>` | Найти факты в LTM по подстроке.          |

**Пример использования:**

```
> :save-fact Пользователь предпочитает Kotlin
Факт сохранён (ID: fact-1)

> :list-facts
Факты (1):
  [fact-1] Пользователь предпочитает Kotlin

> :forget-fact fact-1
Факт удалён
```

## Команда отладки (Debug Mode)

| Команда  | Синтаксис | Описание                                                                                                                                         |
|----------|-----------|--------------------------------------------------------------------------------------------------------------------------------------------------|
| `:debug` | `:debug`  | Переключить режим отладки. В режиме отладки отображается дополнительная информация о запросах к LLM, используемых промптах и времени выполнения. |

**Пример использования:**

```
> :debug
Debug mode: enabled

> :debug
Debug mode: disabled
```

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