package io.averkhogliad.ai.challenge.week3.events.infra.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import kotlin.time.Clock

@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock = Clock.System
}
