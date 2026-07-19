package io.averkhogliad.ai.challenge.week6.infrastructure.mcp

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.model.McpServer
import io.averkhogliad.ai.challenge.week6.domain.port.McpClientPort
import io.averkhogliad.ai.challenge.week6.domain.port.McpToolDefinition
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.ClientOptions
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

class KtorMcpClientAdapter : McpClientPort {

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val mutex = Mutex()

    @Volatile
    private var sdkClient: Client? = null

    @Volatile
    private var connected: Boolean = false

    private var httpClient: HttpClient? = null

    override suspend fun connect(server: McpServer): DomainResult<Unit> = mutex.withLock {
        if (connected) return@withLock DomainResult.Success(Unit)

        val url = server.baseUrl
            ?: return@withLock DomainResult.Failure(
                io.averkhogliad.ai.challenge.week6.domain.error.DomainError.invalidUrl("null")
            )

        return@withLock try {
            val client = HttpClient(CIO)
            httpClient = client
            val transport = StreamableHttpClientTransport(client = client, url = url)

            val clientInfo = Implementation(name = "ai-challenge-cli", version = "1.0.0")
            val mcpClient = Client(clientInfo = clientInfo, options = ClientOptions())
            mcpClient.connect(transport)
            sdkClient = mcpClient
            connected = true
            DomainResult.Success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            cleanupResources()
            connected = false
            DomainResult.Failure(
                io.averkhogliad.ai.challenge.week6.domain.error.DomainError.mcpConnectionFailed(
                    server.name, e.message ?: "Unknown error"
                )
            )
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
                connected = false
            }
        }
    }

    override suspend fun listTools(): DomainResult<List<McpToolDefinition>> = mutex.withLock {
        val client = sdkClient
            ?: return@withLock DomainResult.Failure(
                io.averkhogliad.ai.challenge.week6.domain.error.DomainError.mcpConnectionFailed(
                    "unknown", "Not connected"
                )
            )
        return@withLock try {
            val result = client.listTools(request = ListToolsRequest())
            val definitions = result.tools.map { sdkTool ->
                val schemaJson = json.encodeToString(ToolSchema.serializer(), sdkTool.inputSchema)
                val schemaObject = json.parseToJsonElement(schemaJson) as JsonObject
                McpToolDefinition(
                    name = sdkTool.name,
                    description = sdkTool.description,
                    inputSchema = schemaObject,
                )
            }
            DomainResult.Success(definitions)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DomainResult.Failure(
                io.averkhogliad.ai.challenge.week6.domain.error.DomainError.mcpConnectionFailed(
                    "unknown", "listTools failed: ${e.message}"
                )
            )
        }
    }

    override suspend fun callTool(name: String, arguments: JsonObject): DomainResult<String> = mutex.withLock {
        val client = sdkClient
            ?: return@withLock DomainResult.Failure(
                io.averkhogliad.ai.challenge.week6.domain.error.DomainError.mcpConnectionFailed(
                    "unknown", "Not connected"
                )
            )
        return@withLock try {
            val argsMap: Map<String, Any?> = json.decodeFromString<Map<String, Any?>>(
                json.encodeToString(JsonObject.serializer(), arguments)
            )
            val result: CallToolResult = client.callTool(name = name, arguments = argsMap)
            val content = result.content.joinToString("\n") { block ->
                when (block) {
                    is TextContent -> block.text
                    else -> block.toString()
                }
            }
            DomainResult.Success(content)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            DomainResult.Failure(
                io.averkhogliad.ai.challenge.week6.domain.error.DomainError.mcpConnectionFailed(
                    name, "callTool failed: ${e.message}"
                )
            )
        }
    }

    override fun isConnected(): Boolean = connected

    private fun cleanupResources() {
        try {
            httpClient?.close()
        } catch (_: Exception) {
            // Ignore
        }
        httpClient = null
    }
}
