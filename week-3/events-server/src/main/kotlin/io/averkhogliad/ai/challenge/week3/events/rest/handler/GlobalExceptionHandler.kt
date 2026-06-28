package io.averkhogliad.ai.challenge.week3.events.rest.handler

import io.averkhogliad.ai.challenge.week3.events.core.exception.EventNotFoundException
import io.averkhogliad.ai.challenge.week3.events.rest.dto.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

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

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse.invalidDateFormat(
            field = ex.name ?: "unknown",
            receivedValue = ex.value?.toString() ?: "null"
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(EventNotFoundException::class)
    fun handleNotFound(ex: EventNotFoundException): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse.notFound(ex.id.toString())
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ErrorResponse> {
        val response = ErrorResponse.internalError(ex.message ?: "Internal server error")
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response)
    }
}
