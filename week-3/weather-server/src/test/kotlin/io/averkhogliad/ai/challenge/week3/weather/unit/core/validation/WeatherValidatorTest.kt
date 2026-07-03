package io.averkhogliad.ai.challenge.week3.weather.unit.core.validation

import io.averkhogliad.ai.challenge.week3.weather.core.exception.InvalidParametersException
import io.averkhogliad.ai.challenge.week3.weather.core.validation.WeatherInputValidator
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class WeatherValidatorTest : FreeSpec({

    "validateCity" - {
        "accepts valid city name" { shouldNotThrowAny { WeatherInputValidator.validateCity("London") } }
        "accepts city with 2 characters" { shouldNotThrowAny { WeatherInputValidator.validateCity("Ab") } }
        "accepts city with exactly 100 characters" {
            shouldNotThrowAny {
                WeatherInputValidator.validateCity(
                    "A".repeat(
                        100
                    )
                )
            }
        }

        "throws for null city" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateCity(null) }.parameter shouldBe "city"
        }
        "throws for blank city" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateCity("   ") }.parameter shouldBe "city"
        }
        "throws for empty city" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateCity("") }.parameter shouldBe "city"
        }
        "throws for city shorter than 2 characters" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateCity("A") }.parameter shouldBe "city"
        }
        "throws for city longer than 100 characters" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateCity("A".repeat(101)) }.parameter shouldBe "city"
        }
    }

    "validateCountry" - {
        "accepts valid country" { shouldNotThrowAny { WeatherInputValidator.validateCountry("GB") } }
        "accepts null country" { shouldNotThrowAny { WeatherInputValidator.validateCountry(null) } }

        "throws for country shorter than 2 characters" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateCountry("X") }.parameter shouldBe "country"
        }
        "throws for country longer than 100 characters" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateCountry("A".repeat(101)) }.parameter shouldBe "country"
        }
    }

    "validateDays" - {
        "accepts valid days (1)" { shouldNotThrowAny { WeatherInputValidator.validateDays(1) } }
        "accepts valid days (14)" { shouldNotThrowAny { WeatherInputValidator.validateDays(14) } }
        "accepts valid days (7)" { shouldNotThrowAny { WeatherInputValidator.validateDays(7) } }
        "accepts null days" { shouldNotThrowAny { WeatherInputValidator.validateDays(null) } }

        "throws for days < 1" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateDays(0) }.parameter shouldBe "days"
        }
        "throws for days > 14" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateDays(15) }.parameter shouldBe "days"
        }
        "throws for negative days" {
            shouldThrow<InvalidParametersException> { WeatherInputValidator.validateDays(-1) }.parameter shouldBe "days"
        }
    }
})
