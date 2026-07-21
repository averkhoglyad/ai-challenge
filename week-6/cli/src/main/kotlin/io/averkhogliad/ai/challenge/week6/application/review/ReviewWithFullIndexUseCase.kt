package io.averkhogliad.ai.challenge.week6.application.review

import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week6.application.GetActiveProjectUseCase
import io.averkhogliad.ai.challenge.week6.application.ProjectContextProvider
import io.averkhogliad.ai.challenge.week6.application.StartupIndexingUseCase
import io.averkhogliad.ai.challenge.week6.application.rag.RagService
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewTrigger

class ReviewWithFullIndexUseCase(
    ragService: RagService,
    gitPort: GitPort,
    llmClient: LlmClient,
    saveReviewUseCase: SaveReviewUseCase,
    projectContextProvider: ProjectContextProvider,
    private val startupIndexingUseCase: StartupIndexingUseCase,
    private val getActiveProjectUseCase: GetActiveProjectUseCase,
) : ReviewFromDbUseCase(ragService, gitPort, llmClient, saveReviewUseCase, projectContextProvider) {

    override suspend fun execute() {
        val ctx = getContext() ?: return

        val activeProject = when (val result = getActiveProjectUseCase.execute()) {
            is DomainResult.Success -> result.value
            is DomainResult.Failure -> null
        }
        if (activeProject != null) {
            println("🔄 Running full reindexing...")
            startupIndexingUseCase.execute(activeProject)
                .collect { /* collect to drive the flow, output goes via StartupIndexingRenderer in CLI mode */ }
        }

        println("🔍 Loading index from database...")
        ragService.loadIndexFromDb(ctx.projectId)
        executePostIndexSteps(ctx, ReviewTrigger.MANUAL)
    }
}
