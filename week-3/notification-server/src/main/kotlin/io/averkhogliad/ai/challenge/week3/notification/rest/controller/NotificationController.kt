package io.averkhogliad.ai.challenge.week3.notification.rest.controller

import io.averkhogliad.ai.challenge.week3.notification.core.model.Notification
import io.averkhogliad.ai.challenge.week3.notification.core.service.NotificationService
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.CreateNotificationRequest
import io.averkhogliad.ai.challenge.week3.notification.rest.dto.PaginatedResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1")
class NotificationController(
    private val notificationService: NotificationService,
) {
    @PostMapping("/notifications")
    fun createNotification(@Valid @RequestBody request: CreateNotificationRequest): ResponseEntity<Notification> {
        val notification = notificationService.createNotification(request)
        return ResponseEntity.status(HttpStatus.CREATED).body(notification)
    }

    @GetMapping("/notifications")
    fun listNotifications(
        @RequestParam(name = "limit", defaultValue = "50") limit: Int,
        @RequestParam(name = "offset", defaultValue = "0") offset: Int,
    ): PaginatedResponse {
        return notificationService.listNotifications(limit, offset)
    }
}
