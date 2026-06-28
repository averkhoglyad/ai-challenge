package io.averkhogliad.ai.challenge.week3.weather.rest.handler

import io.averkhogliad.ai.challenge.week3.weather.core.exception.CityNotFoundException
import io.averkhogliad.ai.challenge.week3.weather.core.exception.InvalidParametersException
import io.averkhogliad.ai.challenge.week3.weather.core.exception.ProviderUnavailableException
import io.averkhogliad.ai.challenge.week3.weather.core.model.ErrorCode
import io.averkhogliad.ai.challenge.week3.weather.core.model.ErrorDetail
import io.averkhogliad.ai.challenge.week3.weather.core.model.ErrorResponse
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.beans.TypeMismatchException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(CityNotFoundException::class)
    fun handleCityNotFound(ex: CityNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("City not found: {}", ex.message)
        return errorResponse(
            status = HttpStatus.NOT_FOUND,
            code = ErrorCode.NOT_FOUND,
            message = ex.message ?: "City not found",
            details = mapOf("city" to ex.city) + (ex.country?.let { mapOf("country" to it) } ?: emptyMap())
        )
    }

    @ExceptionHandler(InvalidParametersException::class)
    fun handleInvalidParameters(ex: InvalidParametersException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid parameters: {}", ex.message)
        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = ex.message ?: "Invalid parameters",
            details = ex.parameter?.let { mapOf("parameter" to it) }
        )
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameter(ex: MissingServletRequestParameterException): ResponseEntity<ErrorResponse> {
        logger.warn("Missing request parameter: {}", ex.parameterName)
        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = "Required request parameter '${ex.parameterName}' is missing",
            details = mapOf("parameter" to ex.parameterName)
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("Type mismatch for request parameter: {}", ex.name)
        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = "Invalid value for request parameter '${ex.name}'",
            details = mapOf("parameter" to ex.name)
        )
    }

    @ExceptionHandler(TypeMismatchException::class)
    fun handleTypeMismatch(ex: TypeMismatchException): ResponseEntity<ErrorResponse> {
        logger.warn("Type mismatch: {}", ex.message)
        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = "Invalid request parameter value",
            details = ex.propertyName?.let { mapOf("parameter" to it) }
        )
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        logger.warn("Constraint violation: {}", ex.message)
        val paramName = ex.constraintViolations.firstOrNull()?.propertyPath?.toString()?.split(".")?.lastOrNull()
        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = ex.constraintViolations.firstOrNull()?.message ?: "Validation error",
            details = paramName?.let { mapOf("parameter" to it) }
        )
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleHandlerMethodValidation(ex: HandlerMethodValidationException): ResponseEntity<ErrorResponse> {
        logger.warn("Handler method validation: {}", ex.message)
        val paramName = ex.parameterValidationResults.firstOrNull()?.methodParameter?.parameterName
        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = ex.message ?: "Validation error",
            details = paramName?.let { mapOf("parameter" to it) }
        )
    }

    @ExceptionHandler(ProviderUnavailableException::class)
    fun handleProviderUnavailable(ex: ProviderUnavailableException): ResponseEntity<ErrorResponse> {
        logger.error("Provider unavailable: {}", ex.message)
        return errorResponse(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            code = ErrorCode.PROVIDER_UNAVAILABLE,
            message = "Weather provider is currently unavailable",
            details = ex.message?.let { mapOf("reason" to it) }
        )
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Bad request: {}", ex.message)
        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            code = ErrorCode.VALIDATION_ERROR,
            message = ex.message ?: "Invalid parameters"
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Internal error", ex)
        return errorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = ErrorCode.INTERNAL_ERROR,
            message = "An internal error occurred"
        )
    }

    private fun errorResponse(
        status: HttpStatus,
        code: ErrorCode,
        message: String,
        details: Map<String, String>? = null
    ): ResponseEntity<ErrorResponse> = ResponseEntity
        .status(status)
        .body(ErrorResponse(ErrorDetail(code = code, message = message, details = details)))
}
