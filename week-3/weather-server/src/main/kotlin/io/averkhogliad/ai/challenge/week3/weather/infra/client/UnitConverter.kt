package io.averkhogliad.ai.challenge.week3.weather.infra.client

import kotlin.math.round

object UnitConverter {

    private const val KM_PER_HOUR_TO_MPS = 1.0 / 3.6

    /**
     * Конвертирует скорость ветра из км/ч в м/с
     * с округлением до 1 знака после запятой.
     */
    fun kmhToMps(kmh: Double): Double =
        round(kmh * KM_PER_HOUR_TO_MPS * 10.0) / 10.0
}
