package io.averkhogliad.ai.challenge.week3.events.infra.config

import kotlinx.datetime.LocalDate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.convert.converter.Converter
import org.springframework.data.convert.ReadingConverter
import org.springframework.data.convert.WritingConverter
import org.springframework.data.jdbc.core.convert.JdbcCustomConversions
import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.*

@Configuration
class JdbcConverters {

    @Bean
    fun jdbcCustomConversions(): JdbcCustomConversions {
        return JdbcCustomConversions(
            listOf(
                UuidToStringConverter(),
                StringToUuidConverter(),
                LocalDateToStringConverter(),
                StringToLocalDateConverter(),
                InstantToStringConverter(),
                StringToInstantConverter(),
            )
        )
    }

    @WritingConverter
    class UuidToStringConverter : Converter<UUID, String> {
        override fun convert(source: UUID): String = source.toString()
    }

    @ReadingConverter
    class StringToUuidConverter : Converter<String, UUID> {
        override fun convert(source: String): UUID = UUID.fromString(source)
    }

    @WritingConverter
    class LocalDateToStringConverter : Converter<LocalDate, String> {
        override fun convert(source: LocalDate): String = source.toString()
    }

    @ReadingConverter
    class StringToLocalDateConverter : Converter<String, LocalDate> {
        override fun convert(source: String): LocalDate = LocalDate.parse(source)
    }

    @WritingConverter
    class InstantToStringConverter : Converter<Instant, String> {
        override fun convert(source: Instant): String = source.toString()
    }

    @ReadingConverter
    class StringToInstantConverter : Converter<String, Instant> {
        override fun convert(source: String): Instant = try {
            Instant.parse(source)
        } catch (e: DateTimeParseException) {
            Instant.ofEpochMilli(source.toLong())
        }
    }
}
