package io.averkhogliad.ai.challenge.week3.weather.infra.config

import org.springframework.beans.factory.annotation.Qualifier

@Qualifier("currentWeatherCache")
@Retention(AnnotationRetention.RUNTIME)
@Target(
    AnnotationTarget.FIELD,
    AnnotationTarget.VALUE_PARAMETER,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.ANNOTATION_CLASS
)
annotation class CurrentWeatherCache
