package io.averkhogliad.ai.challenge.utils.config

/**
 * Абстракция над источником конфигурации.
 *
 * Предоставляет доступ к настройкам по строковым ключам без привязки
 * к конкретной структуре (DTO, JSON, Properties и т.д.).
 *
 * Реализации:
 * - [PropertiesConfig] — обёртка над [java.util.Properties];
 * - [MergedConfig] — объединение нескольких [Config] с приоритетом.
 */
interface Config {

    /**
     * Возвращает значение по [key] или `null`, если ключ отсутствует.
     */
    fun getOrNull(key: String): String?

    /**
     * Возвращает значение по [key] или выбрасывает [NoSuchElementException],
     * если ключ отсутствует.
     */
    fun get(key: String): String =
        getOrNull(key) ?: throw NoSuchElementException("Config key not found: '$key'")

    /**
     * Возвращает значение по [key] или [default], если ключ отсутствует.
     */
    fun getOrDefault(key: String, default: String): String =
        getOrNull(key) ?: default

    /**
     * Возвращает множество всех ключей, присутствующих в конфигурации.
     */
    fun keys(): Set<String>

    /**
     * Проверяет, присутствует ли ключ [key] в конфигурации.
     */
    fun contains(key: String): Boolean = getOrNull(key) != null
}
