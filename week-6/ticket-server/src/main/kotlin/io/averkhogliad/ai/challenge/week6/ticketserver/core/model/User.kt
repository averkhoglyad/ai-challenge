package io.averkhogliad.ai.challenge.week6.ticketserver.core.model

data class User(
    val id: String,
    val name: String,
    val email: String,
    val company: String,
    val subscriptionTier: String,
)
