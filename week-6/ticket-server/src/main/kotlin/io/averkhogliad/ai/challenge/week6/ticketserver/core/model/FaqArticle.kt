package io.averkhogliad.ai.challenge.week6.ticketserver.core.model

data class FaqArticle(
    val id: String,
    val title: String,
    val content: String,
    val tags: List<String>,
)
