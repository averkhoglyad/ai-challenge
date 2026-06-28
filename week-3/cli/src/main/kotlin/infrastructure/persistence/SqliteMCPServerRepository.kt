package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.ModelId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPServerConfig
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MCPTransport
import io.averkhogliad.ai.challenge.week3.cli.domain.service.MCPServerRepository
import kotlinx.serialization.json.*
import java.sql.ResultSet
import java.time.Instant

/**
 * Реализация [MCPServerRepository] на SQLite.
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализует port из domain-слоя
 * - **Hexagonal Architecture** — адаптер для persistence
 *
 * ## Схема БД
 * - Таблица `mcp_servers`: id, name, transport_type, transport_config, enabled, created_at
 *
 * ## Особенности
 * - Автоматическое создание схемы при инициализации
 * - Файл БД задаётся через конструктор (общий с другими репозиториями)
 * - Поддержка транзакций для атомарности операций
 * - Сериализация transport_config через kotlinx.serialization.json.Json
 *
 * @param database единый владелец SQLite JDBC-соединения
 */
class SqliteMCPServerRepository(
    private val database: SqliteDatabase
) : MCPServerRepository {

    private val connection get() = database.connection

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    init {
        initializeSchema()
    }

    /**
     * Создаёт таблицу mcp_servers, если она не существует.
     */
    private fun initializeSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS mcp_servers (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    transport_type TEXT NOT NULL CHECK(transport_type IN ('STDIO', 'STREAMABLE_HTTP')),
                    transport_config TEXT NOT NULL,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    override suspend fun save(config: MCPServerConfig): MCPServerConfig {
        connection.autoCommit = false
        try {
            val sql = """
                INSERT INTO mcp_servers (id, name, transport_type, transport_config, enabled, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    name = excluded.name,
                    transport_type = excluded.transport_type,
                    transport_config = excluded.transport_config,
                    enabled = excluded.enabled,
                    created_at = excluded.created_at
            """.trimIndent()

            val (transportType, transportConfigJson) = serializeTransport(config.transport)

            try {
                connection.prepareStatement(sql).use { stmt ->
                    stmt.setString(1, config.id.value)
                    stmt.setString(2, config.name)
                    stmt.setString(3, transportType)
                    stmt.setString(4, transportConfigJson)
                    stmt.setInt(5, if (config.enabled) 1 else 0)
                    stmt.setString(6, config.createdAt.toString())
                    stmt.executeUpdate()
                }
            } catch (e: java.sql.SQLException) {
                if (e.message?.contains("UNIQUE constraint failed") == true && e.message?.contains(".name") == true) {
                    throw IllegalStateException("Server with name '${config.name}' already exists")
                }
                throw e
            }

            connection.commit()
            return config
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun findById(id: ModelId): MCPServerConfig? {
        val sql = """
            SELECT id, name, transport_config, enabled, created_at
            FROM mcp_servers
            WHERE id = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, id.value)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapRowToMCPServerConfig(rs) else null
            }
        }
    }

    override suspend fun findByName(name: String): MCPServerConfig? {
        val sql = """
            SELECT id, name, transport_config, enabled, created_at
            FROM mcp_servers
            WHERE name = ?
        """.trimIndent()

        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs ->
                if (rs.next()) mapRowToMCPServerConfig(rs) else null
            }
        }
    }

    override suspend fun findAll(): List<MCPServerConfig> {
        val sql = """
            SELECT id, name, transport_config, enabled, created_at
            FROM mcp_servers
            ORDER BY created_at DESC
        """.trimIndent()

        return connection.createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                val servers = mutableListOf<MCPServerConfig>()
                while (rs.next()) {
                    servers.add(mapRowToMCPServerConfig(rs))
                }
                servers
            }
        }
    }

    override suspend fun delete(id: ModelId) {
        connection.autoCommit = false
        try {
            val sql = "DELETE FROM mcp_servers WHERE id = ?"
            connection.prepareStatement(sql).use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }
            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = true
        }
    }

    override suspend fun existsByName(name: String): Boolean {
        val sql = "SELECT 1 FROM mcp_servers WHERE name = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            stmt.executeQuery().use { rs -> rs.next() }
        }
    }

    // ── JSON serialization ──────────────────────────────────────────────

    /**
     * Сериализует [MCPTransport] в пару (transport_type, transport_config_json).
     */
    private fun serializeTransport(transport: MCPTransport): Pair<String, String> {
        return when (transport) {
            is MCPTransport.Stdio -> {
                val jsonObj = JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("STDIO"),
                        "command" to JsonPrimitive(transport.command),
                        "args" to JsonArray(transport.args.map { JsonPrimitive(it) })
                    )
                )
                "STDIO" to json.encodeToString(JsonObject.serializer(), jsonObj)
            }

            is MCPTransport.StreamableHttp -> {
                val jsonObj = JsonObject(
                    mapOf(
                        "type" to JsonPrimitive("STREAMABLE_HTTP"),
                        "url" to JsonPrimitive(transport.url)
                    )
                )
                "STREAMABLE_HTTP" to json.encodeToString(JsonObject.serializer(), jsonObj)
            }
        }
    }

    /**
     * Десериализует transport_config JSON обратно в [MCPTransport].
     */
    private fun deserializeTransport(jsonString: String): MCPTransport {
        val jsonObj = json.parseToJsonElement(jsonString).jsonObject
        return when (val type = jsonObj["type"]?.jsonPrimitive?.content) {
            "STDIO" -> MCPTransport.Stdio(
                command = jsonObj["command"]?.jsonPrimitive?.content
                    ?: error("Missing 'command' in STDIO transport config"),
                args = jsonObj["args"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            )

            "STREAMABLE_HTTP" -> MCPTransport.StreamableHttp(
                url = jsonObj["url"]?.jsonPrimitive?.content
                    ?: error("Missing 'url' in STREAMABLE_HTTP transport config")
            )

            else -> error("Unknown transport type: $type")
        }
    }

    // ── Row mapping ─────────────────────────────────────────────────────

    /**
     * Маппит строку ResultSet в доменную модель MCPServerConfig.
     */
    private fun mapRowToMCPServerConfig(rs: ResultSet): MCPServerConfig {
        return MCPServerConfig(
            id = ModelId(rs.getString("id")),
            name = rs.getString("name"),
            transport = deserializeTransport(rs.getString("transport_config")),
            enabled = rs.getInt("enabled") == 1,
            createdAt = Instant.parse(rs.getString("created_at"))
        )
    }
}
