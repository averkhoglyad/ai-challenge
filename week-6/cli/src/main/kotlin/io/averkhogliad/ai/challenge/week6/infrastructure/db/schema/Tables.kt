package io.averkhogliad.ai.challenge.week6.infrastructure.db.schema

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

object ProjectsTable : Table("projects") {
    val id: Column<String> = varchar("id", 36)
    val name: Column<String> = varchar("name", 255)
    val rootPath: Column<String> = varchar("root_path", 1024)
    val docsPath: Column<String?> = varchar("docs_path", 1024).nullable()
    val faqPath: Column<String?> = varchar("faq_path", 1024).nullable()
    val createdAt: Column<Long> = long("created_at")
    val updatedAt: Column<Long> = long("updated_at")

    override val primaryKey = PrimaryKey(id)
}

object McpServersTable : Table("mcp_servers") {
    val id: Column<String> = varchar("id", 36)
    val name: Column<String> = varchar("name", 255)
    val serverType: Column<String> = varchar("server_type", 50)
    val baseUrl: Column<String?> = varchar("base_url", 2048).nullable()
    val transportConfig: Column<String?> = varchar("transport_config", 4096).nullable()
    val enabled: Column<Int> = integer("enabled").default(1)
    val createdAt: Column<Long> = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object AppStateTable : Table("app_state") {
    val key: Column<String> = varchar("key", 255)
    val value: Column<String> = varchar("value", 4096)

    override val primaryKey = PrimaryKey(key)
}
