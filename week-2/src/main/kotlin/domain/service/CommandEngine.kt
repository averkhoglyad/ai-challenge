package io.averkhogliad.ai.challenge.week2.domain.service

import io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week2.domain.model.CommandState

/**
 * Интерфейс FSM-движка для управления выполнением команд.
 * 
 * Отвечает за:
 * - Создание состояния при старте команды
 * - Переход между этапами и шагами
 * - Уничтожение состояния после завершения
 * - Хранение активного состояния команды
 * 
 * Архитектурное расположение: domain layer (контракт)
 */
interface CommandEngine {

    /**
     * Проверяет, есть ли активная команда.
     * 
     * @return true если команда выполняется, false иначе
     */
    fun hasActiveCommand(): Boolean

    /**
     * Получает текущее состояние активной команды.
     * 
     * @return CommandState если команда активна, null иначе
     */
    fun getActiveState(): CommandState?

    /**
     * Запускает новую команду с указанным именем.
     * Создаёт начальное состояние на этапе PLANNING.
     * 
     * @param commandName имя команды (например, "plan")
     * @param initialAction описание первого ожидаемого действия
     * @throws IllegalStateException если уже есть активная команда
     */
    fun startCommand(commandName: String, initialAction: String = "")

    /**
     * Переходит к следующему этапу команды.
     * Сбрасывает счётчик шагов на 1.
     * 
     * @param expectedAction описание ожидаемого действия на новом этапе
     * @throws IllegalStateException если нет активной команды
     */
    fun advanceToStage(expectedAction: String = "")

    /**
     * Переходит к следующему этапу с явным указанием этапа.
     * 
     * @param stage целевой этап
     * @param expectedAction описание ожидаемого действия
     * @throws IllegalStateException если нет активной команды
     */
    fun advanceToStage(stage: CommandStage, expectedAction: String = "")

    /**
     * Переходит к следующему шагу внутри текущего этапа.
     * 
     * @param expectedAction описание ожидаемого действия на новом шаге
     * @throws IllegalStateException если нет активной команды
     */
    fun advanceStep(expectedAction: String = "")

    /**
     * Сохраняет значение в контекст выполнения.
     * 
     * @param key ключ контекста
     * @param value значение
     * @throws IllegalStateException если нет активной команды
     */
    fun putContext(key: String, value: String)

    /**
     * Получает значение из контекста выполнения.
     * 
     * @param key ключ контекста
     * @return значение или null если ключ не найден
     * @throws IllegalStateException если нет активной команды
     */
    fun getContext(key: String): String?

    /**
     * Завершает текущую команду и уничтожает состояние.
     * 
     * @throws IllegalStateException если нет активной команды
     */
    fun completeCommand()

    /**
     * Отменяет текущую команду и уничтожает состояние.
     * Используется для принудительной остановки (например, по команде :abort).
     * 
     * @throws IllegalStateException если нет активной команды
     */
    fun abortCommand()
}
