package io.averkhogliad.ai.challenge.week2.bootstrap

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.DefaultLlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmClientConfig
import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.application.cache.CachingInvariantService
import io.averkhogliad.ai.challenge.week2.application.executor.*
import io.averkhogliad.ai.challenge.week2.cli.CliApplication
import io.averkhogliad.ai.challenge.week2.cli.ConsoleCliRenderer
import io.averkhogliad.ai.challenge.week2.domain.config.AppConfig
import io.averkhogliad.ai.challenge.week2.domain.config.LlmConfig
import io.averkhogliad.ai.challenge.week2.domain.service.ConfigPort
import io.averkhogliad.ai.challenge.week2.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.PromptBuilder
import io.averkhogliad.ai.challenge.week2.infrastructure.config.ConfigAdapter
import io.averkhogliad.ai.challenge.week2.infrastructure.llm.LlmAdapter
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.*
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
 * TodoTaskService (Application: CRUD операции с задачами)
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
     * 4. Application: TodoTaskService (оркестрация задач)
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

        // 4. Infrastructure: репозиторий шагов задач (SQLite persistence)
        val taskStepRepository = SqliteTaskStepRepository(dbPath)

        // 5. Infrastructure: репозиторий фактов LTM (SQLite persistence with FTS5)
        val factRepository = SqliteFactRepository(dbPath)

        // 6. Infrastructure: репозиторий профилей (SQLite persistence)
        val profileRepository = SqliteProfileRepository(dbPath)

        // 7. Infrastructure: репозиторий инвариантов (SQLite persistence)
        val invariantRepository = SqliteInvariantRepository(dbPath)

        // 8. Application: TodoTaskService (CRUD операции с задачами)
        val todoTaskService = io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService(taskRepository)

        // 9. Application: MemoryService (управление памятью диалога)
        val memoryService = MemoryService(
            sessionRepository = dialogSessionRepository,
            taskRepository = taskRepository,
            taskStepRepository = taskStepRepository,
            factRepository = factRepository
        )

        // 10. Application: PromptBuilder (формирование контекстного промпта)
        val promptBuilder = PromptBuilder()

        // 11. Application: CachingInvariantService (кэширующий декоратор над InvariantService)
        val cachingInvariantService = CachingInvariantService(invariantRepository)

        // 12. Application: ProfileService (управление профилями)
        val profileService = ProfileService(profileRepository)

        // 13. Application: DialogService (интеграция с LLM)
        val dialogService = DialogService(
            llmPort = llmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            profileRepository = profileRepository,  // передача репозитория профилей для встраивания активного профиля в промпт
            invariantService = cachingInvariantService  // передача кэширующего сервиса инвариантов для встраивания в промпт
        )

        // 14. Application: Task executors (CLI-ассистенты)
        val task1Executor = Task1Executor(dialogService, memoryService)
        val task2Executor = Task2Executor(dialogService, memoryService, profileService)
        val task3Executor = Task3Executor(dialogService, memoryService, profileService)
        val task4Executor = Task4Executor(dialogService, memoryService, profileService)
        val task5Executor = Task5Executor(dialogService, memoryService, profileService)

        // 15. Application: planner components (выделены из PlanCommandHandler)
        val keywordExtractor = io.averkhogliad.ai.challenge.week2.application.planner.KeywordExtractor()
        val factCollector = io.averkhogliad.ai.challenge.week2.application.planner.FactCollector(
            factRepository = factRepository,
            keywordExtractor = keywordExtractor
        )
        val stepParser = io.averkhogliad.ai.challenge.week2.application.planner.StepParser()
        val llmPlanner = if (llmPort != null) {
            io.averkhogliad.ai.challenge.week2.application.planner.LlmPlanner(llmPort)
        } else null

        // 16. Application: CommandEngine (shared FSM engine, must be passed to CliApplication)
        val commandEngine = io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine()

        // 17. Application: PlanCommandHandler (FSM-based планирование)
        val planCommandHandler = io.averkhogliad.ai.challenge.week2.application.handler.PlanCommandHandler(
            taskRepository = taskRepository,
            commandEngine = commandEngine,
            factCollector = factCollector,
            llmPlanner = llmPlanner,
            stepParser = stepParser,
            invariantService = cachingInvariantService
        )

        // 18. Domain: DebugMode (управление debug-режимом)
        val debugMode = io.averkhogliad.ai.challenge.week2.domain.model.DebugMode()

        // 19. Application: DebugCommandHandler (управление debug-режимом через CLI)
        val debugCommandHandler =
            io.averkhogliad.ai.challenge.week2.application.handler.DebugCommandHandler(debugMode)

        // 7. CLI: renderer + facade
        val renderer = ConsoleCliRenderer()

        // 7a. Shutdown hook: закрытие SQLite соединений при завершении JVM
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                invariantRepository.close()
            } catch (_: Exception) {
                // silently ignore close errors during shutdown
            }
        })

        return CliApplication(
            commandEngine = commandEngine,
            executors = mapOf(
                task1Executor.taskId to task1Executor,
                task2Executor.taskId to task2Executor,
                task3Executor.taskId to task3Executor,
                task4Executor.taskId to task4Executor,
                task5Executor.taskId to task5Executor,
            ),
            renderer = renderer,
            todoTaskService = todoTaskService,
            memoryService = memoryService,
            taskStepRepository = taskStepRepository,
            factRepository = factRepository,
            dialogService = dialogService,
            profileRepository = profileRepository,
            planCommandHandler = planCommandHandler,
            debugCommandHandler = debugCommandHandler,
            invariantService = cachingInvariantService,
            invariantRepository = invariantRepository,
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

}
