package io.averkhogliad.ai.challenge.week3.events.core.repository

import io.averkhogliad.ai.challenge.week3.events.core.model.Event
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface EventRepository : CrudRepository<Event, UUID>, EventRepositoryCustom