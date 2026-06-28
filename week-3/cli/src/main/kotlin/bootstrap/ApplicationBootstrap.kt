package io.averkhogliad.ai.challenge.week3.cli.bootstrap

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.DefaultLlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmClientConfig
import io.averkhogliad.ai.challenge.week3.cli.application.DialogService
import io.averkhogliad.ai.challenge.week3.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week3.cli.application.cache.CachingInvariantService
import io.averkhogliad.ai.challenge.week3.cli.application.executor.Task1Executor
import io.averkhogliad.ai.challenge.week3.cli.application.executor.Task3Executor
import io.averkhogliad.ai.challenge.week3.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week3.cli.application.usecase.CreateEventForTaskUseCase
import io.averkhogliad.ai.challenge.week3.cli.application.usecase.ListNotesUseCase
import io.averkhogliad.ai.challenge.week3.cli.cli.*
import io.averkhogliad.ai.challenge.week3.cli.cli.handlers.*
import io.averkhogliad.ai.challenge.week3.cli.cli.renderers.MCPRenderer
import io.averkhogliad.ai.challenge.week3.cli.domain.config.AppConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.config.LlmConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.config.ServicesConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ConfigPort
import io.averkhogliad.ai.challenge.week3.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week3.cli.domain.service.PromptBuilder
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.client.RestEventsClient
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.client.RestNotificationsClient
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.config.ConfigAdapter
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.llm.LlmAdapter
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.mcp.DefaultMCPConnectionManager
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.time.Instant
import java.time.LocalDate
import java.util.*
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
// Contextual serializers for java.time.* and java.util.UUID
// Required because DTOs use @Contextual annotation on these types.
private object JavaTimeSerializers {
    val module = SerializersModule {
        contextual(LocalDateSerializer)
        contextual(InstantSerializer)
        contextual(UuidSerializer)
    }
}

private object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

private object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

private object UuidSerializer : KSerializer<UUID> {
    override val descriptor = PrimitiveSerialDescriptor("UUID", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: UUID) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): UUID = UUID.fromString(decoder.decodeString())
}

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
        val todoTaskService = io.averkhogliad.ai.challenge.week3.cli.application.service.TodoTaskService(taskRepository)

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

        // 12b. Infrastructure: MCP server repository and connection manager
        val mcpServerRepository = SqliteMCPServerRepository(database)
        val mcpConnectionManager = DefaultMCPConnectionManager(mcpServerRepository)

        // 12c. Application: MCPService (управление MCP-серверами)
        val mcpService = MCPService(mcpServerRepository, mcpConnectionManager)

        // 12a. Application: LTM and task-step use cases
        val ltmService = io.averkhogliad.ai.challenge.week3.cli.application.service.LtmService(factRepository)
        val taskStepService = io.averkhogliad.ai.challenge.week3.cli.application.service.TaskStepService(
            taskStepRepository = taskStepRepository,
            memoryService = memoryService
        )

        // 13. Application: DialogService (интеграция с LLM)
        val dialogService = DialogService(
            llmPort = llmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            profileRepository = profileRepository,  // передача репозитория профилей для встраивания активного профиля в промпт
            invariantService = cachingInvariantService,  // передача кэширующего сервиса инвариантов для встраивания в промпт
            mcpService = mcpService  // передача MCP-сервиса для получения инструментов
        )

        // 14. Application: Task executor (CLI-ассистент с FSM)
        val task1Executor = Task1Executor(dialogService)

        // 14b. Application: Task3 executor (Календарь событий и уведомления)
        val task3Executor = Task3Executor(dialogService)


        // 15. Application: planner components (выделены из PlanCommandHandler)
        val keywordExtractor = io.averkhogliad.ai.challenge.week3.cli.application.planner.KeywordExtractor()
        val factCollector = io.averkhogliad.ai.challenge.week3.cli.application.planner.FactCollector(
            factRepository = factRepository,
            keywordExtractor = keywordExtractor
        )
        val stepParser = io.averkhogliad.ai.challenge.week3.cli.application.planner.StepParser()
        val llmPlanner = if (llmPort != null) {
            io.averkhogliad.ai.challenge.week3.cli.application.planner.LlmPlanner(llmPort)
        } else null

        // 16. Application: CommandEngine (shared FSM engine, must be passed to CliApplication)
        val commandEngine = io.averkhogliad.ai.challenge.week3.cli.application.DefaultCommandEngine()

        // 17. Application: PlanCommandHandler (FSM-based планирование)
        val planCommandHandler = io.averkhogliad.ai.challenge.week3.cli.application.handler.PlanCommandHandler(
            taskRepository = taskRepository,
            commandEngine = commandEngine,
            factCollector = factCollector,
            llmPlanner = llmPlanner,
            stepParser = stepParser,
            invariantService = cachingInvariantService
        )

        // 18. Domain: DebugMode (управление debug-режимом)
        val debugMode = io.averkhogliad.ai.challenge.week3.cli.domain.model.DebugMode()

        // 19. Application: DebugCommandHandler (управление debug-режимом через CLI)
        val debugCommandHandler =
            io.averkhogliad.ai.challenge.week3.cli.application.handler.DebugCommandHandler(debugMode)

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
            task3Executor.taskId to task3Executor,
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

        val mcpRenderer = MCPRenderer()
        val mcpHandler = MCPCommandHandler(
            mcpService = mcpService,
            renderer = mcpRenderer,
            readLine = input::readLine
        )

        val todoTaskHandler = TodoTaskCommandHandler(
            todoTaskService = todoTaskService,
            memoryService = memoryService,
            renderer = renderer,
            readMultiline = input::readMultiline
        )

        // Wave 4 / Task3: Events + Notifications clients and use cases
        val servicesConfig = ServicesConfig(
            eventsBaseUrl = config.getOrNull("services.events.base-url") ?: "http://localhost:8081",
            notificationsBaseUrl = config.getOrNull("services.notifications.base-url") ?: "http://localhost:8083"
        )
        val httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    serializersModule = JavaTimeSerializers.module
                })
            }
        }
        val eventsClient = RestEventsClient(servicesConfig, httpClient)
        val notificationsClient = RestNotificationsClient(servicesConfig, httpClient)
        val createEventUseCase = CreateEventForTaskUseCase(taskRepository, eventsClient, commandEngine, memoryService)
        val listNotesUseCase = ListNotesUseCase(notificationsClient)
        val eventsHandler = EventsCommandHandler(createEventUseCase, listNotesUseCase, renderer)

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
            mcp = mcpHandler,
            events = eventsHandler,
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
