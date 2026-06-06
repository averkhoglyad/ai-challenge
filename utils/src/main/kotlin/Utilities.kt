package io.averkhogliad.ai.challenge.utils

import kotlin.time.Instant
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.coroutines.*
import kotlin.time.ExperimentalTime

@Serializable
class Printer(val message: String) {
    @OptIn(ExperimentalTime::class)
    fun printMessage() = runBlocking {
        val now: Instant = Clock.System.now()
        launch {
            delay(1000L)
            println(now.toString())
        }
        println(message)
    }
}

/**
 * Санитизирует сообщение об ошибке для безопасного отображения пользователю.
 * 
 * Маскирует чувствительные данные:
 * - Bearer токены
 * - OpenAI API ключи (формат sk-...)
 *
 * @param message исходное сообщение об ошибке
 * @return санитизированное сообщение с замаскированными чувствительными данными
 */
fun sanitizeForDisplay(message: String): String {
    return message
        .replace(Regex("Bearer\\s+[A-Za-z0-9\\-._~+/]+=*"), "Bearer ***")
        .replace(Regex("sk-[A-Za-z0-9]{20,}"), "sk-***")
}