package io.averkhogliad.ai.challenge.week6.infrastructure.db.repository

import io.averkhogliad.ai.challenge.week6.domain.error.DomainError
import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult
import io.averkhogliad.ai.challenge.week6.domain.port.AppStateRepository
import io.averkhogliad.ai.challenge.week6.infrastructure.db.schema.AppStateTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update

class SqlAppStateRepository : AppStateRepository {

    override suspend fun getValue(key: String): DomainResult<String?> = transaction {
        try {
            val row = AppStateTable.selectAll()
                .where { AppStateTable.key eq key }
                .singleOrNull()
            DomainResult.Success(row?.get(AppStateTable.value))
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun setValue(key: String, value: String): DomainResult<Unit> = transaction {
        try {
            val existing = AppStateTable.selectAll()
                .where { AppStateTable.key eq key }
                .singleOrNull()
            if (existing != null) {
                AppStateTable.update({ AppStateTable.key eq key }) {
                    it[AppStateTable.value] = value
                }
            } else {
                AppStateTable.insert {
                    it[AppStateTable.key] = key
                    it[AppStateTable.value] = value
                }
            }
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }

    override suspend fun removeKey(key: String): DomainResult<Unit> = transaction {
        try {
            AppStateTable.deleteWhere { AppStateTable.key eq key }
            DomainResult.Success(Unit)
        } catch (e: Exception) {
            System.err.println("[REPO] ${e.javaClass.simpleName}: ${e.message}")
            DomainResult.Failure(DomainError.repository(e.message ?: "unknown"))
        }
    }
}
