package io.averkhogliad.ai.challenge.week3.weather.infra.config

import org.springframework.core.convert.converter.Converter
import org.springframework.stereotype.Component
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

@Component
class StringToDurationConverter : Converter<String, Duration> {
    override fun convert(source: String): Duration {
        return java.time.Duration.parse(source.trim()).toKotlinDuration()
    }
}

fun Duration.toJavaDuration(): java.time.Duration = java.time.Duration.ofNanos(inWholeNanoseconds)
