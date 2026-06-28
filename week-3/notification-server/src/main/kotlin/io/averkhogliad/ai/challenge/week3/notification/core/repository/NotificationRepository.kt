package io.averkhogliad.ai.challenge.week3.notification.core.repository

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import org.springframework.stereotype.Repository
import java.util.*
import org.springframework.data.repository.Repository as SpringRepository

@Repository
interface NotificationRepository : SpringRepository<Notification, UUID>, NotificationRepositoryCustom {
    fun save(entity: Notification): Notification
}
