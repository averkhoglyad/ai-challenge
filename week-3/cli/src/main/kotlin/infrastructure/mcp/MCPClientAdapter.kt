package io.averkhogliad.ai.challenge.week3.cli.infrastructure.mcp

import io.averkhogliad.ai.challenge.week3.cli.domain.model.*
import io.averkhogliad.ai.challenge.week3.cli.domain.model.PromptArgument
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MCPClient
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.Buffer
import kotlinx.io.RawSink
import kotlinx.io.RawSource
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

/**
 * Adapter implementing the domain [MCPClient] port using the Kotlin MCP SDK.
 *
 * Wraps the SDK [Client] and maps between domain models and SDK types.
 * Implements the Adapter pattern from Clean Architecture:
 * - Domain layer defines [MCPClient] port — no SDK dependency.
 * - Infrastructure layer provides [MCPClientAdapter] — bridges domain ↔ SDK.
 *
 * ## SDK mapping
 *
 * | Domain                      | SDK                                              |
 * |-----------------------------|--------------------------------------------------|
 * | [MCPServerConfig]           | [Client] + transport                             |
 * | [MCPTransport.Stdio]        | [StdioClientTransport]                           |
 * | [MCPTransport.StreamableHttp] | [StreamableHttpClientTransport]                |
 * | [MCPTool]                   | [io.modelcontextprotocol.kotlin.sdk.types.Tool]  |
 * | [MCPConnectionState]        | local state tracking                             |
 *
 * ## Error handling
 *
 * - SDK exceptions are caught and mapped to [MCPConnectionState.Failed].
 * - [kotlinx.coroutines.CancellationException] is rethrown for structured concurrency.
 * - [listTools] errors return empty lists and update connection state.
 */
class MCPClientAdapter : MCPClient {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    private val mutex = Mutex()

    @Volatile
    private var sdkClient: Client? = null

    @Volatile
    private var currentState: MCPConnectionState = MCPConnectionState.Disconnected

    // Resources to clean up on disconnect
    private var stdioProcess: Process? = null
    private var httpClient: HttpClient? = null

    override suspend fun connect(config: MCPServerConfig): MCPConnectionState = mutex.withLock {
        currentState = MCPConnectionState.Connecting
        return try {
            val transport = when (val t = config.transport) {
                is MCPTransport.Stdio -> createStdioTransport(t)
                is MCPTransport.StreamableHttp -> createStreamableHttpTransport(t)
            }

            val clientInfo = Implementation(
                name = "ai-challenge-cli",
                version = "1.0.0"
            )
            val client = Client(clientInfo = clientInfo, options = ClientOptions())
            client.connect(transport)
            sdkClient = client

            currentState = MCPConnectionState.Connected(Instant.now())
            currentState
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            cleanupResources()
            currentState = MCPConnectionState.Failed(
                error = e.message ?: "Unknown error",
                since = Instant.now()
            )
            currentState
        }
    }

    override suspend fun disconnect() {
        mutex.withLock {
            try {
                sdkClient?.close()
            } catch (_: Exception) {
                // Ignore errors during disconnect
            } finally {
                cleanupResources()
                sdkClient = null
                currentState = MCPConnectionState.Disconnected
            }
        }
    }

    override suspend fun listTools(): List<MCPTool> {
        val client = sdkClient ?: return emptyList()
        return try {
            val result = client.listTools(request = ListToolsRequest())
            result.tools.map { sdkTool ->
                MCPTool(
                    name = sdkTool.name,
                    description = sdkTool.description,
                    parametersSchema = json.encodeToString(
                        ToolSchema.serializer(),
                        sdkTool.inputSchema
                    )
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            currentState = MCPConnectionState.Failed(
                error = "listTools failed: ${e.message}",
                since = Instant.now()
            )
            emptyList()
        }
    }

    override suspend fun callTool(name: String, arguments: Map<String, Any?>): CallToolResult {
        val client = sdkClient ?: throw IllegalStateException("MCP client not connected")
        return client.callTool(name = name, arguments = arguments)
    }

    override suspend fun listPrompts(): List<McpPrompt> {
        val client = sdkClient ?: return emptyList()
        return try {
            val result = client.listPrompts(request = ListPromptsRequest())
            result.prompts.map { sdkPrompt -> mapPrompt(sdkPrompt) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            currentState = MCPConnectionState.Failed(
                error = "listPrompts failed: ${e.message}",
                since = Instant.now()
            )
            emptyList()
        }
    }

    override suspend fun getPrompt(
        name: String,
        arguments: Map<String, String>
    ): List<McpPromptMessage> {
        val client = sdkClient ?: return emptyList()
        return try {
            val params = GetPromptRequestParams(name = name, arguments = arguments)
            val request = GetPromptRequest(params = params)
            val result = client.getPrompt(request = request)
            result.messages.map { sdkMessage -> mapPromptMessage(sdkMessage) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            currentState = MCPConnectionState.Failed(
                error = "getPrompt failed: ${e.message}",
                since = Instant.now()
            )
            emptyList()
        }
    }

    override fun isConnected(): Boolean = currentState.isConnected()

    override fun getStatus(): MCPConnectionState = currentState

    // ──── Private helpers ────

    /**
     * Maps SDK [io.modelcontextprotocol.kotlin.sdk.types.Prompt] to domain [McpPrompt].
     * Pure function — no I/O, no side effects.
     */
    private fun mapPrompt(sdkPrompt: io.modelcontextprotocol.kotlin.sdk.types.Prompt): McpPrompt {
        return McpPrompt(
            name = sdkPrompt.name,
            description = sdkPrompt.description,
            arguments = sdkPrompt.arguments?.map { sdkArg ->
                PromptArgument(
                    name = sdkArg.name,
                    description = sdkArg.description,
                    required = sdkArg.required ?: true  // MCP spec: absent required means required
                )
            } ?: emptyList()
        )
    }

    /**
     * Maps SDK [io.modelcontextprotocol.kotlin.sdk.types.PromptMessage] to domain [McpPromptMessage].
     * Pure function — no I/O, no side effects.
     */
    private fun mapPromptMessage(sdkMessage: io.modelcontextprotocol.kotlin.sdk.types.PromptMessage): McpPromptMessage {
        val role = when (sdkMessage.role) {
            io.modelcontextprotocol.kotlin.sdk.types.Role.User -> MessageRole.USER
            io.modelcontextprotocol.kotlin.sdk.types.Role.Assistant -> MessageRole.ASSISTANT
        }
        val content = mapContent(sdkMessage.content)
        return McpPromptMessage(role = role, content = content)
    }

    /**
     * Maps SDK [io.modelcontextprotocol.kotlin.sdk.types.ContentBlock] to domain [McpPromptContent].
     * Supports [io.modelcontextprotocol.kotlin.sdk.types.TextContent];
     * all other content types are mapped to [McpPromptContent.Unsupported].
     */
    private fun mapContent(sdkContent: io.modelcontextprotocol.kotlin.sdk.types.ContentBlock): McpPromptContent {
        return when (sdkContent) {
            is TextContent -> McpPromptContent.Text(sdkContent.text)
            else -> McpPromptContent.Unsupported
        }
    }

    // ──── Private helpers ────

    private fun createStdioTransport(t: MCPTransport.Stdio): StdioClientTransport {
        val cmd = mutableListOf<String>().apply {
            add(t.command)
            addAll(t.args)
        }
        val process = ProcessBuilder(cmd).start()
        stdioProcess = process

        val inputSource: Source = InputStreamRawSource(process.inputStream).buffered()
        val outputSink: Sink = OutputStreamRawSink(process.outputStream).buffered()
        val errorSource: Source? = InputStreamRawSource(process.errorStream).buffered()
        val sendChannel = Channel<JSONRPCMessage>(Channel.UNLIMITED)

        return StdioClientTransport(
            input = inputSource,
            output = outputSink,
            error = errorSource,
            sendChannel = sendChannel
        )
    }

    private fun createStreamableHttpTransport(t: MCPTransport.StreamableHttp): StreamableHttpClientTransport {
        val client = HttpClient(CIO)
        httpClient = client
        return StreamableHttpClientTransport(client = client, url = t.url)
    }

    private fun cleanupResources() {
        try {
            stdioProcess?.destroyForcibly()
        } catch (_: Exception) {
            // Ignore
        }
        stdioProcess = null

        try {
            httpClient?.close()
        } catch (_: Exception) {
            // Ignore
        }
        httpClient = null
    }

    // ──── kotlinx.io InputStream/OutputStream adapters ────

    /**
     * Adapts a Java [InputStream] into a kotlinx.io [RawSource].
     *
     * kotlinx-io 0.9.0 keeps [InputStreamSource] private, so we implement
     * [RawSource] manually to bridge Java IO and kotlinx.io.
     */
    private class InputStreamRawSource(private val input: InputStream) : RawSource {

        override fun readAtMostTo(sink: Buffer, byteCount: Long): Long {
            val bufSize = minOf(byteCount, 8192L).toInt()
            val buf = ByteArray(bufSize)
            val bytesRead = input.read(buf)
            if (bytesRead <= 0) return -1
            sink.write(buf, startIndex = 0, endIndex = bytesRead)
            return bytesRead.toLong()
        }

        override fun close() = input.close()
    }

    /**
     * Adapts a Java [OutputStream] into a kotlinx.io [RawSink].
     *
     * kotlinx-io 0.9.0 does not expose a public adapter for OutputStream,
     * so we implement [RawSink] manually.
     */
    private class OutputStreamRawSink(private val output: OutputStream) : RawSink {

        override fun write(source: Buffer, byteCount: Long) {
            var remaining = byteCount
            while (remaining > 0) {
                val chunkSize = minOf(remaining, 8192L).toInt()
                val buf = ByteArray(chunkSize)
                val bytesRead = source.readAtMostTo(buf, startIndex = 0, endIndex = chunkSize)
                if (bytesRead <= 0) break
                output.write(buf, 0, bytesRead)
                remaining -= bytesRead
            }
            output.flush()
        }

        override fun flush() = output.flush()

        override fun close() = output.close()
    }
}
