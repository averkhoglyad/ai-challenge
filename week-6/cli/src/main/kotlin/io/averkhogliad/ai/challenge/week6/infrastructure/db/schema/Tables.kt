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
    val exclusions: Column<String?> = text("exclusions").nullable()

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

object IndexChunksTable : Table("index_chunks") {
    val id: Column<String> = varchar("id", 36)
    val projectId: Column<String> = varchar("project_id", 36).references(ProjectsTable.id)
    val chunkText: Column<String> = varchar("chunk_text", 8192)
    val sourcePath: Column<String> = varchar("source_path", 2048)
    val embedding: Column<ByteArray> = binary("embedding")
    val model: Column<String> = varchar("model", 255)
    val createdAt: Column<Long> = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ReviewsTable : Table("reviews") {
    val id: Column<String> = varchar("id", 36)
    val projectId: Column<String> = varchar("project_id", 36).references(ProjectsTable.id)
    val trigger: Column<String> = varchar("trigger", 20)
    val commitHash: Column<String?> = varchar("commit_hash", 40).nullable()
    val branch: Column<String?> = varchar("branch", 255).nullable()
    val sourceBranch: Column<String?> = varchar("source_branch", 255).nullable()
    val targetBranch: Column<String?> = varchar("target_branch", 255).nullable()
    val prId: Column<String?> = varchar("pr_id", 36).nullable()
    val summary: Column<String?> = varchar("summary", 4096).nullable()
    val createdAt: Column<Long> = long("created_at")

    override val primaryKey = PrimaryKey(id)
}

object ReviewFindingsTable : Table("review_findings") {
    val id: Column<String> = varchar("id", 36)
    val reviewId: Column<String> = varchar("review_id", 36).references(ReviewsTable.id)
    val category: Column<String> = varchar("category", 30)
    val severity: Column<String> = varchar("severity", 20)
    val file: Column<String?> = varchar("file", 1024).nullable()
    val line: Column<Int?> = integer("line").nullable()
    val description: Column<String> = varchar("description", 4096)
    val recommendation: Column<String?> = varchar("recommendation", 4096).nullable()

    override val primaryKey = PrimaryKey(id)
}

object ReleasesTable : Table("releases") {
    val id: Column<String> = varchar("id", 36)
    val projectId: Column<String> = varchar("project_id", 36).references(ProjectsTable.id)
    val version: Column<String> = varchar("version", 255)
    val previousVersion: Column<String?> = varchar("previous_version", 255).nullable()
    val range: Column<String> = varchar("range", 1024)
    val changelogJson: Column<String> = text("changelog_json")
    val commitsJson: Column<String> = text("commits_json")
    val createdAt: Column<Long> = long("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_releases_project", false, projectId)
        index("idx_releases_version", true, projectId, version)
    }
}

object PullRequestsTable : Table("pull_requests") {
    val id: Column<String> = varchar("id", 36)
    val projectId: Column<String> = varchar("project_id", 36).references(ProjectsTable.id)
    val title: Column<String> = varchar("title", 255)
    val sourceBranch: Column<String> = varchar("source_branch", 255)
    val targetBranch: Column<String> = varchar("target_branch", 255)
    val status: Column<String> = varchar("status", 20)
    val createdAt: Column<Long> = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
