package io.averkhogliad.ai.challenge.week3.weather.core.exception

class ProviderUnavailableException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
