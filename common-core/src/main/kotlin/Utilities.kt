package io.averkhogliad.ai.challenge.utils

import java.util.*

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

/**
 * Форматирует время в человекочитаемый вид.
 *
 * Использует [Locale.US] для гарантированного использования точки как десятичного
 * разделителя (независимо от системной локали), что обеспечивает консистентность
 * вывода на любых окружениях.
 *
 * @param ms время в миллисекундах
 * @return строка вида "500 мс" или "1.5 сек"
 */
fun Long.formatTime(): String {
    return if (this < 1000) {
        "${this} мс"
    } else {
        "${String.format(Locale.US, "%.1f", this / 1000.0)} сек"
    }
}
