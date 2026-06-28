package io.averkhogliad.ai.challenge.week3.events.infra.scheduler

import io.averkhogliad.ai.challenge.week3.events.core.service.EventService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@Profile("!demo")
class DailyEventScheduler(
    private val eventService: EventService,
) {

    private val logger = LoggerFactory.getLogger(DailyEventScheduler::class.java)

    @Scheduled(cron = "0 0 9 * * *")
    fun notifyDailyEvents() {
        logger.info("Daily notification triggered (production)")
        eventService.notifyTodayEvents()
    }
}
