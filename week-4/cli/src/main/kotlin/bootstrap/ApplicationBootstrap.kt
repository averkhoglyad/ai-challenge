package io.averkhogliad.ai.challenge.week4.cli.bootstrap

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.DefaultLlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmClientConfig
import io.averkhogliad.ai.challenge.week4.cli.application.DialogService
import io.averkhogliad.ai.challenge.week4.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week4.cli.application.cache.CachingInvariantService
import io.averkhogliad.ai.challenge.week4.cli.application.indexer.DocumentLoader
import io.averkhogliad.ai.challenge.week4.cli.application.indexer.EmbeddingGeneratorFactory
import io.averkhogliad.ai.challenge.week4.cli.application.indexer.IndexingPipeline
import io.averkhogliad.ai.challenge.week4.cli.application.preset.PromptPresetAggregator
import io.averkhogliad.ai.challenge.week4.cli.application.rag.*
import io.averkhogliad.ai.challenge.week4.cli.application.service.MCPService
import io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolCallRouter
import io.averkhogliad.ai.challenge.week4.cli.application.tool.ToolRegistry
import io.averkhogliad.ai.challenge.week4.cli.application.usecase.CreateEventForTaskUseCase
import io.averkhogliad.ai.challenge.week4.cli.application.usecase.ListNotesUseCase
import io.averkhogliad.ai.challenge.week4.cli.cli.*
import io.averkhogliad.ai.challenge.week4.cli.cli.handlers.*
import io.averkhogliad.ai.challenge.week4.cli.cli.indexer.IndexCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.MetricsAnalysisRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.QueryHistoryRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandHandler
import io.averkhogliad.ai.challenge.week4.cli.cli.rag.RagCommandRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.renderers.MCPRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week4.cli.domain.config.AppConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.config.LlmConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.config.ServicesConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.indexer.config.loadIndexerConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPConnectionState
import io.averkhogliad.ai.challenge.week4.cli.domain.model.MCPTransport
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ConfigPort
import io.averkhogliad.ai.challenge.week4.cli.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MCPConnectionManager
import io.averkhogliad.ai.challenge.week4.cli.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week4.cli.domain.service.PromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.client.RestEventsClient
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.client.RestNotificationsClient
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.config.ConfigAdapter
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.chunker.FixedSizeChunker
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.chunker.StructuralChunker
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor.HtmlExtractor
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor.MarkdownExtractor
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.extractor.TextExtractor
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.IndexerDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.indexer.repository.SqliteIndexRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.llm.LlmAdapter
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.mcp.DefaultMCPConnectionManager
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.*
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.preset.ResourcePromptPresetLoader
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.history.SqliteQueryHistoryRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.prompt.CitationAwarePromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.prompt.SimpleRagPromptBuilder
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank.LlmReranker
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rerank.ThresholdReranker
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.rewrite.LlmQueryRewriter
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.rag.search.InMemoryCosineSearchAdapter
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.tool.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
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
        val appConfig: AppConfig? = try {
            configPort.loadAppConfig()
        } catch (_: NoSuchElementException) {
            null  // LLM не настроен — работает в режиме offline
        }
        val llmPort: LlmPort? = appConfig?.let { createLlmPort(it.llm) }

        // 1b. Infrastructure: загрузка конфигурации индексатора
        val indexerConfig = config.loadIndexerConfig()

        // 2. Infrastructure: единый владелец SQLite-соединения
        val dbPath = config.getOrNull("app.database.path") ?: SqliteDatabase.defaultDbPath()
        val database = SqliteDatabase(dbPath)

        // 2a. Infrastructure: инициализация таблиц индексатора
        IndexerDatabase(database).initialize()

        // 3. Infrastructure: репозитории SQLite используют общее соединение
        val taskRepository = SqliteTaskRepository(database)
        val dialogSessionRepository = SqliteDialogSessionRepository(database)
        val taskStepRepository = SqliteTaskStepRepository(database)
        val factRepository = SqliteFactRepository(database)
        val profileRepository = SqliteProfileRepository(database)
        val invariantRepository = SqliteInvariantRepository(database)


        // 8. Application: TodoTaskService (CRUD операции с задачами)
        val todoTaskService = io.averkhogliad.ai.challenge.week4.cli.application.service.TodoTaskService(taskRepository)

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

        // 12d. Подключаем системные MCP-сервера (не сохраняются в БД, не видны в :mcp list)
        connectSystemMcpServers(mcpConnectionManager, config)

        // 12a. Application: LTM and task-step use cases
        val ltmService = io.averkhogliad.ai.challenge.week4.cli.application.service.LtmService(factRepository)
        val taskStepService = io.averkhogliad.ai.challenge.week4.cli.application.service.TaskStepService(
            taskStepRepository = taskStepRepository,
            memoryService = memoryService
        )

        // 12e. Infrastructure: ResourcePromptPresetLoader (загрузка BUILTIN пресетов)
        val resourcePromptPresetLoader = ResourcePromptPresetLoader()

        // 12f. Application: PromptPresetAggregator (BUILTIN + MCP пресеты)
        val promptPresetAggregator = PromptPresetAggregator(resourcePromptPresetLoader, mcpService)

        // 12g. Infrastructure: 6 builtin tools
        val builtinTools = listOf(
            GetCurrentTaskTool(),
            CreateTaskTool(todoTaskService, taskRepository),
            UpdateTaskTool(todoTaskService, taskRepository),
            AddTaskStepTool(taskStepService, taskRepository),
            ListTaskStepsTool(taskStepService),
            LinkTaskToEventTool(taskRepository)
        )

        // 12h. Application: ToolRegistry + ToolCallRouter
        val toolRegistry = ToolRegistry(builtinTools)
        val toolCallRouter = ToolCallRouter(toolRegistry, mcpService)

        // 13. Application: DialogService (интеграция с LLM)
        val dialogService = DialogService(
            llmPort = llmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            profileRepository = profileRepository,
            invariantService = cachingInvariantService,
            mcpService = mcpService,
            toolCallRouter = toolCallRouter,
            toolRegistry = toolRegistry,
            promptPresetAggregator = promptPresetAggregator,
            taskRepository = taskRepository
        )

        // 14. Application: Task executors (task5Executor is created later, after RAG block)
        val task1Executor = io.averkhogliad.ai.challenge.week4.cli.application.executor.Task1Executor(dialogService)
        val task2Executor = io.averkhogliad.ai.challenge.week4.cli.application.executor.Task2Executor(dialogService)
        val task3Executor = io.averkhogliad.ai.challenge.week4.cli.application.executor.Task3Executor(dialogService)
        val task4Executor = io.averkhogliad.ai.challenge.week4.cli.application.executor.Task4Executor(dialogService)
        // task5Executor: see section 14a after RAG block
        val executors = mutableMapOf(
            task1Executor.taskId to task1Executor,
            task2Executor.taskId to task2Executor,
            task3Executor.taskId to task3Executor,
            task4Executor.taskId to task4Executor,
        )

        // 15. Application: planner components
        val keywordExtractor = io.averkhogliad.ai.challenge.week4.cli.application.planner.KeywordExtractor()
        val factCollector = io.averkhogliad.ai.challenge.week4.cli.application.planner.FactCollector(
            factRepository = factRepository,
            keywordExtractor = keywordExtractor
        )
        val stepParser = io.averkhogliad.ai.challenge.week4.cli.application.planner.StepParser()
        val llmPlanner = if (llmPort != null) {
            io.averkhogliad.ai.challenge.week4.cli.application.planner.LlmPlanner(
                llmPort = llmPort,
                temperature = config.getOrDefault("planner.temperature", "0.7").toDoubleOrNull()
                    ?: appConfig?.llm?.defaultTemperature ?: 0.7,
                maxTokens = config.getOrDefault("planner.max-tokens", "2000").toIntOrNull() ?: 2000
            )
        } else null

        // 16. Application: CommandEngine (shared FSM engine, must be passed to CliApplication)
        val commandEngine = io.averkhogliad.ai.challenge.week4.cli.application.DefaultCommandEngine()

        // 17. Application: PlanCommandHandler (FSM-based планирование)
        val planCommandHandler = io.averkhogliad.ai.challenge.week4.cli.application.handler.PlanCommandHandler(
            taskRepository = taskRepository,
            commandEngine = commandEngine,
            factCollector = factCollector,
            llmPlanner = llmPlanner,
            stepParser = stepParser,
            invariantService = cachingInvariantService
        )

        // 18. Domain: DebugMode (управление debug-режимом)
        val debugMode = io.averkhogliad.ai.challenge.week4.cli.domain.model.DebugMode()

        // 19. Application: DebugCommandHandler (управление debug-режимом через CLI)
        val debugCommandHandler =
            io.averkhogliad.ai.challenge.week4.cli.application.handler.DebugCommandHandler(debugMode)

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

        // ──── Indexer components ────

        val indexerHttpClient = HttpClient(CIO)
        val embeddingGeneratorFactory = EmbeddingGeneratorFactory(indexerHttpClient)
        val embeddingGenerator = embeddingGeneratorFactory.create(indexerConfig.embedding)

        val extractors = listOf(
            TextExtractor(),
            MarkdownExtractor(),
            HtmlExtractor()
        )

        val chunkingStrategy = FixedSizeChunker(
            chunkSize = indexerConfig.chunkSize,
            overlap = indexerConfig.overlap
        )

        val structuralChunker = StructuralChunker()

        val indexRepository = SqliteIndexRepository(database)
        val documentLoader = DocumentLoader()

        val indexingPipeline = IndexingPipeline(
            extractors = extractors,
            chunkingStrategy = chunkingStrategy,
            embeddingGenerator = embeddingGenerator,
            repository = indexRepository,
            config = indexerConfig
        )

        val indexCommandHandler = IndexCommandHandler(
            pipeline = indexingPipeline,
            documentLoader = documentLoader,
            repository = indexRepository,
            renderer = renderer,
            fixedSizeChunker = chunkingStrategy,
            structuralChunker = structuralChunker
        )

        // ──── RAG components (Task 2/3/4) ────
        val vectorSearchAdapter = InMemoryCosineSearchAdapter(indexRepository)
        val ragPromptBuilder = SimpleRagPromptBuilder()

        // Task 3: Query history repository
        val historyRepository = SqliteQueryHistoryRepository(database)
        val historyService = QueryHistoryService(historyRepository)

        // Task 3: Reranking + Rewrite
        val tokenEstimateChars = appConfig?.llm?.tokenEstimateCharsPerToken ?: 4
        val thresholdReranker = ThresholdReranker()
        val llmReranker = if (llmPort != null) {
            val fbScore = config.getOrDefault("reranker.fallback-score", "5.0").toFloatOrNull() ?: 5.0f
            val normDiv = config.getOrDefault("reranker.normalization-divisor", "10.0").toFloatOrNull() ?: 10.0f
            val chunkLimit = config.getOrDefault("reranker.chunk-text-limit", "300").toIntOrNull() ?: 300
            LlmReranker(
                llmPort,
                thresholdReranker,
                fallbackScore = fbScore,
                normalizationDivisor = normDiv,
                chunkTextLimit = chunkLimit,
                tokenEstimateCharsPerToken = tokenEstimateChars
            )
        } else null
        val llmQueryRewriter = if (llmPort != null) {
            LlmQueryRewriter(llmPort, tokenEstimateCharsPerToken = tokenEstimateChars)
        } else null

        // Task 3: Search pipeline
        val searchPipeline = if (llmPort != null && llmQueryRewriter != null && llmReranker != null) {
            SearchPipeline(
                queryRewriter = llmQueryRewriter,
                vectorSearch = vectorSearchAdapter,
                reranker = llmReranker,
                embeddingGenerator = embeddingGenerator
            )
        } else null

        val citationPromptBuilder = CitationAwarePromptBuilder(
            appConfig?.rag ?: io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig()
        )

        val ragQueryProcessor = if (llmPort != null) {
            val relevanceChecker = RelevanceChecker()
            val answerParser = RagAnswerParser()
            val answerValidator =
                RagAnswerValidator(appConfig?.rag ?: io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig())

            RagQueryProcessor(
                embeddingGenerator = embeddingGenerator,
                vectorSearchPort = vectorSearchAdapter,
                promptBuilder = ragPromptBuilder,
                llmPort = llmPort,
                indexRepository = indexRepository,
                searchPipeline = searchPipeline,
                historyService = historyService,
                tokenEstimateCharsPerToken = appConfig?.llm?.tokenEstimateCharsPerToken ?: 4,
                relevanceChecker = relevanceChecker,
                citationPromptBuilder = citationPromptBuilder,
                answerParser = answerParser,
                answerValidator = answerValidator,
                ragConfig = appConfig?.rag ?: io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig()
            )
        } else null

        // Task 3: Config + Metrics
        val ragConfig = appConfig?.rag ?: io.averkhogliad.ai.challenge.week4.cli.domain.config.RagConfig()
        val defaultSearchConfig = ragConfig.toSearchConfig()
        val ragConfigService = RagConfigService()

        val initialRagState = RagSessionState(
            config = defaultSearchConfig,
            topK = defaultSearchConfig.topKFinal,
            similarityThreshold = defaultSearchConfig.threshold,
            relevanceThreshold = ragConfig.relevanceThreshold
        )
        val ragStateHolder = MutableStateFlow(initialRagState)
        val ragStateManager = DefaultRagStateManager(ragConfig, ragStateHolder)

        val initialState = CliState(ragState = initialRagState)
        val metricsAnalyzer = MetricsAnalyzer(historyService)
        val historyRenderer = QueryHistoryRenderer()
        val analysisRenderer = MetricsAnalysisRenderer()

        val ragCommandRenderer = RagCommandRenderer(ragConfig = ragConfig)
        val ragCommandHandler = RagCommandHandler(
            indexRepository = indexRepository,
            ragRenderer = ragCommandRenderer,
            configService = ragConfigService,
            historyService = historyService,
            metricsAnalyzer = metricsAnalyzer,
            historyRenderer = historyRenderer,
            analysisRenderer = analysisRenderer,
            ragStateManager = ragStateManager,
            ragConfig = ragConfig
        )

        // 14a. Application: Chat services (Task 5)
        val chatConfig = io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig(
            historyWindowSize = config.getOrDefault("chat.history.window-size", "6").toInt(),
            nameMaxLength = config.getOrDefault("chat.name.max-length", "50").toInt(),
            autoNameEnabled = config.getOrDefault("chat.auto-name.enabled", "true").toBoolean(),
            taskStateExtractionEnabled = config.getOrDefault("task-state.extraction.enabled", "true").toBoolean(),
            taskStateMaxTerms = config.getOrDefault("task-state.extraction.max-terms", "50").toInt(),
            taskStateMaxConstraints = config.getOrDefault("task-state.extraction.max-constraints", "50").toInt(),
            maxClarifiedFacts = config.getOrDefault("task-state.extraction.max-clarified-facts", "50").toInt()
        )
        val chatPromptBuilder = io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatPromptBuilder(
            citationPromptBuilder = citationPromptBuilder,
            config = chatConfig
        )

        // Infrastructure: SQLite chat session repository
        val chatSessionRepository =
            io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteChatSessionRepository(database)

        // Infrastructure: LLM adapters for TaskStateExtractor and ChatNameGenerator
        val taskStateExtractor: io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStateExtractor =
            if (llmPort != null) {
                io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat.LlmTaskStateExtractor(llmPort)
            } else {
                object : io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStateExtractor {
                    override suspend fun extract(
                        currentState: io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskState,
                        newMessages: List<io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage>
                    ): Result<io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta> =
                        Result.success(io.averkhogliad.ai.challenge.week4.cli.domain.model.TaskStateDelta.NoChanges)
                }
            }
        val chatNameGenerator: io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator =
            if (llmPort != null) {
                io.averkhogliad.ai.challenge.week4.cli.infrastructure.chat.LlmChatNameGenerator(llmPort)
            } else {
                object : io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator {
                    override suspend fun generate(messages: List<io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatMessage>): Result<String> =
                        Result.success("New Chat")
                }
            }

        // Application: ChatSessionManager
        val chatSessionManager = io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatSessionManager(
            repository = chatSessionRepository,
            nameGenerator = chatNameGenerator,
            config = chatConfig
        )

        // Application: ChatExecutor
        val chatExecutor = if (ragQueryProcessor != null) {
            io.averkhogliad.ai.challenge.week4.cli.application.chat.ChatExecutor(
                taskStateExtractor = taskStateExtractor,
                ragQueryProcessor = ragQueryProcessor,
                chatSessionRepository = chatSessionRepository,
                chatSessionManager = chatSessionManager,
                chatPromptBuilder = chatPromptBuilder,
                chatNameGenerator = chatNameGenerator,
                config = chatConfig
            )
        } else null

        // Application: TaskStateManager
        val taskStateManager = io.averkhogliad.ai.challenge.week4.cli.application.chat.TaskStateManager(
            repository = chatSessionRepository,
            config = chatConfig
        )

        if (chatExecutor != null) {
            val task5Executor = io.averkhogliad.ai.challenge.week4.cli.application.executor.Task5Executor(
                chatExecutor = chatExecutor,
                chatSessionManager = chatSessionManager
            )
            executors[task5Executor.taskId] = task5Executor
        }

        // CLI: Chat renderers (all are singleton objects)
        val chatAnswerRenderer = io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatAnswerRenderer
        val chatListRenderer = io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatListRenderer
        val chatHistoryRenderer = io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatHistoryRenderer
        val chatNotificationRenderer = io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatNotificationRenderer
        val taskStateRenderer = io.averkhogliad.ai.challenge.week4.cli.cli.chat.TaskStateRenderer

        // CLI: Chat command handlers
        val chatCommandHandler = io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatCommandHandler(
            chatSessionManager = chatSessionManager,
            repository = chatSessionRepository,
            listRenderer = chatListRenderer,
            notificationRenderer = chatNotificationRenderer,
            historyRenderer = chatHistoryRenderer
        )
        val taskStateCommandHandler = io.averkhogliad.ai.challenge.week4.cli.cli.chat.TaskStateCommandHandler(
            taskStateManager = taskStateManager,
            renderer = taskStateRenderer,
            notificationRenderer = chatNotificationRenderer
        )

        // CLI: ChatModeHandler
        val chatModeHandler = if (chatExecutor != null) {
            io.averkhogliad.ai.challenge.week4.cli.cli.chat.ChatModeHandler(
                chatExecutor = chatExecutor,
                chatSessionManager = chatSessionManager,
                chatCommandHandler = chatCommandHandler,
                taskStateCommandHandler = taskStateCommandHandler,
                answerRenderer = chatAnswerRenderer,
                notificationRenderer = chatNotificationRenderer
            )
        } else null

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
            indexer = indexCommandHandler,
            rag = ragCommandHandler,
            chatCommand = chatCommandHandler,
            taskStateCommand = taskStateCommandHandler,
            chatMode = chatModeHandler,
        )

        val userInputFlowHandler = UserInputFlowHandler(
            renderer = renderer,
            dialogService = dialogService,
            planCommandHandler = planCommandHandler,
            commandEngine = commandEngine,
            commandHandler = commandHandler,
            indexRepository = indexRepository,
            ragQueryProcessor = ragQueryProcessor,
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
            chatModeHandler = chatModeHandler,
            initialState = initialState,
        )


    }

    /**
     * Подключает системные MCP-сервера (weather, events, notifications).
     *
     * Сервера регистрируются через [MCPConnectionManager.connectSystem] —
     * в обход [MCPServerRepository], не сохраняются в БД, не видны в `:mcp list`.
     * Ошибки подключения игнорируются — сервер может быть не запущен.
     */
    private fun connectSystemMcpServers(
        connectionManager: MCPConnectionManager,
        config: Config
    ) {
        val systemServers = listOf(
            ModelId("system-weather") to deriveMcpUrl(config, "services.weather.base-url"),
            ModelId("system-events") to deriveMcpUrl(config, "services.events.base-url"),
            ModelId("system-notifications") to deriveMcpUrl(config, "services.notifications.base-url")
        )

        runBlocking {
            for ((id, url) in systemServers) {
                if (url.isNullOrBlank()) continue
                val state = connectionManager.connectSystem(
                    id = id,
                    name = id.value,
                    transport = MCPTransport.StreamableHttp(url)
                )
                when (state) {
                    is MCPConnectionState.Connected ->
                        System.err.println("  \u001b[32m✓\u001b[0m Системный MCP-сервер \"${id.value}\" подключён")

                    is MCPConnectionState.Failed ->
                        System.err.println("  \u001b[33m⚠\u001b[0m Системный MCP-сервер \"${id.value}\" не подключён: ${state.error}")

                    else -> {}
                }
            }
        }
    }

    /**
     * Извлекает URL сервиса из конфигурации и добавляет к нему путь `/mcp`.
     * Возвращает null, если базовый URL не задан.
     */
    private fun deriveMcpUrl(config: Config, key: String): String? {
        val baseUrl = config.getOrNull(key) ?: return null
        val trimmed = baseUrl.trimEnd('/')
        return "$trimmed/mcp"
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
