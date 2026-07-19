package io.averkhogliad.ai.challenge.week3.events.core.service

import io.averkhogliad.ai.challenge.week3.events.core.exception.EventNotFoundException
import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import io.averkhogliad.ai.challenge.week3.events.core.repository.EventRepository
import io.averkhogliad.ai.challenge.week3.events.infra.client.NotificationClient
import io.averkhogliad.ai.challenge.week3.events.rest.dto.CreateEventRequest
import io.averkhogliad.ai.challenge.week3.events.rest.dto.PaginatedResponse
import io.averkhogliad.ai.challenge.week3.events.rest.dto.PaginationMeta
import io.averkhogliad.ai.challenge.week3.events.rest.dto.UpdateEventRequest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.util.*
import kotlin.time.Clock

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val notificationClient: NotificationClient,
    private val clock: Clock,
) {

    private val logger = LoggerFactory.getLogger(EventService::class.java)

    fun createEvent(request: CreateEventRequest): Event {
        val event = eventRepository.save(
            Event(
                date = request.date,
                title = request.title,
                description = request.description,
            )
        )
        sendNotification(
            title = "Событие создано",
            message = "Событие '${event.title}' (дата: ${event.date}) создано"
        )
        return event
    }

    fun getEvent(id: UUID): Event {
        return eventRepository.findById(id)
            .orElseThrow { EventNotFoundException(id) }
    }

    fun listEvents(fromDate: LocalDate?, toDate: LocalDate?, limit: Int, offset: Int): PaginatedResponse {
        require(limit in 1..100) { "Limit must be between 1 and 100, got: $limit" }
        require(offset >= 0) { "Offset must be non-negative, got: $offset" }
        val pageable = OffsetPageRequest(offset.toLong(), limit)
        val items = eventRepository.findFiltered(fromDate, toDate, pageable)
        val total = eventRepository.countFiltered(fromDate, toDate)
        return PaginatedResponse(
            items = items,
            meta = PaginationMeta(total = total, limit = limit, offset = offset),
        )
    }

    fun updateEvent(id: UUID, request: UpdateEventRequest): Event {
        val existing = getEvent(id)
        val updated = existing.copy(
            date = request.date ?: existing.date,
            title = request.title ?: existing.title,
            description = request.description ?: existing.description,
        )
        val saved = eventRepository.save(updated)
        sendNotification(
            title = "Событие обновлено",
            message = "Событие '${saved.title}' (дата: ${saved.date}) обновлено"
        )
        return saved
    }

    fun deleteEvent(id: UUID) {
        if (!eventRepository.existsById(id)) {
            throw EventNotFoundException(id)
        }
        eventRepository.deleteById(id)
        sendNotification(
            title = "Событие удалено",
            message = "Событие с ID '$id' удалено"
        )
    }

    private fun sendNotification(title: String, message: String) {
        try {
            notificationClient.send(title, message)
        } catch (e: Exception) {
            logger.error("Failed to send notification: title={}, error={}", title, e.message, e)
        }
    }

    fun notifyTodayEvents() {
        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val pageable = OffsetPageRequest(0, 100) // MVP: лимит 100 событий в daily-уведомлении
        val events = eventRepository.findFiltered(today, today, pageable)

        if (events.isEmpty()) {
            logger.debug("No events for today ({})", today)
            return
        }

        val message = buildString {
            appendLine("📅 События на $today:")
            events.forEachIndexed { index, event ->
                appendLine("${index + 1}. ${event.title} (дата: ${event.date})")
            }
        }.trimEnd()

        logger.info("Sending daily notification with {} events", events.size)
        sendNotification(
            title = "События на сегодня",
            message = message
        )
    }
}

private data class OffsetPageRequest(
    private val offset: Long,
    private val limit: Int,
) : Pageable {
    override fun getPageNumber(): Int = 0
    override fun getPageSize(): Int = limit
    override fun getOffset(): Long = offset
    override fun getSort(): Sort = Sort.unsorted()
    override fun next(): Pageable = OffsetPageRequest(offset + limit, limit)
    override fun previousOrFirst(): Pageable = OffsetPageRequest(maxOf(0, offset - limit), limit)
    override fun first(): Pageable = OffsetPageRequest(0, limit)
    override fun hasPrevious(): Boolean = offset > 0
    override fun withPage(pageNumber: Int): Pageable = OffsetPageRequest(pageNumber.toLong() * limit, limit)
}
