package io.averkhogliad.ai.challenge.utils

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

