package io.averkhogliad.ai.challenge.week3.cli.domain.model

/**
 * Модель debug-режима для FSM-команд.
 *
 * Debug-режим позволяет видеть внутреннюю работу агента:
 * - текущий этап и шаг выполнения команды
 * - промежуточные результаты
 * - пауза после каждого шага для изучения вывода
 *
 * ## Свойства
 * - Глобальная настройка (не привязана к задаче или профилю)
 * - По умолчанию выключена (false)
 * - Не сохраняется между сессиями (только в памяти)
 *
 * ## Использование
 * ```kotlin
 * val debugMode = DebugMode()
 * if (debugMode.isEnabled) {
 *     println("[DEBUG] Stage: $stage, Step: $step")
 * }
 * ```
 */
class DebugMode {
    /**
     * Флаг включения debug-режима.
     * По умолчанию false (режим отладки выключен).
     */
    var isEnabled: Boolean = false
        private set

    /**
     * Включить debug-режим.
     */
    fun enable() {
        isEnabled = true
    }

    /**
     * Выключить debug-режим.
     */
    fun disable() {
        isEnabled = false
    }

    /**
     * Переключить debug-режим (toggle).
     * Если включен — выключит, если выключен — включит.
     */
    fun toggle() {
        isEnabled = !isEnabled
    }

    /**
     * Установить debug-режим в указанное состояние.
     * @param enabled true для включения, false для выключения
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }

    override fun toString(): String {
        return "DebugMode(isEnabled=$isEnabled)"
    }
}
