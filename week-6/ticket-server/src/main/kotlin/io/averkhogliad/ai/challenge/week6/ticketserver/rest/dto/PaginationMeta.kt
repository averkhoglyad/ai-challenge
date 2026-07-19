package io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto

data class PaginationMeta(
    val total: Int,
    val limit: Int,
    val offset: Int,
)
