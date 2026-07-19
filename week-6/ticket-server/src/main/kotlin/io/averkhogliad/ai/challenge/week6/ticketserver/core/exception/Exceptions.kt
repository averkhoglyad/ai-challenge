package io.averkhogliad.ai.challenge.week6.ticketserver.core.exception

class TicketNotFoundException(val id: String) : RuntimeException("Ticket not found: $id")

class UserNotFoundException(val id: String) : RuntimeException("User not found: $id")

class ValidationException(
    message: String,
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)
