package io.averkhogliad.ai.challenge.week2.bootstrap

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.DefaultLlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmClientConfig
import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.application.executor.TaskManagerExecutor
import io.averkhogliad.ai.challenge.week2.cli.CliApplication
import io.averkhogliad.ai.challenge.week2.cli.ConsoleCliRenderer
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskId
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.AppConfig
import io.averkhogliad.ai.challenge.week2.domain.config.LlmConfig
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.service.ConfigPort
import io.averkhogliad.ai.challenge.week2.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.PromptBuilder
import io.averkhogliad.ai.challenge.week2.infrastructure.config.ConfigAdapter
import io.averkhogliad.ai.challenge.week2.infrastructure.llm.LlmAdapter
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteDialogSessionRepository
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteFactRepository
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteTaskRepository
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteTaskStepRepository
import kotlin.time.Duration.Companion.seconds

/**
 * Composition root приложения AI Challenge Week 2.
 *
 * ## Архитектурная роль
 *
 * [ApplicationBootstrap] — это **composition root** (точка сборки), где:
 * 1. Создаются все инфраструктурные компоненты (adapters)
 * 2. Создаются application сервисы и executor'ы
 * 3. Создаются CLI-компоненты (renderer)
 * 4. Собирается и возвращается [CliApplication]
 *
 * ## Почему composition root?
 *
 * - **Единственное место сборки**: все зависимости создаются здесь, а не разбросаны
 *   по конструкторам через service locator или reflection
 * - **Порядок создания гарантирован**: infrastructure → application services/executors → CLI
 * - **Нет бизнес-логики**: bootstrap только создаёт объекты и связывает их,
 *   не содержит условий, валидации или алгоритмов
 * - **Зависимость infrastructure от domain**: адаптеры реализуют принцип инверсии зависимостей (DIP)
 *
 * ## Порядок создания (обоснование)
 *
 * ```
 * ConfigAdapter → AppConfig
 *     ↓
 * SqliteTaskRepository (Infrastructure: task persistence)
 *     ↓
 * SqliteDialogSessionRepository (Infrastructure: dialog persistence)
 *     ↓
 * TaskManagerExecutor (Application: оркестрация задач)
 *     ↓
 * MemoryService (Application: управление памятью диалога)
 *     ↓
 * CLI Renderer + CliApplication
 * ```
 *
 * Порядок строится от наиболее низкоуровневых (infrastructure) к наиболее
 * высокоуровневым (CLI/application). Это гарантирует, что каждая зависимость
 * уже существует к моменту её внедрения.
 */
object ApplicationBootstrap {

    /**
     * Создаёт [CliApplication] на основе конфигурации.
     *
     * @param config загруженная конфигурация ([Config] из utils)
     * @return полностью инициализированный [CliApplication]
     */
    fun createApplication(config: Config): CliApplication {
        return createCliApplication(config)
    }

    // ──── Private construction ────

    /**
     * Собирает полностью инициализированное CLI-приложение.
     *
     * Шаги:
     * 1. Infrastructure: ConfigAdapter → AppConfig
     * 2. Infrastructure: SqliteTaskRepository (persistence)
     * 3. Infrastructure: SqliteDialogSessionRepository (dialog persistence)
     * 4. Application: TaskManagerExecutor (оркестрация задач)
     * 5. Application: MemoryService (управление памятью диалога)
     * 6. CLI: renderer + application
     */
    private fun createCliApplication(config: Config): CliApplication {
        // 1. Infrastructure: конфигурация
        val configPort: ConfigPort = ConfigAdapter(config)

        // 1a. Infrastructure: загрузка AppConfig и создание LLM-клиента (опционально)
        val llmPort: LlmPort? = try {
            val appConfig: AppConfig = configPort.loadAppConfig()
            createLlmPort(appConfig.llm)
        } catch (_: NoSuchElementException) {
            null  // LLM не настроен — DialogService работает в режиме offline
        }

        // 2. Infrastructure: репозиторий задач (SQLite persistence)
        val dbPath = config.getOrNull("app.database.path") ?: SqliteTaskRepository.defaultDbPath()
        val taskRepository = SqliteTaskRepository(dbPath)

        // 3. Infrastructure: репозиторий сессий диалога (SQLite persistence)
        val dialogSessionRepository = SqliteDialogSessionRepository(dbPath)

        // 3a. Infrastructure: репозиторий шагов задач (SQLite persistence)
        val taskStepRepository = SqliteTaskStepRepository(dbPath)

        // 4. Application: TaskManagerExecutor (оркестрация задач)
        val taskManagerExecutor = TaskManagerExecutor(taskRepository)

        // 3b. Infrastructure: репозиторий фактов LTM (SQLite persistence with FTS5)
        val factRepository = SqliteFactRepository(dbPath)

        // 5. Application: MemoryService (управление памятью диалога)
        val memoryService = MemoryService(
            sessionRepository = dialogSessionRepository,
            taskRepository = taskRepository,
            taskStepRepository = taskStepRepository,
            factRepository = factRepository
        )

        // 5a. Application: PromptBuilder (формирование контекстного промпта)
        val promptBuilder = PromptBuilder()

        // 5b. Application: DialogService (интеграция с LLM)
        val dialogService = DialogService(
            llmPort = llmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder
        )

        // 6. Application: Task 1 executor (CLI-ассистент)
        val task1Executor = createTask1Executor(dialogService, memoryService)

        // 7. CLI: renderer + facade
        val renderer = ConsoleCliRenderer()

        return CliApplication(
            executors = mapOf(task1Executor.taskId to task1Executor),
            renderer = renderer,
            taskManagerExecutor = taskManagerExecutor,
            memoryService = memoryService,
            taskStepRepository = taskStepRepository,
            factRepository = factRepository,
            dialogService = dialogService
        )
    }

    /**
     * Создаёт [LlmPort] из доменной [LlmConfig].
     *
     * Если конфигурация LLM не заполнена (пустой apiKey), возвращает `null`.
     * DialogService обрабатывает `null` gracefully — возвращает сообщение об ошибке.
     *
     * @param llmConfig доменная конфигурация LLM из [AppConfig.llm]
     * @return [LlmPort] или `null` если API-ключ не настроен
     */
    private fun createLlmPort(llmConfig: LlmConfig): LlmPort? {
        if (llmConfig.apiKey.isBlank()) {
            return null
        }

        val clientConfig = LlmClientConfig(
            baseUrl = llmConfig.baseUrl,
            apiKey = llmConfig.apiKey,
            model = llmConfig.defaultModelId.value,
            connectTimeout = llmConfig.timeoutSeconds.seconds,
            requestTimeout = llmConfig.timeoutSeconds.seconds,
            rateLimitEnabled = true,
            minInterval = 0.5.seconds,
            maxRequestsPerMinute = 60
        )

        val llmClient = DefaultLlmClient(clientConfig)

        return LlmAdapter(
            llmClient = llmClient,
            defaultModelId = llmConfig.defaultModelId,
            availableModels = listOf(llmConfig.defaultModelId)
        )
    }

    /**
     * Создаёт executor для Задачи 1 (CLI-ассистент).
     *
     * Task 1 — это основная задача приложения: общение с ассистентом
     * через трёхуровневую модель памяти (STM/WM/LTM).
     */
    private fun createTask1Executor(
        dialogService: DialogService,
        memoryService: MemoryService
    ): TaskExecutor {
        return object : TaskExecutor {
            override val taskId = TaskId(1)
            override val metadata = TaskMetadata(
                id = taskId,
                title = "Task 1: CLI-ассистент",
                description = "Диалоговый ассистент с трёхуровневой моделью памяти (STM/WM/LTM). " +
                        "Поддерживает управление задачами, шагами, фактами и общение с LLM.",
                availableCommands = listOf(
                    ":add <text>", ":list", ":edit <id> <text>", ":drop <id>",
                    ":open <id>", ":close", ":cancel", ":back",
                    ":step-add <text>", ":step-list", ":step-done <id>",
                    ":ctx-save <text>", ":ctx-list", ":ctx-forget <id>",
                    ":plan <title>", ":status", ":clear",
                    "temp <value>", "maxtokens <n>", "params"
                )
            )

            override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
                // Делегируем в DialogService.chat() — основную точку входа для общения
                return dialogService.chat(
                    userInput = prompt.value,
                    level = io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel.TASK_LIST,
                    taskId = null
                )
            }
        }
    }
}
