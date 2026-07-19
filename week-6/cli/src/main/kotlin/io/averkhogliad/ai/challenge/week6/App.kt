package io.averkhogliad.ai.challenge.week6

import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.ai.challenge.indexer.application.ChunkingStrategyFactory
import io.averkhogliad.ai.challenge.indexer.config.ChunkingConfig
import io.averkhogliad.ai.challenge.indexer.domain.port.ChunkingStrategyType
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.DocumentExtractorRegistry
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.HtmlExtractor
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.MarkdownExtractor
import io.averkhogliad.ai.challenge.indexer.infrastructure.extractor.TextExtractor
import io.averkhogliad.ai.challenge.indexer.infrastructure.search.InMemoryCosineSearchAdapter
import io.averkhogliad.ai.challenge.llm.chat.DefaultLlmClient
import io.averkhogliad.ai.challenge.llm.chat.LlmClientConfig
import io.averkhogliad.ai.challenge.llm.embedding.StubEmbeddingClient
import io.averkhogliad.ai.challenge.week6.application.*
import io.averkhogliad.ai.challenge.week6.application.mcp.*
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.application.review.ReviewCodeUseCase
import io.averkhogliad.ai.challenge.week6.application.review.SaveReviewTool
import io.averkhogliad.ai.challenge.week6.application.review.SaveReviewUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.CreatePullRequestUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.GetPullRequestDiffUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.ListPullRequestsUseCase
import io.averkhogliad.ai.challenge.week6.cli.contexts.CopilotContext
import io.averkhogliad.ai.challenge.week6.cli.contexts.OnboardingContext
import io.averkhogliad.ai.challenge.week6.cli.contexts.SupportContext
import io.averkhogliad.ai.challenge.week6.cli.handlers.SupportCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.mcp.*
import io.averkhogliad.ai.challenge.week6.cli.handlers.review.*
import io.averkhogliad.ai.challenge.week6.cli.rendering.McpServerInfoRenderer
import io.averkhogliad.ai.challenge.week6.cli.rendering.DiffRenderer
import io.averkhogliad.ai.challenge.week6.cli.rendering.ReviewRenderers
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexMetadataStore
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedSourceRepository
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CheckIndexStalenessUseCase
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CollectDefaultSourcesUseCase
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.IndexSourcesUseCase
import io.averkhogliad.ai.challenge.week6.infrastructure.config.AppConfigLoader
import io.averkhogliad.ai.challenge.week6.infrastructure.db.DatabaseFactory
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlAppStateRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlIndexedChunkRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlIndexedSourceRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlMcpServerRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlProjectRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlPullRequestRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.SqlReviewRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.AppStateTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.IndexChunksTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.McpServersTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectSourcesTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ProjectsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.PullRequestsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReviewFindingsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.ReviewsTable
import io.averkhogliad.ai.challenge.week6.infrastructure.git.ProcessGitAdapter
import io.averkhogliad.ai.challenge.week6.infrastructure.indexer.metadata.InMemoryIndexMetadataStore
import io.averkhogliad.ai.challenge.week6.infrastructure.mcp.KtorMcpClientAdapter
import io.averkhogliad.ai.challenge.week6.infrastructure.tools.GitBuiltinTool
import io.averkhogliad.ai.challenge.week6.infrastructure.tools.RagSearchBuiltinTool
import io.averkhogliad.cli.repl.core.ReplContext
import io.averkhogliad.cli.repl.engine.ReplEngine
import io.averkhogliad.cli.repl.io.StdinInputReader
import io.averkhogliad.cli.repl.mordant.writer.MordantOutputWriter
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.seconds

fun main() {
    val args = emptyArray<String>()
    val isReviewMode = args.contains("--review")
    val config = AppConfigLoader().load()
    val db: Database = DatabaseFactory.connect(config.dbPath)

    transaction(db) {
        SchemaUtils.create(
            ProjectsTable,
            McpServersTable,
            AppStateTable,
            ProjectSourcesTable,
            IndexChunksTable,
            ReviewsTable,
            ReviewFindingsTable,
            PullRequestsTable
        )
    }

    val projectRepository = SqlProjectRepository()
    val appStateRepository = SqlAppStateRepository()
    val indexedChunkRepository = SqlIndexedChunkRepository()
    val reviewRepository = SqlReviewRepository()

    // LLM Client
    val llmClientConfig = LlmClientConfig(
        baseUrl = System.getenv("LLM_BASE_URL") ?: "http://localhost:11434/v1",
        apiKey = System.getenv("LLM_API_KEY") ?: "ollama",
        model = System.getenv("LLM_MODEL") ?: "qwen2.5:7b",
        connectTimeout = 10.seconds,
        requestTimeout = 120.seconds,
        rateLimitEnabled = false,
        minInterval = 0.5.seconds,
        maxRequestsPerMinute = 60,
    )
    val llmClient = DefaultLlmClient(llmClientConfig)

    // Git
    val gitPort = ProcessGitAdapter()

    // Embedding (from :common:llm)
    val embeddingClient = StubEmbeddingClient()

    // Indexer (from :common:indexer)
    val extractorRegistry = DocumentExtractorRegistry(
        listOf(TextExtractor(), MarkdownExtractor(), HtmlExtractor())
    )
    val chunkingStrategy = ChunkingStrategyFactory.create(
        ChunkingConfig(strategy = ChunkingStrategyType.STRUCTURAL)
    )
    val vectorSearch = InMemoryCosineSearchAdapter()

    // RAG
    val ragService = RagService(extractorRegistry, chunkingStrategy, embeddingClient, vectorSearch)

    // Indexer infrastructure
    val sourceRepository: IndexedSourceRepository = SqlIndexedSourceRepository()
    val metadataStore: IndexMetadataStore = InMemoryIndexMetadataStore()
    val collectDefaultSourcesUseCase = CollectDefaultSourcesUseCase()
    val checkStalenessUseCase = CheckIndexStalenessUseCase(gitPort, metadataStore)
    val indexSourcesUseCase = IndexSourcesUseCase(
        extractorRegistry, chunkingStrategy, embeddingClient, vectorSearch, gitPort, metadataStore,
        indexedChunkRepository,
    )

    // Project context (needed by tools)
    val getActiveProjectUseCase = GetActiveProjectUseCase(appStateRepository, projectRepository)
    val projectContextProvider = ProjectContextProvider(getActiveProjectUseCase)

    // Tools
    val toolRegistry = ToolRegistryImpl()
    val ragSearchTool = RagSearchBuiltinTool(ragService, checkStalenessUseCase, projectContextProvider)
    toolRegistry.register(GitBuiltinTool(gitPort, projectContextProvider))
    toolRegistry.register(ragSearchTool)

    // Use cases
    val openProjectUseCase = OpenProjectUseCase(
        projectRepository = projectRepository,
        appStateRepository = appStateRepository,
        sourceRepository = sourceRepository,
        collectDefaultSourcesUseCase = collectDefaultSourcesUseCase,
        indexSourcesUseCase = indexSourcesUseCase,
    )
    val listProjectsUseCase = ListProjectsUseCase(projectRepository)

    val agentLoopService = AgentLoopService(llmClient, toolRegistry, projectContextProvider)

    // MCP infrastructure
    val mcpServerRepository = SqlMcpServerRepository()
    val mcpClientManager = McpClientManager(
        clientFactory = { KtorMcpClientAdapter() },
    )

    // MCP use cases
    val addMcpServerUseCase = AddMcpServerUseCase(mcpServerRepository, toolRegistry, mcpClientManager)
    val removeMcpServerUseCase = RemoveMcpServerUseCase(mcpServerRepository, toolRegistry, mcpClientManager)
    val toggleMcpServerUseCase = ToggleMcpServerUseCase(mcpServerRepository, mcpClientManager, toolRegistry)
    val reconnectMcpServerUseCase = ReconnectMcpServerUseCase(mcpServerRepository, toolRegistry, mcpClientManager)

    // Support
    val supportUseCase = SupportUseCase(agentLoopService)
    val supportContext = SupportContext(supportUseCase)
    val supportCommandHandler = SupportCommandHandler()

    // Review
    val saveReviewUseCase = SaveReviewUseCase(reviewRepository)
    val reviewCodeUseCase = ReviewCodeUseCase(llmClient, ragService, saveReviewUseCase)
    val saveReviewTool = SaveReviewTool()
    toolRegistry.register(saveReviewTool)

    // PR
    val prRepository = SqlPullRequestRepository()
    val createPrUseCase = CreatePullRequestUseCase(prRepository, gitPort)
    val listPrUseCase = ListPullRequestsUseCase(prRepository)
    val getPrDiffUseCase = GetPullRequestDiffUseCase(gitPort)

    // MCP CLI handlers
    val terminal = Terminal()
    val mcpServerInfoRenderer = McpServerInfoRenderer(terminal)
    val mcpListHandler = McpListHandler(mcpServerRepository, mcpClientManager, mcpServerInfoRenderer)
    val mcpAddHandler = McpAddHandler(addMcpServerUseCase)
    val mcpRemoveHandler = McpRemoveHandler(removeMcpServerUseCase)
    val mcpEnableHandler = McpEnableHandler(toggleMcpServerUseCase)
    val mcpInfoHandler = McpInfoHandler(mcpServerRepository, mcpClientManager)
    val mcpReconnectHandler = McpReconnectHandler(reconnectMcpServerUseCase)

    // Review/PR CLI handlers
    val reviewRenderers = ReviewRenderers(terminal)
    val diffRenderer = DiffRenderer(terminal)

    val rootPathProvider: () -> java.nio.file.Path? = {
        runBlocking {
            when (val r = getActiveProjectUseCase.execute()) {
                is DomainResult.Success -> r.value?.rootPath
                is DomainResult.Failure -> null
            }
        }
    }
    val projectIdProvider: () -> String? = {
        runBlocking {
            when (val r = getActiveProjectUseCase.execute()) {
                is DomainResult.Success -> r.value?.id
                is DomainResult.Failure -> null
            }
        }
    }
    val binPathProvider: () -> java.nio.file.Path =
        { java.nio.file.Path.of(System.getProperty("java.home")).parent.resolve("bin").resolve("week-6") }

    val reviewHandler = ReviewCommandHandler(reviewCodeUseCase, gitPort, rootPathProvider, projectIdProvider)
    val reviewHistoryHandler = ReviewHistoryCommandHandler(reviewRepository, reviewRenderers, projectIdProvider)
    val reviewShowHandler = ReviewShowCommandHandler(reviewRepository, reviewRenderers)
    val reviewInstallHookHandler = ReviewInstallHookHandler(rootPathProvider, binPathProvider)
    val reviewRemoveHookHandler = ReviewRemoveHookHandler(rootPathProvider)
    val prCreateHandler = PrCreateHandler(createPrUseCase, rootPathProvider, projectIdProvider)
    val prListHandler = PrListHandler(listPrUseCase, projectIdProvider, terminal)
    val prReviewHandler = PrReviewHandler(reviewCodeUseCase, prRepository, gitPort, rootPathProvider, projectIdProvider)
    val prDiffHandler = PrDiffHandler(getPrDiffUseCase, prRepository, diffRenderer, rootPathProvider)

    val onboardingContext = OnboardingContext(openProjectUseCase)

    val activeProject = runBlocking {
        when (val result = getActiveProjectUseCase.execute()) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> null
        }
    }

    // Blocking indexing with progress
    if (activeProject != null) {
        try {
            var sources = runBlocking { sourceRepository.findByProjectId(activeProject.id) }
            if (sources.isEmpty()) {
                sources = collectDefaultSourcesUseCase.execute(activeProject.id, activeProject.rootPath)
                runBlocking {
                    sources.forEach { sourceRepository.addSource(it) }
                }
            }
            println("Индексация документации...")
            runBlocking {
                indexSourcesUseCase.execute(sources, activeProject.id, activeProject.rootPath)
                    .collect { progress ->
                        when (progress) {
                            is io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexProgress.SourceComplete ->
                                println("  [${progress.index}/${progress.total}] ${progress.sourcePath} (${progress.chunkCount} чанков)")

                            is io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexProgress.Completed ->
                                println("Индекс готов: ${progress.totalChunks} чанков, модель: ${progress.model}")

                            is io.averkhogliad.ai.challenge.week6.domain.indexer.model.IndexProgress.Error ->
                                System.err.println("Ошибка: ${progress.source}: ${progress.cause}")

                            else -> {}
                        }
                    }
            }
        } catch (e: Exception) {
            System.err.println("[App] Startup indexing failed: ${e.message}")
        }
    }

    val copilotContext = CopilotContext(
        openProjectUseCase = openProjectUseCase,
        listProjectsUseCase = listProjectsUseCase,
        agentLoopService = agentLoopService,
        mcpListHandler = mcpListHandler,
        mcpAddHandler = mcpAddHandler,
        mcpRemoveHandler = mcpRemoveHandler,
        mcpEnableHandler = mcpEnableHandler,
        mcpInfoHandler = mcpInfoHandler,
        mcpReconnectHandler = mcpReconnectHandler,
        supportCommandHandler = supportCommandHandler,
        reviewCommandHandler = reviewHandler,
        reviewHistoryHandler = reviewHistoryHandler,
        reviewShowHandler = reviewShowHandler,
        reviewInstallHookHandler = reviewInstallHookHandler,
        reviewRemoveHookHandler = reviewRemoveHookHandler,
        prCreateHandler = prCreateHandler,
        prListHandler = prListHandler,
        prReviewHandler = prReviewHandler,
        prDiffHandler = prDiffHandler,
    )

    if (isReviewMode) {
        val reviewRunner = ReviewRunner(llmClient, ragService, gitPort, saveReviewUseCase, projectContextProvider)
        runBlocking { reviewRunner.runReview() }
        return
    }

    val initialContext: ReplContext = if (activeProject != null) copilotContext else onboardingContext

    val outputWriter = MordantOutputWriter(terminal)
    val inputReader = StdinInputReader()

    val engine = ReplEngine(
        initialContext = initialContext,
        additionalContexts = listOf(onboardingContext, copilotContext, supportContext),
        inputReader = inputReader,
        outputWriter = outputWriter,
    )

    // Shutdown hook for MCP cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { mcpClientManager.closeAll() }
    })

    runBlocking { engine.start() }
}
