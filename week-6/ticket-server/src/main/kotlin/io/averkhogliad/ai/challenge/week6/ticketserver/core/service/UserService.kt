package io.averkhogliad.ai.challenge.week6.ticketserver.core.service

import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.UserNotFoundException
import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.User
import io.averkhogliad.ai.challenge.week6.ticketserver.core.repository.TicketRepository
import io.averkhogliad.ai.challenge.week6.ticketserver.core.repository.UserRepository
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val ticketRepository: TicketRepository,
) {

    fun getUserContext(userId: String): UserContext {
        val user = userRepository.findById(userId)
            ?: throw UserNotFoundException(userId)

        val userTickets = ticketRepository.findAll()
            .filter { it.userId == userId }

        return UserContext(
            user = user,
            openTickets = userTickets.count { it.status == io.averkhogliad.ai.challenge.week6.ticketserver.core.model.TicketStatus.OPEN },
            totalTickets = userTickets.size,
        )
    }

    data class UserContext(
        val user: User,
        val openTickets: Int,
        val totalTickets: Int,
    )
}
