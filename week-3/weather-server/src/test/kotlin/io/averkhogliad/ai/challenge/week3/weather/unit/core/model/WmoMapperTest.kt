package io.averkhogliad.ai.challenge.week3.weather.unit.model

import io.averkhogliad.ai.challenge.week3.weather.core.model.WeatherCondition
import io.averkhogliad.ai.challenge.week3.weather.core.model.WmoMapper
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

class WmoMapperTest : FreeSpec({
    "WmoMapper" - {
        "fromWmoCode" - {
            "0 -> CLEAR_SKY" { WmoMapper.fromWmoCode(0) shouldBe WeatherCondition.CLEAR_SKY }
            "1 -> MAINLY_CLEAR" { WmoMapper.fromWmoCode(1) shouldBe WeatherCondition.MAINLY_CLEAR }
            "2 -> PARTLY_CLOUDY" { WmoMapper.fromWmoCode(2) shouldBe WeatherCondition.PARTLY_CLOUDY }
            "3 -> OVERCAST" { WmoMapper.fromWmoCode(3) shouldBe WeatherCondition.OVERCAST }
            "45 -> FOG" { WmoMapper.fromWmoCode(45) shouldBe WeatherCondition.FOG }
            "48 -> FOG" { WmoMapper.fromWmoCode(48) shouldBe WeatherCondition.FOG }
            "51 -> DRIZZLE" { WmoMapper.fromWmoCode(51) shouldBe WeatherCondition.DRIZZLE }
            "53 -> DRIZZLE" { WmoMapper.fromWmoCode(53) shouldBe WeatherCondition.DRIZZLE }
            "55 -> DRIZZLE" { WmoMapper.fromWmoCode(55) shouldBe WeatherCondition.DRIZZLE }
            "56 -> FREEZING_DRIZZLE" { WmoMapper.fromWmoCode(56) shouldBe WeatherCondition.FREEZING_DRIZZLE }
            "57 -> FREEZING_DRIZZLE" { WmoMapper.fromWmoCode(57) shouldBe WeatherCondition.FREEZING_DRIZZLE }
            "61 -> RAIN" { WmoMapper.fromWmoCode(61) shouldBe WeatherCondition.RAIN }
            "63 -> RAIN" { WmoMapper.fromWmoCode(63) shouldBe WeatherCondition.RAIN }
            "65 -> RAIN" { WmoMapper.fromWmoCode(65) shouldBe WeatherCondition.RAIN }
            "66 -> FREEZING_RAIN" { WmoMapper.fromWmoCode(66) shouldBe WeatherCondition.FREEZING_RAIN }
            "67 -> FREEZING_RAIN" { WmoMapper.fromWmoCode(67) shouldBe WeatherCondition.FREEZING_RAIN }
            "71 -> SNOW" { WmoMapper.fromWmoCode(71) shouldBe WeatherCondition.SNOW }
            "73 -> SNOW" { WmoMapper.fromWmoCode(73) shouldBe WeatherCondition.SNOW }
            "75 -> SNOW" { WmoMapper.fromWmoCode(75) shouldBe WeatherCondition.SNOW }
            "77 -> SNOW" { WmoMapper.fromWmoCode(77) shouldBe WeatherCondition.SNOW }
            "80 -> RAIN_SHOWERS" { WmoMapper.fromWmoCode(80) shouldBe WeatherCondition.RAIN_SHOWERS }
            "81 -> RAIN_SHOWERS" { WmoMapper.fromWmoCode(81) shouldBe WeatherCondition.RAIN_SHOWERS }
            "82 -> RAIN_SHOWERS" { WmoMapper.fromWmoCode(82) shouldBe WeatherCondition.RAIN_SHOWERS }
            "85 -> SNOW_SHOWERS" { WmoMapper.fromWmoCode(85) shouldBe WeatherCondition.SNOW_SHOWERS }
            "86 -> SNOW_SHOWERS" { WmoMapper.fromWmoCode(86) shouldBe WeatherCondition.SNOW_SHOWERS }
            "95 -> THUNDERSTORM" { WmoMapper.fromWmoCode(95) shouldBe WeatherCondition.THUNDERSTORM }
            "96 -> THUNDERSTORM_WITH_HAIL" { WmoMapper.fromWmoCode(96) shouldBe WeatherCondition.THUNDERSTORM_WITH_HAIL }
            "99 -> THUNDERSTORM_WITH_HAIL" { WmoMapper.fromWmoCode(99) shouldBe WeatherCondition.THUNDERSTORM_WITH_HAIL }
            "unknown code -> UNKNOWN" {
                WmoMapper.fromWmoCode(999) shouldBe WeatherCondition.UNKNOWN
                WmoMapper.fromWmoCode(-1) shouldBe WeatherCondition.UNKNOWN
                WmoMapper.fromWmoCode(100) shouldBe WeatherCondition.UNKNOWN
            }
        }
    }
})
