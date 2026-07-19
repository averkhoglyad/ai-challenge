package io.averkhogliad.ai.challenge.week0.bootstrap

import io.averkhogliad.ai.challenge.llm.chat.DefaultLlmClient
import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.llm.chat.LlmClientConfig
import io.averkhogliad.ai.challenge.llm.config.Config
import io.averkhogliad.ai.challenge.week0.application.executor.*
import io.averkhogliad.ai.challenge.week0.cli.CliApplication
import io.averkhogliad.ai.challenge.week0.cli.ConsoleCliRenderer
import io.averkhogliad.ai.challenge.week0.domain.ModelId
import io.averkhogliad.ai.challenge.week0.domain.TaskId
import io.averkhogliad.ai.challenge.week0.domain.service.*
import io.averkhogliad.ai.challenge.week0.infrastructure.config.ConfigAdapter
import io.averkhogliad.ai.challenge.week0.infrastructure.llm.LlmAdapter
import io.averkhogliad.ai.challenge.week0.infrastructure.llm.LlmClientResourceManager
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.averkhogliad.ai.challenge.week0.domain.config.LlmConfig as DomainLlmConfig

/**
 * Composition root приложения AI Challenge Week 0.
 *
 * ## Архитектурная роль
 *
 * [ApplicationBootstrap] — это **composition root** (точка сборки), где:
 * 1. Создаются все инфраструктурные компоненты (adapters)
 * 2. Создаются domain-сервисы с внедрёнными зависимостями
 * 3. Создаются application executor'ы
 * 4. Создаются CLI-компоненты (renderer)
 * 5. Собирается и возвращается [CliApplication]
 *
 * ## Почему composition root?
 *
 * - **Единственное место сборки**: все зависимости создаются здесь, а не разбросаны
 *   по конструкторам через service locator или reflection
 * - **Порядок создания гарантирован**: infrastructure → domain services → application executors → CLI
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
 * Domain Services (зависят только от LlmPort)
 *     ↓
 * Application Executors (зависят от domain services)
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
     * 4. Domain: сервисы (PromptEngineering, Temperature, ModelBenchmark)
     * 5. Application: executor'ы
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

        // 4. Domain: сервисы
        val promptEngineeringService = PromptEngineeringService(llmPort)
        val temperatureService = TemperatureService(llmPort)
        val modelBenchmarkService = ModelBenchmarkService(llmPort)

        // 5. Application: executor'ы
        val executors: Map<TaskId, TaskExecutor> = createExecutors(
            llmPort = llmPort,
            promptEngineeringService = promptEngineeringService,
            temperatureService = temperatureService,
            modelBenchmarkService = modelBenchmarkService,
            defaultModelIds = listOf(domainLlmConfig.defaultModelId)
        )

        // 6. CLI: renderer + facade
        val renderer = ConsoleCliRenderer()

        // 6b. Infrastructure: ResourceManager (обёртка для LlmClient)
        val resourceManager = LlmClientResourceManager(llmClient)

        return CliApplication(
            executors = executors,
            renderer = renderer,
            llmPort = llmPort,
            resourceManager = resourceManager
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

    /**
     * Создаёт все 5 executor'ов и возвращает map [TaskId] → [TaskExecutor].
     *
     * Executor'ы создаются с параметрами по умолчанию:
     * - Task1, Task2: только [LlmPort]
     * - Task3: [PromptEngineeringService] с DIRECT-режимом и дефолтными экспертами
     * - Task4: [TemperatureService] со стандартным набором температур (0.0, 0.7, 1.2)
     * - Task5: [ModelBenchmarkService] с моделями из [defaultModelIds]
     *
     * UI/CLI может пересоздать executor'ы с другими параметрами при изменении
     * настроек пользователем (режим, эксперты, температуры, модели).
     */
    private fun createExecutors(
        llmPort: LlmPort,
        promptEngineeringService: PromptEngineeringService,
        temperatureService: TemperatureService,
        modelBenchmarkService: ModelBenchmarkService,
        defaultModelIds: List<ModelId>
    ): Map<TaskId, TaskExecutor> {
        return mapOf(
            TaskId(1) to Task1Executor(llmPort),
            TaskId(2) to Task2Executor(llmPort),
            TaskId(3) to Task3Executor(promptEngineeringService),
            TaskId(4) to Task4Executor(temperatureService),
            TaskId(5) to Task5Executor(modelBenchmarkService, defaultModelIds)
        )
    }
}
