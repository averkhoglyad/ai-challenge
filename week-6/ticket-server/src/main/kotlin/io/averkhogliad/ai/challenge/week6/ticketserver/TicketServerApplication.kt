package io.averkhogliad.ai.challenge.week6.ticketserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TicketServerApplication

fun main(args: Array<String>) {
    runApplication<TicketServerApplication>(*args)
}
