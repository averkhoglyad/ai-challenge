package io.averkhogliad.ai.challenge.week6.ticketserver.core.repository

import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.User
import org.springframework.stereotype.Repository
import java.util.concurrent.ConcurrentHashMap

@Repository
class UserRepository {

    private val users = ConcurrentHashMap<String, User>()

    init {
        seedData()
    }

    fun findById(id: String): User? = users[id]

    private fun seedData() {
        val users = listOf(
            User(
                id = "USR-001",
                name = "Алексей Петров",
                email = "alexey@example.com",
                company = "ООО Технологии",
                subscriptionTier = "Pro",
            ),
            User(
                id = "USR-002",
                name = "Мария Иванова",
                email = "maria@example.com",
                company = "ИП Иванова М.С.",
                subscriptionTier = "Basic",
            ),
        )
        users.forEach { this.users[it.id] = it }
    }
}
