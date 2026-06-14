package io.averkhogliad.ai.challenge.week1.bootstrap

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.*
import io.averkhogliad.ai.challenge.week1.application.DialogManager
import io.averkhogliad.ai.challenge.week1.application.executor.*
import io.averkhogliad.ai.challenge.week1.cli.CliApplication
import io.averkhogliad.ai.challenge.week1.cli.ConsoleCliRenderer
import io.averkhogliad.ai.challenge.week1.domain.TaskId
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.context.SlidingWindowCompressor
import io.averkhogliad.ai.challenge.week1.domain.service.*
import io.averkhogliad.ai.challenge.week1.domain.strategy.ContextStrategyManager
import io.averkhogliad.ai.challenge.week1.infrastructure.config.ConfigAdapter
import io.averkhogliad.ai.challenge.week1.infrastructure.llm.LlmAdapter
import io.averkhogliad.ai.challenge.week1.infrastructure.llm.LlmClientResourceManager
import io.averkhogliad.ai.challenge.week1.infrastructure.persistence.SqliteDialogRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.averkhogliad.ai.challenge.week1.domain.config.LlmConfig as DomainLlmConfig

/**
 * Composition root приложения AI Challenge Week 1.
 *
 * ## Архитектурная роль
 *
 * [ApplicationBootstrap] — это **composition root** (точка сборки), где:
 * 1. Создаются все инфраструктурные компоненты (adapters)
 * 2. Создаются application executor'ы
 * 3. Создаются CLI-компоненты (renderer)
 * 4. Собирается и возвращается [CliApplication]
 *
 * ## Почему composition root?
 *
 * - **Единственное место сборки**: все зависимости создаются здесь, а не разбросаны
 *   по конструкторам через service locator или reflection
 * - **Порядок создания гарантирован**: infrastructure → application executors → CLI
 * - **Нет бизнес-логики**: bootstrap только создаёт объекты и связывает их,
 *   не содержит условий, валидации или алгоритмов
 * - **Зависимость infrastructure от domain**: адаптеры [LlmAdapter] и [ConfigAdapter]
 *   создаются здесь, реализуя принцип инверсии зависимостей (DIP)
 *
 * ## Порядок создания (обоснование)
 *
 * ```
 * ConfigAdapter → AppConfig → LlmConfig
 *     ↓
 * LlmClientConfig → DefaultLlmClient
 *     ↓
 * LlmAdapter(llmClient, modelId)
 *     ↓
 * SimpleAgent(llmPort)
 *     ↓
 * Application Executors (зависят от Agent)
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
     * 1. Infrastructure: ConfigAdapter → AppConfig → LlmConfig
     * 2. Infrastructure: LlmClientConfig → DefaultLlmClient
     * 3. Infrastructure: LlmAdapter (связывает LlmClient с LlmPort)
     * 4. Domain: SimpleAgent (инкапсулирует бизнес-логику агента)
     * 5. Application: executor'ы (зависят от Agent)
     * 6. CLI: renderer + application
     */
    private fun createCliApplication(config: Config): CliApplication {
        // 1. Infrastructure: конфигурация
        val configPort: ConfigPort = ConfigAdapter(config)
        val appConfig = configPort.loadAppConfig()
        val domainLlmConfig: DomainLlmConfig = appConfig.llm

        // 2. Infrastructure: LLM клиент
        val llmClient: LlmClient = createLlmClient(domainLlmConfig)

        // 3. Infrastructure: адаптер (реализация LlmPort)
        val llmPort: LlmPort = LlmAdapter(
            llmClient = llmClient,
            defaultModelId = domainLlmConfig.defaultModelId
        )

        // 4. Domain: агент для Task 1
        val agent = SimpleAgent(llmPort)

        // 5. Infrastructure: persistence для диалогов (Task 2)
        val dialogRepository = SqliteDialogRepository()
        val dialogManager = DialogManager(dialogRepository)
        val conversationalAgent = ConversationalAgent(llmPort, dialogRepository)

        // 5a. Task 4: Context Compression components
        val configAdapter = configPort as ConfigAdapter
        val compressionConfig = configAdapter.loadCompressionConfig()
        val compressionConfigProvider = ContextCompressionConfigProvider(compressionConfig)
        val slidingWindowCompressor = SlidingWindowCompressor(llmPort)
        val compressingConversationalAgent = CompressingConversationalAgent(
            delegate = conversationalAgent,
            compressor = slidingWindowCompressor,
            configProvider = compressionConfigProvider,
            dialogRepository = dialogRepository,
            llmPort = llmPort
        )

        // 5b. Task 5: Context Management Strategies
        val contextStrategyManager = ContextStrategyManager(
            llmPort = llmPort,
            slidingWindowCompressor = slidingWindowCompressor
        )

        // 5c. Load ModelInfo for default model (used by Task3Executor for cost calculation)
        val models = config.loadModels()
        val defaultModelId = domainLlmConfig.defaultModelId.value

        // Ищем модель в списке models
        val modelFromList = models.find { it.modelId == defaultModelId }

        // Парсим api.model — он может содержать стоимость (даже если models-запись без стоимости)
        val apiModelInfo = config.getOrNull("api.model")
            ?.takeIf { it.isNotBlank() }
            ?.let { ModelInfo.parse(it) }
            ?.takeIf { it.modelId == defaultModelId }

        val modelInfo = when {
            // Модель найдена в models и имеет стоимость
            modelFromList != null && modelFromList.costPerMillionInputTokens != null -> modelFromList
            // Модель найдена в models, но без стоимости — обогащаем из api.model
            modelFromList != null && apiModelInfo?.costPerMillionInputTokens != null ->
                modelFromList.copy(
                    costPerMillionInputTokens = apiModelInfo.costPerMillionInputTokens,
                    costPerMillionOutputTokens = apiModelInfo.costPerMillionOutputTokens
                )
            // Модель найдена в models (пусть и без стоимости, и api.model не помог)
            modelFromList != null -> modelFromList
            // Модель не найдена в models, но есть в api.model
            apiModelInfo != null -> apiModelInfo
            // Нигде не найдена
            else -> throw IllegalStateException(
                "Default model '$defaultModelId' not found in 'models' or 'api.model' configuration. " +
                        "Please add it to your application.properties."
            )
        }

        // 5c. Load context window from config (default: 16384)
        val contextWindow = config.getOrDefault("api.context-window", "16384")
            .toIntOrNull() ?: throw IllegalArgumentException(
            "Invalid api.context-window: '${config.getOrNull("api.context-window")}'"
        )

        // 6. Application: executor'ы
        val executors: Map<TaskId, TaskExecutor> = mapOf(
            TaskId(1) to Task1Executor(agent),
            TaskId(2) to Task2Executor(conversationalAgent, dialogManager),
            TaskId(3) to Task3Executor(conversationalAgent, dialogManager, modelInfo, contextWindow),
            TaskId(4) to Task4Executor(
                llmPort = llmPort,
                dialogRepository = dialogRepository,
                compressor = slidingWindowCompressor,
                configProvider = compressionConfigProvider
            ),
            TaskId(5) to Task5Executor(
                llmPort = llmPort,
                dialogRepository = dialogRepository,
                slidingWindowCompressor = slidingWindowCompressor
            )
        )

        // 7. CLI: renderer + facade
        val renderer = ConsoleCliRenderer()

        // 7b. Infrastructure: ResourceManager (обёртка для LlmClient)
        val resourceManager = LlmClientResourceManager(llmClient)

        return CliApplication(
            executors = executors,
            renderer = renderer,
            llmPort = llmPort,
            resourceManager = resourceManager,
            compressionConfigProvider = compressionConfigProvider,
            contextStrategyManager = contextStrategyManager
        )
    }

    /**
     * Создаёт [LlmClient] из domain-конфигурации.
     *
     * Маппинг из domain [DomainLlmConfig] в infrastructure [LlmClientConfig]:
     * - `baseUrl`, `apiKey`, `defaultModelId.value` — прямые маппинги
     * - `timeoutSeconds` → `connectTimeout` и `requestTimeout`
     * - `rateLimitEnabled`, `minInterval`, `maxRequestsPerMinute` — значения по умолчанию
     *   (domain-конфиг не содержит rate-limit параметров)
     */
    private fun createLlmClient(domainConfig: DomainLlmConfig): LlmClient {
        val clientConfig = LlmClientConfig(
            baseUrl = domainConfig.baseUrl,
            apiKey = domainConfig.apiKey,
            model = domainConfig.defaultModelId.value,
            connectTimeout = domainConfig.timeoutSeconds.seconds,
            requestTimeout = domainConfig.timeoutSeconds.seconds,
            rateLimitEnabled = true,
            minInterval = 500.milliseconds,
            maxRequestsPerMinute = 60
        )

        return DefaultLlmClient(clientConfig)
    }
}
