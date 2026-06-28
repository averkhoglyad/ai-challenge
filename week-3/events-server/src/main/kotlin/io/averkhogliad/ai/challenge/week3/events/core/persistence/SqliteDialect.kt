package io.averkhogliad.ai.challenge.week3.events.core.persistence

import org.springframework.data.relational.core.dialect.*
import org.springframework.data.relational.core.sql.IdentifierProcessing
import org.springframework.data.relational.core.sql.IdentifierProcessing.LetterCasing
import org.springframework.data.relational.core.sql.IdentifierProcessing.Quoting
import org.springframework.data.relational.core.sql.LockOptions

class SqliteDialect : AbstractDialect() {

    override fun lock(): LockClause = LOCK_CLAUSE
    override fun limit(): LimitClause = LIMIT_CLAUSE
    override fun getArraySupport(): ArrayColumns = ARRAY_COLUMNS
    override fun getIdentifierProcessing(): IdentifierProcessing = IDENTIFIER_PROCESSING
    override fun getIdGeneration(): IdGeneration = ID_GENERATION

    companion object {
        val IDENTIFIER_PROCESSING: IdentifierProcessing =
            IdentifierProcessing.create(Quoting.ANSI, LetterCasing.UPPER_CASE)
        val ID_GENERATION: IdGeneration = IdGeneration.create(IDENTIFIER_PROCESSING)
        val ARRAY_COLUMNS: ArrayColumns = object : ArrayColumns {
            override fun isSupported(): Boolean = false
            override fun getArrayType(userType: Class<*>): Class<*> = userType
        }
        val LIMIT_CLAUSE: LimitClause = object : LimitClause {
            override fun getLimit(limit: Long): String = "LIMIT $limit"
            override fun getOffset(offset: Long): String = "OFFSET $offset"
            override fun getLimitOffset(limit: Long, offset: Long): String =
                "LIMIT $limit OFFSET $offset"

            override fun getClausePosition(): LimitClause.Position = LimitClause.Position.AFTER_ORDER_BY
        }
        val LOCK_CLAUSE: LockClause = object : LockClause {
            override fun getLock(lockOptions: LockOptions): String = ""
            override fun getClausePosition(): LockClause.Position = LockClause.Position.AFTER_ORDER_BY
        }
    }
}