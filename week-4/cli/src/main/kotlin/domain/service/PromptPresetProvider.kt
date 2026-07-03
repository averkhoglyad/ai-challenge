package io.averkhogliad.ai.challenge.week4.cli.domain.service

import io.averkhogliad.ai.challenge.week4.cli.domain.model.PromptPreset

/**
 * Порт для загрузки preset'ов.
 *
 * ## Архитектурная роль
 * - **Domain Port** — контракт, реализуемый infrastructure-слоем
 * - **Hexagonal Architecture** — domain определяет, infrastructure реализует
 */
interface PromptPresetProvider {
    /**
     * Загружает все preset'ы из источника.
     *
     * @return список preset'ов (пустой, если источник недоступен)
     */
    suspend fun load(): List<PromptPreset>
}
