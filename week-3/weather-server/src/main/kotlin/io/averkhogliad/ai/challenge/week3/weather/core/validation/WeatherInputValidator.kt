package io.averkhogliad.ai.challenge.week3.weather.core.validation

import io.averkhogliad.ai.challenge.week3.weather.core.exception.InvalidParametersException

object WeatherInputValidator {
    const val CITY_MIN_LENGTH = 2
    const val CITY_MAX_LENGTH = 100
    const val COUNTRY_MIN_LENGTH = 2
    const val COUNTRY_MAX_LENGTH = 100
    const val MIN_FORECAST_DAYS = 1
    const val MAX_FORECAST_DAYS = 14
    const val DEFAULT_FORECAST_DAYS = 7

    fun validateCity(city: String?) {
        if (city.isNullOrBlank()) {
            throw InvalidParametersException("City parameter is required", "city")
        }
        if (city.length !in CITY_MIN_LENGTH..CITY_MAX_LENGTH) {
            throw InvalidParametersException(
                "City name must be between $CITY_MIN_LENGTH and $CITY_MAX_LENGTH characters",
                "city"
            )
        }
    }

    fun validateCountry(country: String?) {
        if (country != null && country.length !in COUNTRY_MIN_LENGTH..COUNTRY_MAX_LENGTH) {
            throw InvalidParametersException(
                "Country must be between $COUNTRY_MIN_LENGTH and $COUNTRY_MAX_LENGTH characters",
                "country"
            )
        }
    }

    fun validateDays(days: Int?) {
        if (days != null && days !in MIN_FORECAST_DAYS..MAX_FORECAST_DAYS) {
            throw InvalidParametersException(
                "Days must be between $MIN_FORECAST_DAYS and $MAX_FORECAST_DAYS, got: $days",
                "days"
            )
        }
    }
}
