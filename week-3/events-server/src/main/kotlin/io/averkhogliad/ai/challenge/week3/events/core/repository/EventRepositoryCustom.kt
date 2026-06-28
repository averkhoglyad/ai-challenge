package io.averkhogliad.ai.challenge.week3.events.core.repository

import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import kotlinx.datetime.LocalDate
import org.springframework.data.domain.Pageable

interface EventRepositoryCustom {
    fun findFiltered(from: LocalDate?, to: LocalDate?, pageable: Pageable): List<Event>
    fun countFiltered(from: LocalDate?, to: LocalDate?): Long
}