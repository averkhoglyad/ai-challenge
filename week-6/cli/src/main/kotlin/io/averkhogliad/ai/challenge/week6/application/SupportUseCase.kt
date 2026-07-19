package io.averkhogliad.ai.challenge.week6.application

import kotlinx.coroutines.flow.Flow

class SupportUseCase(
    private val agentLoopService: AgentLoopService,
) {
    fun execute(query: String): Flow<String> {
        val systemPrompt = """
                You are a customer support assistant.

                1. Use the FAQ/documentation search tool to find answers.
                2. If the user mentions a ticket ID or a specific issue, use the available ticket lookup tools to get context.
                3. If ticket tools return an error or are unavailable, inform the user politely and rely ONLY on the FAQ.
                4. Do not invent ticket statuses.
            """.trimIndent()

        return agentLoopService.processQuery(query, systemPromptOverride = systemPrompt)
    }
}
