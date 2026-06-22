package io.averkhogliad.ai.challenge.week2.bootstrap

import io.averkhogliad.ai.challenge.utils.config.Config
import io.averkhogliad.ai.challenge.utils.llm.DefaultLlmClient
import io.averkhogliad.ai.challenge.utils.llm.LlmClientConfig
import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
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

        // 3c. Infrastructure: репозиторий профилей (SQLite persistence)
        val profileRepository = SqliteProfileRepository(dbPath)

        // 5b. Infrastructure: репозиторий инвариантов (SQLite persistence)
        val invariantRepository = SqliteInvariantRepository(dbPath)

        // 5c. Application: InvariantService (управление инвариантами агента)
        val invariantService = InvariantService(invariantRepository)

        // 5d. Application: DialogService (интеграция с LLM)
        val dialogService = DialogService(
            llmPort = llmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            profileRepository = profileRepository,  // передача репозитория профилей для встраивания активного профиля в промпт
            invariantService = invariantService  // NEW: передача сервиса инвариантов для встраивания в промпт
        )

        // 6. Application: Task 1 executor (CLI-ассистент)
        val task1Executor = Task1Executor(dialogService, memoryService)

        // 5c. Application: ProfileService (управление профилями)
        val profileService = ProfileService(profileRepository)

        // 6a. Application: Task 2 executor (CLI-ассистент, копия Task 1) + profile delegation
        val task2Executor = Task2Executor(dialogService, memoryService, profileService)

        // 6b. Application: Task 3 executor (CLI-ассистент, копия Task 2) + profile delegation
        val task3Executor = Task3Executor(dialogService, memoryService, profileService)

        // 6b2. Application: Task 4 executor (CLI-ассистент, копия Task 3) + profile delegation
        val task4Executor = Task4Executor(dialogService, memoryService, profileService)

        // 6b3. Application: Task 5 executor (CLI-ассистент с FSM, копия Task 4) + profile delegation
        val task5Executor = Task5Executor(dialogService, memoryService, profileService)

        // 6c. Application: PlanCommandExecutor (FSM-based планирование)
        val planCommandExecutor = io.averkhogliad.ai.challenge.week2.application.executor.PlanCommandExecutor(
            taskRepository = taskRepository,
            factRepository = factRepository,
            commandEngine = io.averkhogliad.ai.challenge.week2.application.DefaultCommandEngine(),
            llmPort = llmPort,
            invariantService = invariantService  // NEW: передача сервиса инвариантов для проверки конфликтов в :plan
        )

        // 6d. Domain: DebugMode (управление debug-режимом)
        val debugMode = io.averkhogliad.ai.challenge.week2.domain.model.DebugMode()

        // 6e. Application: DebugCommandExecutor (управление debug-режимом через CLI)
        val debugCommandExecutor =
            io.averkhogliad.ai.challenge.week2.application.executor.DebugCommandExecutor(debugMode)

        // 7. CLI: renderer + facade
        val renderer = ConsoleCliRenderer()

        return CliApplication(
            executors = mapOf(
                task1Executor.taskId to task1Executor,
                task2Executor.taskId to task2Executor,
                task3Executor.taskId to task3Executor,
                task4Executor.taskId to task4Executor,
                task5Executor.taskId to task5Executor,
            ),
            renderer = renderer,
            taskManagerExecutor = taskManagerExecutor,
            memoryService = memoryService,
            taskStepRepository = taskStepRepository,
            factRepository = factRepository,
            dialogService = dialogService,
            profileRepository = profileRepository,
            planCommandExecutor = planCommandExecutor,
            debugCommandExecutor = debugCommandExecutor,
            invariantService = invariantService,
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
