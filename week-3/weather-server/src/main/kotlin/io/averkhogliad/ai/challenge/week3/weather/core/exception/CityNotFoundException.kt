package io.averkhogliad.ai.challenge.week3.weather.core.exception

class CityNotFoundException(
    val city: String,
    val country: String? = null
) : RuntimeException(
    "City not found: $city${country?.let { ", $it" } ?: ""}"
)
