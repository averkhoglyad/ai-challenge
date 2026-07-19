package io.averkhogliad.ai.challenge.week2.bootstrap

import io.averkhogliad.ai.challenge.llm.chat.DefaultLlmClient
import io.averkhogliad.ai.challenge.llm.chat.LlmClientConfig
import io.averkhogliad.ai.challenge.llm.config.Config
import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.application.cache.CachingInvariantService
import io.averkhogliad.ai.challenge.week2.application.executor.*
import io.averkhogliad.ai.challenge.week2.cli.*
import io.averkhogliad.ai.challenge.week2.cli.handlers.*
import io.averkhogliad.ai.challenge.week2.domain.ModelId
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.AppConfig
import io.averkhogliad.ai.challenge.week2.domain.config.LlmConfig
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.service.*
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
        val llmPort: LlmPort = try {
            val appConfig: AppConfig = configPort.loadAppConfig()
            createLlmPort(appConfig.llm)
        } catch (_: NoSuchElementException) {
            object : LlmPort {
                override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult =
                    TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")

                override suspend fun chatWithMessages(
                    messages: List<ChatMessage>,
                    config: TaskExecutionConfig
                ): TaskResult =
                    TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")

                override suspend fun listModels(): List<ModelId> = emptyList()
            }
        }

        // 2. Infrastructure: единый владелец SQLite-соединения
        val dbPath = config.getOrNull("app.database.path") ?: SqliteDatabase.defaultDbPath()
        val database = SqliteDatabase(dbPath)

        // 3. Infrastructure: репозитории SQLite используют общее соединение
        val taskRepository = SqliteTaskRepository(database)
        val dialogSessionRepository = SqliteDialogSessionRepository(database)
        val taskStepRepository = SqliteTaskStepRepository(database)
        val factRepository = SqliteFactRepository(database)
        val profileRepository = SqliteProfileRepository(database)
        val invariantRepository = SqliteInvariantRepository(database)


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

        // 12a. Application: LTM and task-step use cases
        val ltmService = io.averkhogliad.ai.challenge.week2.application.service.LtmService(factRepository)
        val taskStepService = io.averkhogliad.ai.challenge.week2.application.service.TaskStepService(
            taskStepRepository = taskStepRepository,
            memoryService = memoryService
        )

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
        val task2Executor = Task2Executor(dialogService)

        val task3Executor = Task3Executor(dialogService)
        val task4Executor = Task4Executor(dialogService)
        val task5Executor = Task5Executor(dialogService)


        // 15. Application: planner components (выделены из PlanCommandHandler)
        val keywordExtractor = io.averkhogliad.ai.challenge.week2.application.planner.KeywordExtractor()
        val factCollector = io.averkhogliad.ai.challenge.week2.application.planner.FactCollector(
            factRepository = factRepository,
            keywordExtractor = keywordExtractor
        )
        val stepParser = io.averkhogliad.ai.challenge.week2.application.planner.StepParser()
        val llmPlanner = io.averkhogliad.ai.challenge.week2.application.planner.LlmPlanner(llmPort)

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

        // 7a. Shutdown hook: закрытие единого SQLite-соединения при завершении JVM
        Runtime.getRuntime().addShutdownHook(Thread {
            try {
                database.close()
            } catch (_: Exception) {
                // silently ignore close errors during shutdown
            }
        })


        val executors = mapOf(
            task1Executor.taskId to task1Executor,
            task2Executor.taskId to task2Executor,
            task3Executor.taskId to task3Executor,
            task4Executor.taskId to task4Executor,
            task5Executor.taskId to task5Executor,
        )
        val input = ConsoleCliInput()
        val commandHandler = CommandHandler(executors)
        val taskStepHandler = TaskStepCommandHandler(
            taskStepService = taskStepService,
            renderer = renderer
        )
        val memoryHandler = MemoryCommandHandler(
            memoryService = memoryService,
            profileRepository = profileRepository,
            debugCommandHandler = debugCommandHandler,
            commandEngine = commandEngine,
            invariantService = cachingInvariantService,
            renderer = renderer
        )
        val ltmHandler = LtmCommandHandler(
            ltmService = ltmService,
            renderer = renderer
        )
        val fsmHandler = FsmCommandHandler(
            commandEngine = commandEngine,
            renderer = renderer,
            readInput = input::readLine
        )

        val invariantHandler = InvariantCommandHandler(
            invariantService = cachingInvariantService,
            renderer = renderer,
            readInput = input::readLine
        )
        val profileHandler = ProfileCommandHandler(
            profileService = profileService,
            renderer = renderer,
            readLine = input::readLine,
            readMultiline = input::readMultiline
        )

        val todoTaskHandler = TodoTaskCommandHandler(
            todoTaskService = todoTaskService,
            memoryService = memoryService,
            renderer = renderer,
            readMultiline = input::readMultiline
        )

        val handlers = CliCommandHandlers(
            command = commandHandler,
            debug = debugCommandHandler,
            todoTask = todoTaskHandler,

            taskStep = taskStepHandler,
            memory = memoryHandler,
            ltm = ltmHandler,
            fsm = fsmHandler,
            invariant = invariantHandler,
            profile = profileHandler,
        )

        val userInputFlowHandler = UserInputFlowHandler(
            renderer = renderer,
            dialogService = dialogService,
            planCommandHandler = planCommandHandler,
            commandEngine = commandEngine,
            commandHandler = commandHandler
        )
        val planFlowHandler = PlanFlowHandler(
            renderer = renderer,
            dialogService = dialogService,
            planCommandHandler = planCommandHandler
        )
        val dispatcher = CliCommandDispatcher(
            renderer = renderer,
            handlers = handlers,
            userInputFlowHandler = userInputFlowHandler,
            planFlowHandler = planFlowHandler
        )

        return CliApplication(
            renderer = renderer,
            input = input,
            dispatcher = dispatcher,
            commandHandler = commandHandler,
            applicationResources = database,
        )




    }

    /**
     * Создаёт [LlmPort] из доменной [LlmConfig].
     *
     * Если конфигурация LLM не заполнена (пустой apiKey), возвращает `null`.
     * DialogService обрабатывает `null` gracefully — возвращает сообщение об ошибке.
     *
     * @param llmConfig доменная конфигурация LLM из [AppConfig.llm]
     * @return [LlmPort]; возвращает no-op заглушку если API-ключ не настроен
     */
    private fun createLlmPort(llmConfig: LlmConfig): LlmPort {
        if (llmConfig.apiKey.isBlank()) {
            return object : LlmPort {
                override suspend fun chat(prompt: Prompt, config: TaskExecutionConfig): TaskResult =
                    TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")

                override suspend fun chatWithMessages(
                    messages: List<ChatMessage>,
                    config: TaskExecutionConfig
                ): TaskResult =
                    TaskResult.Error("LLM не настроен. Добавьте API-ключ в конфигурацию.")

                override suspend fun listModels(): List<ModelId> = emptyList()
            }
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
