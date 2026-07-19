package io.averkhogliad.ai.challenge.week1.infrastructure.llm

import io.averkhogliad.ai.challenge.llm.chat.LlmClient
import io.averkhogliad.ai.challenge.week1.domain.service.ResourceManager

/**
 * Адаптер ResourceManager для LlmClient.
 * Делегирует close() инфраструктурному клиенту.
 */
class LlmClientResourceManager(
    private val llmClient: LlmClient
) : ResourceManager {
    override fun close() {
        llmClient.close()
    }
}
