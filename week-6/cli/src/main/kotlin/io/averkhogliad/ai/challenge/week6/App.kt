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
import io.averkhogliad.ai.challenge.llm.embedding.EmbeddingClientFactory
import io.averkhogliad.ai.challenge.llm.embedding.config.EmbeddingConfig
import io.averkhogliad.ai.challenge.week6.application.*
import io.averkhogliad.ai.challenge.week6.application.fileops.*
import io.averkhogliad.ai.challenge.week6.application.mcp.*
import io.averkhogliad.ai.challenge.week6.application.pr.CreatePullRequestUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.GetPullRequestDiffUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.ListPullRequestsUseCase
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.application.release.*
import io.averkhogliad.ai.challenge.week6.application.review.ReviewCodeUseCase
import io.averkhogliad.ai.challenge.week6.application.review.SaveReviewTool
import io.averkhogliad.ai.challenge.week6.application.review.SaveReviewUseCase
import io.averkhogliad.ai.challenge.week6.cli.contexts.CopilotContext
import io.averkhogliad.ai.challenge.week6.cli.contexts.OnboardingContext
import io.averkhogliad.ai.challenge.week6.cli.contexts.SupportContext
import io.averkhogliad.ai.challenge.week6.cli.handlers.SupportCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.fileops.*
import io.averkhogliad.ai.challenge.week6.cli.handlers.indexer.IndexCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.mcp.*
import io.averkhogliad.ai.challenge.week6.cli.handlers.release.ReleaseCommandHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.release.ReleaseHistoryHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.release.ReleaseShowHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.release.ReleaseSuggestHandler
import io.averkhogliad.ai.challenge.week6.cli.handlers.review.*
import io.averkhogliad.ai.challenge.week6.cli.rendering.*
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexMetadataStore
import io.averkhogliad.ai.challenge.week6.domain.indexer.port.IndexedSourceRepository
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CheckIndexStalenessUseCase
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.CollectDefaultSourcesUseCase
import io.averkhogliad.ai.challenge.week6.domain.indexer.usecase.IndexSourcesUseCase
import io.averkhogliad.ai.challenge.week6.infrastructure.config.AppConfigLoader
import io.averkhogliad.ai.challenge.week6.infrastructure.db.DatabaseFactory
import io.averkhogliad.ai.challenge.week6.infrastructure.db.repository.*
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.*
import io.averkhogliad.ai.challenge.week6.infrastructure.fileops.FileOpsBuiltinTool
import io.averkhogliad.ai.challenge.week6.infrastructure.fileops.LocalFileOpsAdapter
import io.averkhogliad.ai.challenge.week6.infrastructure.git.ProcessGitAdapter
import io.averkhogliad.ai.challenge.week6.infrastructure.indexer.metadata.InMemoryIndexMetadataStore
import io.averkhogliad.ai.challenge.week6.infrastructure.mcp.KtorMcpClientAdapter
import io.averkhogliad.ai.challenge.week6.infrastructure.release.*
import io.averkhogliad.ai.challenge.week6.infrastructure.tools.GitBuiltinTool
import io.averkhogliad.ai.challenge.week6.infrastructure.tools.RagSearchBuiltinTool
import io.averkhogliad.cli.repl.core.ReplContext
import io.averkhogliad.cli.repl.engine.ReplEngine
import io.averkhogliad.cli.repl.io.StdinInputReader
import io.averkhogliad.cli.repl.mordant.writer.MordantOutputWriter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Duration.Companion.seconds

fun main(args: Array<String>) {
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
            PullRequestsTable,
            ReleasesTable
        )
    }

    val projectRepository = SqlProjectRepository()
    val appStateRepository = SqlAppStateRepository()
    val indexedChunkRepository = SqlIndexedChunkRepository()
    val reviewRepository = SqlReviewRepository()
    val releaseRepository = SqlReleaseRepository()

    // LLM Client
    val llmClientConfig = LlmClientConfig(
        baseUrl = config.llmBaseUrl,
        apiKey = config.llmApiKey,
        model = config.llmModel,
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
    val embeddingClient = EmbeddingClientFactory.create(
        EmbeddingConfig(provider = config.embeddingProviderConfig)
    )

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

    // Project settings repository (used by OpenProjectUseCase and FileOps tools)
    val projectSettingsRepo = ProjectSettingsRepository()

    // Use cases
    val openProjectUseCase = OpenProjectUseCase(
        projectRepository = projectRepository,
        appStateRepository = appStateRepository,
        sourceRepository = sourceRepository,
        collectDefaultSourcesUseCase = collectDefaultSourcesUseCase,
        indexSourcesUseCase = indexSourcesUseCase,
        projectSettingsRepository = projectSettingsRepo,
    )
    val listProjectsUseCase = ListProjectsUseCase(projectRepository)

    val agentLoopService = AgentLoopService(llmClient, toolRegistry, projectContextProvider)

    val conventionalCommitParser = ConventionalCommitParser()
    val ticketIdExtractor = TicketIdExtractor()
    val gitHistoryFetcher = GitHistoryFetcher(gitPort, conventionalCommitParser, ticketIdExtractor)
    val parseConventionalCommitUseCase = ParseConventionalCommitUseCase(conventionalCommitParser)
    val classifyCommitUseCase = ClassifyCommitUseCase(llmClient)
    val hybridCommitClassifier = HybridCommitClassifier(parseConventionalCommitUseCase, classifyCommitUseCase)
    val suggestVersionUseCase = SuggestVersionUseCase(releaseRepository)
    val enhanceWithRagContextUseCase = EnhanceWithRagContextUseCase(ragService)
    val generateReleaseNotesUseCase = GenerateReleaseNotesUseCase(
        gitHistoryFetcher,
        hybridCommitClassifier,
        suggestVersionUseCase,
        enhanceWithRagContextUseCase,
        ChangelogFormatter(),
    )
    val listReleasesUseCase = ListReleasesUseCase(releaseRepository)
    val showReleaseUseCase = ShowReleaseUseCase(releaseRepository)

    if (args.firstOrNull() == "--release") {
        val exitCode = runBlocking { runStandaloneRelease(args, projectRepository, generateReleaseNotesUseCase) }
        if (exitCode != 0) kotlin.system.exitProcess(exitCode)
        return
    }

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

    val activeProjectProvider: () -> io.averkhogliad.ai.challenge.week6.domain.model.Project? = {
        runBlocking {
            when (val r = getActiveProjectUseCase.execute()) {
                is DomainResult.Success -> r.value
                is DomainResult.Failure -> null
            }
        }
    }
    val rootPathProvider: () -> java.nio.file.Path? = { activeProjectProvider()?.rootPath }
    val projectIdProvider: () -> String? = { activeProjectProvider()?.id }
    val launcherPathProvider: () -> java.nio.file.Path? = {
        System.getProperty("week6.launcher.path")
            ?.takeIf(String::isNotBlank)
            ?.let(java.nio.file.Path::of)
    }

    val reviewHandler = ReviewCommandHandler(reviewCodeUseCase, gitPort, rootPathProvider, projectIdProvider)
    val reviewHistoryHandler = ReviewHistoryCommandHandler(reviewRepository, reviewRenderers, projectIdProvider)
    val reviewShowHandler = ReviewShowCommandHandler(reviewRepository, reviewRenderers)
    val reviewInstallHookHandler = ReviewInstallHookHandler(rootPathProvider, launcherPathProvider)
    val reviewRemoveHookHandler = ReviewRemoveHookHandler(rootPathProvider)
    val prCreateHandler = PrCreateHandler(createPrUseCase, rootPathProvider, projectIdProvider)
    val prListHandler = PrListHandler(listPrUseCase, projectIdProvider, terminal)
    val prReviewHandler = PrReviewHandler(reviewCodeUseCase, prRepository, gitPort, rootPathProvider, projectIdProvider)
    val prDiffHandler = PrDiffHandler(getPrDiffUseCase, prRepository, diffRenderer, rootPathProvider)

    val activeProject = runBlocking {
        when (val result = getActiveProjectUseCase.execute()) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> null
        }
    }

    // Blocking indexing with progress rendered by the CLI layer
    val startupIndexingUseCase = StartupIndexingUseCase(
        sourceRepository,
        collectDefaultSourcesUseCase,
        indexSourcesUseCase,
    )

    if (activeProject != null) {
        val startupIndexingRenderer = StartupIndexingRenderer(terminal)
        runBlocking {
            startupIndexingUseCase.execute(activeProject).collect(startupIndexingRenderer::render)
        }
    }

    // FileOps tools (registered after activeProject is known)
    val activeProjectRoot = activeProject?.rootPath ?: java.nio.file.Path.of(".")
    val sandboxPolicy = if (activeProject != null) {
        SandboxPolicy(ExclusionList.fromProject(projectSettingsRepo, activeProject.id))
    } else {
        SandboxPolicy(ExclusionList())
    }
    val fileOpsAdapter = LocalFileOpsAdapter(activeProjectRoot, sandboxPolicy)
    val confirmReleaseUseCaseFactory: (io.averkhogliad.ai.challenge.week6.domain.model.Project) -> ConfirmReleaseUseCase =
        { project ->
            val changelogPath = when (val result =
                io.averkhogliad.ai.challenge.week6.domain.fileops.model.RelativePath.from(
                    "CHANGELOG.md",
                    project.rootPath
                )) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> error(result.error.message)
            }
            ConfirmReleaseUseCase(
                LocalFileOpsAdapter(
                    project.rootPath,
                    SandboxPolicy(ExclusionList.fromProject(projectSettingsRepo, project.id)),
                ),
                releaseRepository,
                changelogPath,
            )
        }
    val fileOpsToolFactory = FileOpsBuiltinTool(fileOpsAdapter, projectContextProvider)
    fileOpsToolFactory.createTools().forEach { toolRegistry.register(it) }

    // FileOps use cases
    val fileSearchUseCase = FileSearchUseCase(fileOpsAdapter, projectContextProvider)
    val fileReadUseCase = FileReadUseCase(fileOpsAdapter, projectContextProvider)
    val fileListUseCase = FileListUseCase(fileOpsAdapter, projectContextProvider)

    // FileOps CLI handlers
    val searchResultsRenderer = SearchResultsRenderer(terminal)
    val releaseTableRenderer = ReleaseTableRenderer(terminal)
    val releaseDetailRenderer =
        ReleaseDetailRenderer(io.averkhogliad.cli.repl.mordant.common.MarkdownRenderer(terminal))
    val releaseCommandHandler =
        ReleaseCommandHandler(generateReleaseNotesUseCase, confirmReleaseUseCaseFactory, activeProjectProvider)
    val releaseSuggestHandler = ReleaseSuggestHandler(generateReleaseNotesUseCase, rootPathProvider, projectIdProvider)
    val releaseHistoryHandler = ReleaseHistoryHandler(listReleasesUseCase, releaseTableRenderer, projectIdProvider)
    val releaseShowHandler = ReleaseShowHandler(showReleaseUseCase, releaseDetailRenderer, projectIdProvider)
    val findCommandHandler = FindCommandHandler(fileSearchUseCase, searchResultsRenderer)
    val fileReadCommandHandler = FileReadCommandHandler(fileReadUseCase)
    val fileInfoUseCase = FileInfoUseCase(fileOpsAdapter, projectContextProvider)
    val fileInfoCommandHandler = FileInfoCommandHandler(fileInfoUseCase)
    val fileListCommandHandler = FileListCommandHandler(fileListUseCase)

    // Refactor orchestrator + use case
    val diffService = DiffService()
    val refactorAgentOrchestrator = RefactorAgentOrchestrator(
        agentLoopService, fileOpsAdapter, diffService, projectContextProvider
    )
    val refactorUseCase = RefactorUseCase(refactorAgentOrchestrator, diffRenderer, projectContextProvider)
    val configExclusionsHandler = ConfigExclusionsHandler(projectSettingsRepo, projectContextProvider)
    val refactorCommandHandler = RefactorCommandHandler(refactorUseCase)
    val indexCommandHandler = IndexCommandHandler(startupIndexingUseCase, activeProjectProvider)

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
        findCommandHandler = findCommandHandler,
        fileReadCommandHandler = fileReadCommandHandler,
        fileInfoCommandHandler = fileInfoCommandHandler,
        fileListCommandHandler = fileListCommandHandler,
        configExclusionsHandler = configExclusionsHandler,
        refactorCommandHandler = refactorCommandHandler,
        indexCommandHandler = indexCommandHandler,
        releaseCommandHandler = releaseCommandHandler,
        releaseSuggestHandler = releaseSuggestHandler,
        releaseHistoryHandler = releaseHistoryHandler,
        releaseShowHandler = releaseShowHandler,
    )

    val onboardingContext = OnboardingContext(
        openProjectUseCase = openProjectUseCase,
        listProjectsUseCase = listProjectsUseCase,
        mcpListHandler = mcpListHandler,
        mcpAddHandler = mcpAddHandler,
        mcpRemoveHandler = mcpRemoveHandler,
        mcpEnableHandler = mcpEnableHandler,
        mcpInfoHandler = mcpInfoHandler,
        mcpReconnectHandler = mcpReconnectHandler,
    )

    if (isReviewMode) {
        try {
            val reviewRunner = ReviewRunner(llmClient, ragService, gitPort, saveReviewUseCase, projectContextProvider)
            runBlocking { reviewRunner.runReview() }
        } finally {
            embeddingClient.close()
        }
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

    // Shutdown hook for resource cleanup
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking { mcpClientManager.closeAll() }
        embeddingClient.close()
    })

    runBlocking { engine.start() }
}

private suspend fun runStandaloneRelease(
    args: Array<String>,
    projectRepository: io.averkhogliad.ai.challenge.week6.domain.port.ProjectRepository,
    generateReleaseNotesUseCase: GenerateReleaseNotesUseCase,
): Int {
    val command = ReleaseMain.parse(args).getOrElse {
        System.err.println("Release mode error: ${it.message}")
        return 1
    }
    if (command.output != null && command.output != "stdout") {
        System.err.println("Release mode error: only --output stdout is supported; release output is not persisted in standalone mode")
        return 1
    }
    val project = when (val result = projectRepository.findById(command.projectId)) {
        is DomainResult.Success -> result.value
        is DomainResult.Failure -> {
            System.err.println(result.error.message)
            return 1
        }
    }
    if (project == null) {
        System.err.println("Project not found: ${command.projectId}")
        return 1
    }
    val progress = generateReleaseNotesUseCase.execute(
        ReleaseRequest(project.id, project.rootPath, command.version, command.range),
    ).toList()
    progress.filterIsInstance<ReleaseProgress.Warning>().forEach { System.err.println("Warning: ${it.message}") }
    val error = progress.filterIsInstance<ReleaseProgress.Error>().firstOrNull()
    if (error != null) {
        System.err.println(error.error.message)
        return 1
    }
    val draft = progress.filterIsInstance<ReleaseProgress.PreviewReady>().firstOrNull()?.draft
    if (draft == null) {
        System.err.println("Release preview was not generated.")
        return 1
    }
    println(draft.markdown)
    return 0
}
