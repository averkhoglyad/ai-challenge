package io.averkhogliad.ai.challenge.week3.weather.core.exception

class InvalidParametersException(
    message: String,
    val parameter: String? = null
) : RuntimeException(message)
