package io.averkhogliad.ai.challenge.week3.events.core.repository

import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import kotlinx.datetime.LocalDate
import org.springframework.data.domain.Pageable
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.*

@Repository
class EventRepositoryImpl(
    private val jdbcClient: JdbcClient
) : EventRepositoryCustom {

    override fun findFiltered(from: LocalDate?, to: LocalDate?, pageable: Pageable): List<Event> {
        val baseSql = "SELECT id, date, title, description, created_at FROM event"
        val (whereClause, params) = buildWhereClause(from, to)
        val sql = "$baseSql$whereClause ORDER BY date ASC LIMIT :limit OFFSET :offset"

        lateinit var spec: JdbcClient.StatementSpec
        spec = jdbcClient.sql(sql)
        for ((name, value) in params) {
            spec = spec.param(name, value)
        }
        spec = spec.param("limit", pageable.pageSize)
        spec = spec.param("offset", pageable.offset)

        return spec.query(eventRowMapper).list()
    }

    private val eventRowMapper: (java.sql.ResultSet, Int) -> Event = { rs, _ ->
        Event(
            id = rs.getString("id")?.let { UUID.fromString(it) },
            date = LocalDate.parse(rs.getString("date")),
            title = rs.getString("title"),
            description = rs.getString("description"),
            createdAt = parseInstant(rs.getString("created_at"))
        )
    }

    override fun countFiltered(from: LocalDate?, to: LocalDate?): Long {
        val (whereClause, params) = buildWhereClause(from, to)
        val sql = "SELECT COUNT(*) FROM event$whereClause"

        lateinit var spec: JdbcClient.StatementSpec
        spec = jdbcClient.sql(sql)
        for ((name, value) in params) {
            spec = spec.param(name, value)
        }

        return spec.query(Long::class.java).optional().orElse(0L)
    }

    private fun buildWhereClause(from: LocalDate?, to: LocalDate?): Pair<String, List<Pair<String, String>>> {
        val conditions = mutableListOf<String>()
        val params = mutableListOf<Pair<String, String>>()
        if (from != null) {
            conditions.add("date >= :from")
            params.add("from" to from.toString())
        }
        if (to != null) {
            conditions.add("date <= :to")
            params.add("to" to to.toString())
        }
        val whereClause = if (conditions.isNotEmpty()) " WHERE " + conditions.joinToString(" AND ") else ""
        return whereClause to params
    }

    companion object {
        /**
         * Parses [raw] as Instant, supporting both ISO-8601 strings and epoch-millis timestamps.
         * Spring Data JDBC may store Instant as epoch-millis text when custom converters are not applied.
         */
        private fun parseInstant(raw: String): Instant = try {
            Instant.parse(raw)
        } catch (e: DateTimeParseException) {
            Instant.ofEpochMilli(raw.toLong())
        }
    }
}

