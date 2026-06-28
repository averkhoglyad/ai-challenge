package io.averkhogliad.ai.challenge.week3.events.core.exception

import java.util.*

class EventNotFoundException(val id: UUID) : RuntimeException("Event not found: $id")
