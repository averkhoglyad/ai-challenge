package io.averkhogliad.ai.challenge.week6.infrastructure.db.schema

import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Table

object ProjectSourcesTable : Table("project_sources") {
    val id: Column<String> = varchar("id", 36)
    val projectId: Column<String> = varchar("project_id", 36).references(ProjectsTable.id)
    val path: Column<String> = varchar("path", 2048)
    val sourceType: Column<String> = varchar("source_type", 20)
    val isDefault: Column<Int> = integer("is_default").default(0)
    val createdAt: Column<Long> = long("created_at")

    override val primaryKey = PrimaryKey(id)
}
