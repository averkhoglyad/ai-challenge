package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.EventDto
import java.time.LocalDate
import java.util.*

/**
 * Порт для взаимодействия с Events-сервисом.
 */
interface EventsClient {
    suspend fun createEvent(title: String, date: LocalDate): Result<EventDto>
    suspend fun deleteEvent(id: UUID): Result<Unit>
}
