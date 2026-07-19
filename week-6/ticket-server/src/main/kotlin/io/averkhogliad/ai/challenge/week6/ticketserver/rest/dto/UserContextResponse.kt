package io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto

import io.averkhogliad.ai.challenge.week6.ticketserver.core.service.UserService

data class UserContextResponse(
    val userId: String,
    val name: String,
    val email: String,
    val company: String,
    val subscriptionTier: String,
    val openTickets: Int,
    val totalTickets: Int,
) {
    companion object {
        fun from(context: UserService.UserContext): UserContextResponse {
            val user = context.user
            return UserContextResponse(
                userId = user.id,
                name = user.name,
                email = user.email,
                company = user.company,
                subscriptionTier = user.subscriptionTier,
                openTickets = context.openTickets,
                totalTickets = context.totalTickets,
            )
        }
    }
}
