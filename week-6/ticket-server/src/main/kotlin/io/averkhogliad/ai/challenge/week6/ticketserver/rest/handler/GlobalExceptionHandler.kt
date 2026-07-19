package io.averkhogliad.ai.challenge.week6.ticketserver.rest.handler

import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.TicketNotFoundException
import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.UserNotFoundException
import io.averkhogliad.ai.challenge.week6.ticketserver.core.exception.ValidationException
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.ErrorDetails
import io.averkhogliad.ai.challenge.week6.ticketserver.rest.dto.ErrorResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(
                ErrorResponse(
                    error = ErrorDetails(
                        code = "VALIDATION_ERROR",
                        message = ex.message ?: "Validation failed",
                        details = ex.details.ifEmpty { null },
                    )
                )
            )
    }

    @ExceptionHandler(TicketNotFoundException::class)
    fun handleTicketNotFound(ex: TicketNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    error = ErrorDetails(
                        code = "NOT_FOUND",
                        message = "Ticket not found: ${ex.id}",
                    )
                )
            )
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFound(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(
                ErrorResponse(
                    error = ErrorDetails(
                        code = "NOT_FOUND",
                        message = "User not found: ${ex.id}",
                    )
                )
            )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        log.error("Unexpected error", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ErrorResponse(
                    error = ErrorDetails(
                        code = "INTERNAL_ERROR",
                        message = "Internal server error",
                    )
                )
            )
    }
}
