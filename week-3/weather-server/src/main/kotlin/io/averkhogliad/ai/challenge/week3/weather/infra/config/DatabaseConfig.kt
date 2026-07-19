package io.averkhogliad.ai.challenge.week3.weather.infra.config

import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DatabaseConfig(
    @Value("\${weather.database.url}") private val url: String
) {
    @Bean
    fun database(): Database {
        return Database.connect(url, "org.sqlite.JDBC")
    }
}
