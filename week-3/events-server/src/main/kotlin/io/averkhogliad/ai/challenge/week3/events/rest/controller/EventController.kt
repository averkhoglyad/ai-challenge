package io.averkhogliad.ai.challenge.week3.events.rest.controller

import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import io.averkhogliad.ai.challenge.week3.events.core.service.EventService
import io.averkhogliad.ai.challenge.week3.events.rest.dto.CreateEventRequest
import io.averkhogliad.ai.challenge.week3.events.rest.dto.PaginatedResponse
import io.averkhogliad.ai.challenge.week3.events.rest.dto.UpdateEventRequest
import jakarta.validation.Valid
import kotlinx.datetime.toKotlinLocalDate
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.*

@RestController
@RequestMapping("/api/v1")
class EventController(
    private val eventService: EventService,
) {
    @PostMapping("/events")
    fun createEvent(@Valid @RequestBody request: CreateEventRequest): ResponseEntity<Event> {
        val event = eventService.createEvent(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(event)
    }

    @GetMapping("/events")
    fun listEvents(
        @RequestParam(
            name = "from_date",
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) fromDate: LocalDate?,
        @RequestParam(
            name = "to_date",
            required = false
        ) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) toDate: LocalDate?,
        @RequestParam(name = "limit", defaultValue = "50") limit: Int,
        @RequestParam(name = "offset", defaultValue = "0") offset: Int,
    ): PaginatedResponse {
        return eventService.listEvents(fromDate?.toKotlinLocalDate(), toDate?.toKotlinLocalDate(), limit, offset)
    }

    @GetMapping("/events/{id}")
    fun getEvent(@PathVariable id: UUID): Event {
        return eventService.getEvent(id)
    }

    @PatchMapping("/events/{id}")
    fun updateEvent(
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateEventRequest,
    ): Event {
        return eventService.updateEvent(id, request)
    }

    @DeleteMapping("/events/{id}")
    fun deleteEvent(@PathVariable id: UUID): ResponseEntity<Void> {
        eventService.deleteEvent(id)
        return ResponseEntity.noContent().build()
    }
}
