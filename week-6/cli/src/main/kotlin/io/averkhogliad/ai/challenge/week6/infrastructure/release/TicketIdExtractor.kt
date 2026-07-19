package io.averkhogliad.ai.challenge.week6.infrastructure.release

class TicketIdExtractor {

    fun extract(message: String): List<String> = ticketPatterns
        .flatMap { pattern -> pattern.findAll(message).map { it.groupValues[1] }.toList() }
        .distinct()

    private companion object {
        val ticketPatterns = listOf(
            Regex("(?<![A-Za-z0-9])(#\\d+)\\b"),
            Regex("\\b([A-Z][A-Z0-9]+-\\d+)\\b"),
        )
    }
}
