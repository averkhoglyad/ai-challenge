package io.averkhogliad.ai.challenge.week1.domain.context

import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class SummaryPromptBuilderTest {

    @Test
    fun `buildInitialSummaryPrompt should contain instructions`() {
        val messages = listOf(
            ChatMessage(ChatRole.USER, "Hello"),
            ChatMessage(ChatRole.ASSISTANT, "Hi there")
        )

        val prompt = SummaryPromptBuilder.buildInitialSummaryPrompt(messages)

        assertTrue(prompt.contains("summarizer"))
        assertTrue(prompt.contains("Instructions"))
        assertTrue(prompt.contains("Preserve key facts"))
        assertTrue(prompt.contains("Retain entities"))
        assertTrue(prompt.contains("Capture decisions"))
    }

    @Test
    fun `buildInitialSummaryPrompt should contain conversation content`() {
        val messages = listOf(
            ChatMessage(ChatRole.USER, "What is Kotlin?"),
            ChatMessage(ChatRole.ASSISTANT, "Kotlin is a programming language")
        )

        val prompt = SummaryPromptBuilder.buildInitialSummaryPrompt(messages)

        assertTrue(prompt.contains("User: What is Kotlin?"))
        assertTrue(prompt.contains("Assistant: Kotlin is a programming language"))
    }

    @Test
    fun `buildInitialSummaryPrompt should handle empty messages`() {
        val prompt = SummaryPromptBuilder.buildInitialSummaryPrompt(emptyList())

        assertTrue(prompt.contains("(no messages)"))
    }

    @Test
    fun `buildIncrementalSummaryPrompt should contain existing summary`() {
        val existingSummary = "Previous conversation about Kotlin programming"
        val newMessages = listOf(
            ChatMessage(ChatRole.USER, "Tell me more about coroutines")
        )

        val prompt = SummaryPromptBuilder.buildIncrementalSummaryPrompt(existingSummary, newMessages)

        assertTrue(prompt.contains("Existing Summary"))
        assertTrue(prompt.contains(existingSummary))
        assertTrue(prompt.contains("New Messages to Integrate"))
    }

    @Test
    fun `buildIncrementalSummaryPrompt should contain integration instructions`() {
        val prompt = SummaryPromptBuilder.buildIncrementalSummaryPrompt(
            "Existing summary",
            listOf(ChatMessage(ChatRole.USER, "New message"))
        )

        assertTrue(prompt.contains("Integrate, don't replace"))
        assertTrue(prompt.contains("Resolve contradictions"))
        assertTrue(prompt.contains("Preserve chronology"))
    }

    @Test
    fun `buildIncrementalSummaryPrompt should contain new messages`() {
        val newMessages = listOf(
            ChatMessage(ChatRole.USER, "What about Java?"),
            ChatMessage(ChatRole.ASSISTANT, "Java is also a popular language")
        )

        val prompt = SummaryPromptBuilder.buildIncrementalSummaryPrompt("Previous summary", newMessages)

        assertTrue(prompt.contains("User: What about Java?"))
        assertTrue(prompt.contains("Assistant: Java is also a popular language"))
    }

    @Test
    fun `buildIncrementalSummaryPrompt should handle empty new messages`() {
        val prompt = SummaryPromptBuilder.buildIncrementalSummaryPrompt("Summary", emptyList())

        assertTrue(prompt.contains("(no messages)"))
    }

    @Test
    fun `formatConversation should format system messages correctly`() {
        val messages = listOf(
            ChatMessage(ChatRole.SYSTEM, "You are helpful")
        )

        val prompt = SummaryPromptBuilder.buildInitialSummaryPrompt(messages)

        assertTrue(prompt.contains("System: You are helpful"))
    }
}
