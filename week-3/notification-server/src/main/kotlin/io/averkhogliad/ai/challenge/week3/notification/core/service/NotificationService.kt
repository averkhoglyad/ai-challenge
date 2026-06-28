package io.averkhogliad.ai.challenge.week3.notification.core.service

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import io.averkhogliad.ai.challenge.week3.notification.core.repository.NotificationRepository
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.CreateNotificationRequest
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginatedResponse
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginationMeta
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val notificationRepository: NotificationRepository,
) {
    fun createNotification(request: CreateNotificationRequest): Notification {
        return notificationRepository.save(
            Notification(
                title = request.title,
                message = request.message,
            )
        )
    }

    fun listNotifications(limit: Int, offset: Int): PaginatedResponse {
        require(limit in 1..100) { "Limit must be between 1 and 100, got: $limit" }
        require(offset >= 0) { "Offset must be non-negative, got: $offset" }
        val pageable = OffsetPageRequest(offset.toLong(), limit)
        val items = notificationRepository.findAllPaginated(pageable)
        val total = notificationRepository.countAll()
        return PaginatedResponse(
            items = items,
            meta = PaginationMeta(total = total, limit = limit, offset = offset),
        )
    }
}

private data class OffsetPageRequest(
    private val offset: Long,
    private val limit: Int,
) : Pageable {
    override fun getPageNumber(): Int = 0
    override fun getPageSize(): Int = limit
    override fun getOffset(): Long = offset
    override fun getSort(): Sort = Sort.unsorted()
    override fun next(): Pageable = OffsetPageRequest(offset + limit, limit)
    override fun previousOrFirst(): Pageable = OffsetPageRequest(maxOf(0, offset - limit), limit)
    override fun first(): Pageable = OffsetPageRequest(0, limit)
    override fun hasPrevious(): Boolean = offset > 0
    override fun withPage(pageNumber: Int): Pageable = OffsetPageRequest(pageNumber.toLong() * limit, limit)
}
