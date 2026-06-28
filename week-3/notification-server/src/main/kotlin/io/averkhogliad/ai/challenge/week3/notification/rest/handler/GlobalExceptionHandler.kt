package io.averkhogliad.ai.challenge.week3.notification.rest.handler

import io.averkhogliad.ai.challenge.week3.notification.rest.dto.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val details = mutableMapOf<String, String>()
        ex.bindingResult.fieldErrors.forEach { error ->
            details[error.field] = error.defaultMessage ?: "Invalid value"
        }
        val response = ErrorResponse.validationError(
            message = "Validation failed",
            details = details,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        val details = mutableMapOf<String, String>()
        ex.constraintViolations.forEach { violation ->
            val field = violation.propertyPath.toString().substringAfterLast('.')
            details[field] = violation.message
        }
        val response = ErrorResponse.validationError(
            message = "Validation failed",
            details = details,
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse.validationError(
            message = ex.message ?: "Invalid argument",
            details = emptyMap(),
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse.internalError(ex.message ?: "Internal server error")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
