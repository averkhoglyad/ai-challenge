package io.averkhogliad.ai.challenge.week6.ticketserver.core.repository

import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.Ticket
import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.TicketPriority
import io.averkhogliad.ai.challenge.week6.ticketserver.core.model.TicketStatus
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Repository
class TicketRepository {

    private val tickets = ConcurrentHashMap<String, Ticket>()

    init {
        seedData()
    }

    fun findById(id: String): Ticket? = tickets[id]

    fun findAll(): Collection<Ticket> = tickets.values

    private fun seedData() {
        val now = Instant.parse("2026-01-20T10:00:00Z")
        val tickets = listOf(
            Ticket(
                id = "TKT-1001",
                userId = "USR-001",
                subject = "Не могу войти в личный кабинет после обновления",
                description = "После вчерашнего обновления системы не могу авторизоваться. Ввожу логин и пароль — страница перезагружается без ошибок. Пробовал чистить куки и менять браузер — не помогает.",
                status = TicketStatus.OPEN,
                priority = TicketPriority.HIGH,
                createdAt = now,
                updatedAt = now,
            ),
            Ticket(
                id = "TKT-1002",
                userId = "USR-001",
                subject = "Двойное списание за подписку в этом месяце",
                description = "Заметил, что 15 января с карты дважды списали 499₽ за подписку Pro. Прикладываю скриншоты транзакций из банковского приложения.",
                status = TicketStatus.IN_PROGRESS,
                priority = TicketPriority.CRITICAL,
                createdAt = now.minusSeconds(86_400 * 2),
                updatedAt = now,
            ),
            Ticket(
                id = "TKT-1003",
                userId = "USR-002",
                subject = "Не отображается статистика за декабрь",
                description = "В разделе аналитики пустой график за декабрь 2025. Данные за ноябрь и январь отображаются корректно.",
                status = TicketStatus.OPEN,
                priority = TicketPriority.MEDIUM,
                createdAt = now.minusSeconds(86_400),
                updatedAt = now.minusSeconds(86_400),
            ),
            Ticket(
                id = "TKT-1004",
                userId = "USR-002",
                subject = "Добавить тёмную тему в веб-интерфейс",
                description = "Работаю часто по ночам, белый фон сильно напрягает глаза. Есть ли планы по добавлению тёмной темы?",
                status = TicketStatus.CLOSED,
                priority = TicketPriority.LOW,
                createdAt = now.minusSeconds(86_400 * 5),
                updatedAt = now.minusSeconds(86_400 * 2),
                resolution = "Тёмная тема запланирована в релизе v2.5 (конец февраля). Проголосовало 147 пользователей.",
            ),
            Ticket(
                id = "TKT-1005",
                userId = "USR-001",
                subject = "Ошибка при экспорте отчёта в PDF",
                description = "При попытке экспортировать отчёт за период 01.01-15.01 в PDF файл создаётся пустым (0 байт). В CSV экспортируется нормально.",
                status = TicketStatus.RESOLVED,
                priority = TicketPriority.HIGH,
                createdAt = now.minusSeconds(86_400 * 3),
                updatedAt = now,
                resolution = "Исправлено в хотфиксе v2.4.1. Проблема была в кодировке шрифтов.",
            ),
        )
        tickets.forEach { this.tickets[it.id] = it }
    }
}
