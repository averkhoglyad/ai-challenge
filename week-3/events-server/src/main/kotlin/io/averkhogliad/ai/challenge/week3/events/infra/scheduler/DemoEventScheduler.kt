package io.averkhogliad.ai.challenge.week3.events.infra.scheduler

import io.averkhogliad.ai.challenge.week3.events.core.service.EventService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("demo")
class DemoEventScheduler(
    private val eventService: EventService,
) {

    private val logger = LoggerFactory.getLogger(DemoEventScheduler::class.java)

    @Scheduled(fixedRate = 60_000)
    fun notifyDemoEvents() {
        logger.info("Demo notification triggered")
        eventService.notifyTodayEvents()
    }
}
