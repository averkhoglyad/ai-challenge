package io.averkhogliad.ai.challenge.week3.notification.core.repository

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import org.springframework.data.domain.Pageable
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.*

@Repository
class NotificationRepositoryImpl(
    private val jdbcClient: JdbcClient
) : NotificationRepositoryCustom {

    override fun findAllPaginated(pageable: Pageable): List<Notification> {
        val sql =
            "SELECT id, title, message, created_at FROM notification ORDER BY created_at DESC LIMIT :limit OFFSET :offset"

        return jdbcClient.sql(sql)
            .param("limit", pageable.pageSize)
            .param("offset", pageable.offset)
            .query(notificationRowMapper)
            .list()
    }

    private val notificationRowMapper: (java.sql.ResultSet, Int) -> Notification = { rs, _ ->
        Notification(
            id = rs.getString("id")?.let { UUID.fromString(it) },
            title = rs.getString("title"),
            message = rs.getString("message"),
            createdAt = parseInstant(rs.getString("created_at"))
        )
    }

    override fun countAll(): Long {
        val sql = "SELECT COUNT(*) FROM notification"

        return jdbcClient.sql(sql)
            .query(Long::class.java)
            .optional()
            .orElse(0L)
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
