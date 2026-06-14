package io.averkhogliad.ai.challenge.week1.domain.service

import io.averkhogliad.ai.challenge.week1.domain.Prompt
import io.averkhogliad.ai.challenge.week1.domain.TaskResult
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfig
import io.averkhogliad.ai.challenge.week1.domain.config.ContextCompressionConfigProvider
import io.averkhogliad.ai.challenge.week1.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContext
import io.averkhogliad.ai.challenge.week1.domain.context.DialogContextCompressor
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompressingConversationalAgentTest {

    private val mockLlm = MockLlmPort()
    private val mockRepository = InMemoryDialogRepository()
    private val mockCompressor = MockDialogContextCompressor()
    private val configProvider = ContextCompressionConfigProvider(ContextCompressionConfig())

    // delegate — реальный ConversationalAgent с mock LlmPort и mock Repository
    private val delegateLlm = MockLlmPort()
    private val delegate = ConversationalAgent(delegateLlm, mockRepository)

    private fun createAgent(enabled: Boolean = false): CompressingConversationalAgent {
        configProvider.setEnabled(enabled)
        return CompressingConversationalAgent(
            delegate = delegate,
            compressor = mockCompressor,
            configProvider = configProvider,
            dialogRepository = mockRepository,
            llmPort = mockLlm,
            systemPrompt = "You are helpful"
        )
    }

    @Test
    fun `should delegate when compression is disabled`() = runTest {
        val agent = createAgent(enabled = false)
        delegateLlm.respondWithMessagesSuccess("Delegate response")

        val result = agent.process(
            Prompt("Hello"),
            TaskExecutionConfig(),
            DialogId("test-dialog")
        )

        assertTrue(result is TaskResult.Success)
        assertEquals("Delegate response", (result as TaskResult.Success).content)
        assertEquals(1, delegateLlm.chatWithMessagesCalls.size)
        assertTrue(mockCompressor.compressCalls.isEmpty())
    }

    @Test
    fun `should call compressor when compression is enabled`() = runTest {
        val agent = createAgent(enabled = true)
        mockLlm.respondWithMessagesSuccess("LLM response")
        mockCompressor.setContext(
            DialogContext(
                summary = "Summary",
                recentMessages = listOf(ChatMessage(ChatRole.USER, "Hello")),
                compressedMessageCount = 0
            )
        )

        val result = agent.process(
            Prompt("Hello"),
            TaskExecutionConfig(),
            DialogId("test-dialog")
        )

        assertTrue(result is TaskResult.Success)
        assertEquals(1, mockCompressor.compressCalls.size)
        assertEquals(1, mockLlm.chatWithMessagesCalls.size)
    }

    @Test
    fun `should create new dialog if not found`() = runTest {
        val agent = createAgent(enabled = true)
        mockLlm.respondWithMessagesSuccess("Response")
        mockCompressor.setContext(
            DialogContext(
                summary = null,
                recentMessages = listOf(ChatMessage(ChatRole.USER, "Hello")),
                compressedMessageCount = 0
            )
        )

        agent.process(Prompt("Hello"), TaskExecutionConfig(), DialogId("new-dialog"))

        val savedDialog = mockRepository.findById(DialogId("new-dialog"))
        assertTrue(savedDialog != null)
    }

    @Test
    fun `should throw IllegalArgumentException when dialogId is null`() = runTest {
        val agent = createAgent(enabled = true)

        try {
            agent.process(Prompt("Hello"), TaskExecutionConfig(), null)
            assertTrue(false, "Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("dialogId") == true)
        }
    }

    @Test
    fun `should handle LLM error gracefully`() = runTest {
        val agent = createAgent(enabled = true)
        mockLlm.respondWithMessagesError("LLM failure")
        mockCompressor.setContext(
            DialogContext(
                summary = "Summary",
                recentMessages = listOf(ChatMessage(ChatRole.USER, "Hello")),
                compressedMessageCount = 0
            )
        )

        val result = agent.process(
            Prompt("Hello"),
            TaskExecutionConfig(),
            DialogId("test-dialog")
        )

        assertTrue(result is TaskResult.Error)
    }

    // Mock implementations

    private class MockDialogContextCompressor : DialogContextCompressor {
        private val _compressCalls = mutableListOf<Triple<List<ChatMessage>, ContextCompressionConfig, String?>>()
        val compressCalls: List<Triple<List<ChatMessage>, ContextCompressionConfig, String?>> get() = _compressCalls.toList()

        private var context: DialogContext = DialogContext(null, emptyList(), 0)

        fun setContext(ctx: DialogContext) {
            context = ctx
        }

        override suspend fun compress(
            messages: List<ChatMessage>,
            config: ContextCompressionConfig,
            previousSummary: String?
        ): DialogContext {
            _compressCalls.add(Triple(messages, config, previousSummary))
            return context
        }
    }

    private class InMemoryDialogRepository : DialogRepository {
        private val dialogs = mutableMapOf<String, Dialog>()

        override suspend fun save(dialog: Dialog) {
            dialogs[dialog.id.value] = dialog
        }

        override suspend fun findById(id: DialogId): Dialog? = dialogs[id.value]

        override suspend fun findAll(): List<io.averkhogliad.ai.challenge.week1.domain.model.DialogSummary> =
            emptyList()

        override suspend fun delete(id: DialogId) {
            dialogs.remove(id.value)
        }
    }
}
