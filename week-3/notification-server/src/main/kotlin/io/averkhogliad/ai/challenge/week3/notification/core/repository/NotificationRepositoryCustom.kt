package io.averkhogliad.ai.challenge.week3.notification.core.repository

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import org.springframework.data.domain.Pageable

interface NotificationRepositoryCustom {
    fun findAllPaginated(pageable: Pageable): List<Notification>
    fun countAll(): Long
}
