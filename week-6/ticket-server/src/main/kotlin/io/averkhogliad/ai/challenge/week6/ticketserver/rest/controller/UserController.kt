package io.averkhogliad.ai.challenge.week6.ticketserver.rest.controller

import io.averkhogliad.ai.challenge.week6.ticketserver.core.service.UserService
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.UserContextResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userService: UserService,
) {

    @GetMapping("/{id}/context")
    fun getUserContext(@PathVariable id: String): ResponseEntity<UserContextResponse> {
        val context = userService.getUserContext(id)
        return ResponseEntity.ok(UserContextResponse.from(context))
    }
}
