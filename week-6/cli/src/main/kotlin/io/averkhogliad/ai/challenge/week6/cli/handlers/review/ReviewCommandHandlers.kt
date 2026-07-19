package io.averkhogliad.ai.challenge.week6.cli.handlers.review

import io.averkhogliad.ai.challenge.week6.application.pr.CreatePullRequestUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.GetPullRequestDiffUseCase
import io.averkhogliad.ai.challenge.week6.application.pr.ListPullRequestsUseCase
import io.averkhogliad.ai.challenge.week6.application.review.ReviewCodeUseCase
import io.averkhogliad.ai.challenge.week6.cli.rendering.DiffRenderer
import io.averkhogliad.ai.challenge.week6.cli.rendering.ReviewRenderers
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.GitPort
import io.averkhogliad.ai.challenge.week6.domain.pr.PrStatus
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequest
import io.averkhogliad.ai.challenge.week6.domain.pr.PullRequestRepository
import io.averkhogliad.ai.challenge.week6.domain.review.Review
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewRepository
import io.averkhogliad.ai.challenge.week6.domain.review.ReviewTrigger
import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.core.CommandHandler
import java.nio.file.Path

class ReviewCommandHandler(
    private val reviewCodeUseCase: ReviewCodeUseCase,
    private val gitPort: GitPort,
    private val rootPath: () -> Path?,
    private val projectId: () -> String?,
) : CommandHandler {

    override val name: String = "/review"
    override val description: String = "Run code review: /review [base..head]"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/review" || rawInput.startsWith("/review ") &&
                !rawInput.startsWith("/review history") &&
                !rawInput.startsWith("/review show") &&
                !rawInput.startsWith("/review install") &&
                !rawInput.startsWith("/review remove")

    override suspend fun execute(rawInput: String): CommandEffect {
        val pid = projectId() ?: return CommandEffect.Print("No active project. Use /open first.", isError = true)
        val rp = rootPath() ?: return CommandEffect.Print("Cannot determine project root.", isError = true)

        val rangeArg = rawInput.removePrefix("/review").trim()

        val diff: String
        val commitHash: String?
        val branch: String?
        val trigger = if (rangeArg.isEmpty()) ReviewTrigger.AUTO else ReviewTrigger.MANUAL

        if (rangeArg.isEmpty()) {
            // Auto: HEAD~1..HEAD
            val headResult = gitPort.getLastCommitHash(rp)
            if (headResult is DomainResult.Failure) return CommandEffect.Print(
                "Failed to get HEAD: ${headResult.error.message}",
                isError = true
            )

            val headHash = (headResult as DomainResult.Success).value
            val diffResult = gitPort.getDiffBetweenCommits(rp, "$headHash~1", headHash)
            if (diffResult is DomainResult.Failure) return CommandEffect.Print(
                "Failed to get diff: ${diffResult.error.message}",
                isError = true
            )

            diff = (diffResult as DomainResult.Success).value
            commitHash = headHash
            branch = gitPort.getCurrentBranch(rp).getOrNull()
        } else {
            // Manual range
            val parts = rangeArg.split("..")
            val base = parts.getOrElse(0) { "HEAD~1" }
            val head = parts.getOrElse(1) { "HEAD" }

            val diffResult = gitPort.getDiffBetweenCommits(rp, base, head)
            if (diffResult is DomainResult.Failure) return CommandEffect.Print(
                "Failed to get diff: ${diffResult.error.message}",
                isError = true
            )

            diff = (diffResult as DomainResult.Success).value
            commitHash = gitPort.getCurrentCommit(rp).getOrNull()
            branch = gitPort.getCurrentBranch(rp).getOrNull()
        }

        val flow = reviewCodeUseCase.execute(
            projectId = pid,
            diff = diff,
            trigger = trigger,
            commitHash = commitHash,
            branch = branch,
        )

        // Collect flow, save review at end
        return CommandEffect.StreamOutput(flow)
    }
}

class ReviewHistoryCommandHandler(
    private val reviewRepository: ReviewRepository,
    private val reviewRenderers: ReviewRenderers,
    private val projectId: () -> String?,
) : CommandHandler {

    override val name: String = "/review history"
    override val aliases: List<String> = listOf("/review history")
    override val description: String = "Show review history: /review history [--limit N]"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/review history" || rawInput.startsWith("/review history ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val pid = projectId() ?: return CommandEffect.Print("No active project.", isError = true)
        val args = rawInput.removePrefix("/review history").trim()
        val limit = Regex("--limit\\s+(\\d+)").find(args)?.groupValues?.get(1)?.toIntOrNull() ?: 20
        val reviews = reviewRepository.findByProjectId(pid, limit)
        if (reviews.isEmpty()) return CommandEffect.Print("No reviews found.")
        return CommandEffect.Print(reviewRenderers.renderHistoryTable(reviews))
    }
}

class ReviewShowCommandHandler(
    private val reviewRepository: ReviewRepository,
    private val reviewRenderers: ReviewRenderers,
) : CommandHandler {

    override val name: String = "/review show"
    override val aliases: List<String> = listOf("/review show")
    override val description: String = "Show review details: /review show <id>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput.startsWith("/review show ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val reviewId = rawInput.removePrefix("/review show").trim()
        if (reviewId.isEmpty()) return CommandEffect.Print("Usage: /review show <id>", isError = true)

        val review = reviewRepository.findById(reviewId)
            ?: return CommandEffect.Print("Review not found: $reviewId", isError = true)

        return CommandEffect.Print(reviewRenderers.renderDetail(review))
    }
}

class PrCreateHandler(
    private val createPrUseCase: CreatePullRequestUseCase,
    private val rootPath: () -> Path?,
    private val projectId: () -> String?,
) : CommandHandler {

    override val name: String = "/pr create"
    override val aliases: List<String> = listOf("/pr create")
    override val description: String = "Create a PR: /pr create <title> [--source <branch>] [--target <branch>]"

    override fun canHandle(rawInput: String): Boolean =
        rawInput.startsWith("/pr create ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val pid = projectId() ?: return CommandEffect.Print("No active project. Use /open first.", isError = true)
        val rp = rootPath() ?: return CommandEffect.Print("Cannot determine project root.", isError = true)

        val args = rawInput.removePrefix("/pr create").trim()
        val source = Regex("--source\\s+(\\S+)").find(args)?.groupValues?.get(1)
        val target = Regex("--target\\s+(\\S+)").find(args)?.groupValues?.get(1)
        val title = args.replace(Regex("\\s*--source\\s+\\S+\\s*"), " ")
            .replace(Regex("\\s*--target\\s+\\S+\\s*"), " ")
            .trim()

        if (title.isEmpty()) return CommandEffect.Print(
            "Usage: /pr create <title> [--source <branch>] [--target <branch>]",
            isError = true
        )

        val result = createPrUseCase.execute(
            CreatePullRequestUseCase.CreatePrRequest(
                projectId = pid,
                rootPath = rp,
                title = title,
                sourceBranch = source,
                targetBranch = target,
            )
        )

        return when (result) {
            is DomainResult.Success -> CommandEffect.Print("PR created: ${result.value.id.take(8)} (${result.value.sourceBranch} → ${result.value.targetBranch})")
            is DomainResult.Failure -> CommandEffect.Print(result.error.message, isError = true)
        }
    }
}

class PrListHandler(
    private val listPrUseCase: ListPullRequestsUseCase,
    private val projectId: () -> String?,
    private val terminal: com.github.ajalt.mordant.terminal.Terminal,
) : CommandHandler {

    override val name: String = "/pr list"
    override val aliases: List<String> = listOf("/pr list")
    override val description: String = "List PRs: /pr list [--status open|closed|merged|draft]"

    override fun canHandle(rawInput: String): Boolean =
        rawInput == "/pr list" || rawInput.startsWith("/pr list ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val pid = projectId() ?: return CommandEffect.Print("No active project.", isError = true)

        val args = rawInput.removePrefix("/pr list").trim()
        val statusFilter = Regex("--status\\s+(\\S+)").find(args)?.groupValues?.get(1)
            ?.let {
                try {
                    PrStatus.valueOf(it.uppercase())
                } catch (_: Exception) {
                    null
                }
            }

        val prs = listPrUseCase.execute(pid, statusFilter)
        if (prs.isEmpty()) return CommandEffect.Print("No pull requests found.")

        val table = com.github.ajalt.mordant.table.table {
            header { row("ID", "Title", "Source", "Target", "Status") }
            body {
                prs.forEach { pr ->
                    row(pr.id.take(8), pr.title, pr.sourceBranch, pr.targetBranch, pr.status.name)
                }
            }
        }

        return CommandEffect.Print(terminal.render(table))
    }
}

class PrDiffHandler(
    private val getDiffUseCase: GetPullRequestDiffUseCase,
    private val prRepository: PullRequestRepository,
    private val diffRenderer: DiffRenderer,
    private val rootPath: () -> Path?,
) : CommandHandler {

    override val name: String = "/pr diff"
    override val aliases: List<String> = listOf("/pr diff")
    override val description: String = "Show PR diff: /pr diff <id>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput.startsWith("/pr diff ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val prId = rawInput.removePrefix("/pr diff").trim()
        if (prId.isEmpty()) return CommandEffect.Print("Usage: /pr diff <id>", isError = true)

        val rp = rootPath() ?: return CommandEffect.Print("Cannot determine project root.", isError = true)
        val pr = prRepository.findById(prId)
            ?: return CommandEffect.Print("PR not found: $prId", isError = true)

        val diffResult = getDiffUseCase.execute(rp, pr)
        return when (diffResult) {
            is DomainResult.Success -> {
                val summary = diffRenderer.renderSummary(diffResult.value)
                val diff = diffRenderer.render(diffResult.value)
                CommandEffect.Print("$summary\n\n$diff")
            }

            is DomainResult.Failure -> CommandEffect.Print(diffResult.error.message, isError = true)
        }
    }
}

class PrReviewHandler(
    private val reviewCodeUseCase: ReviewCodeUseCase,
    private val prRepository: PullRequestRepository,
    private val gitPort: GitPort,
    private val rootPath: () -> Path?,
    private val projectId: () -> String?,
) : CommandHandler {

    override val name: String = "/pr review"
    override val aliases: List<String> = listOf("/pr review")
    override val description: String = "Review a PR: /pr review <id>"

    override fun canHandle(rawInput: String): Boolean =
        rawInput.startsWith("/pr review ")

    override suspend fun execute(rawInput: String): CommandEffect {
        val prId = rawInput.removePrefix("/pr review").trim()
        if (prId.isEmpty()) return CommandEffect.Print("Usage: /pr review <id>", isError = true)

        val pid = projectId() ?: return CommandEffect.Print("No active project.", isError = true)
        val rp = rootPath() ?: return CommandEffect.Print("Cannot determine project root.", isError = true)

        val pr = prRepository.findById(prId)
            ?: return CommandEffect.Print("PR not found: $prId", isError = true)

        val diffResult = gitPort.getDiffBetweenBranches(rp, pr.sourceBranch, pr.targetBranch)
        val diff = when (diffResult) {
            is DomainResult.Success -> diffResult.value
            is DomainResult.Failure -> return CommandEffect.Print(diffResult.error.message, isError = true)
        }

        val flow = reviewCodeUseCase.execute(
            projectId = pid,
            diff = diff,
            trigger = ReviewTrigger.PR,
            sourceBranch = pr.sourceBranch,
            targetBranch = pr.targetBranch,
            prId = pr.id,
        )

        return CommandEffect.StreamOutput(flow)
    }
}
