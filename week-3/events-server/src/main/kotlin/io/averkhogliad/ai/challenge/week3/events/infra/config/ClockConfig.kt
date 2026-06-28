package io.averkhogliad.ai.challenge.week3.events.infra.config

import kotlinx.datetime.Clock
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.System
}
