package io.averkhogliad.ai.challenge.week3.cli.domain.service

import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandStage
import io.averkhogliad.ai.challenge.week3.cli.domain.model.CommandState
import io.averkhogliad.ai.challenge.week3.cli.domain.model.StateMap
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Transition
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TransitionValidationResult

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

    /**
     * Выполняет переход между состояниями с валидацией через TransitionValidator.
     *
     * @param to целевое состояние
     * @param description описание перехода (для истории)
     * @throws TransitionNotAllowedException если переход недопустим
     * @throws IllegalStateException если нет активной команды
     */
    fun performTransition(to: CommandStage, description: String = "")

    /**
     * Проверяет, доступен ли переход.
     *
     * @param to целевое состояние
     * @return результат валидации
     * @throws IllegalStateException если нет активной команды
     */
    fun isTransitionAllowed(to: CommandStage): TransitionValidationResult

    /**
     * Возвращает список доступных переходов из текущего состояния.
     *
     * @return список допустимых переходов
     * @throws IllegalStateException если нет активной команды
     */
    fun getAvailableTransitions(): List<Transition>

    /**
     * Строит карту состояний для текущего состояния FSM.
     *
     * @return StateMap с информацией о всех состояниях и доступных переходах
     * @throws IllegalStateException если нет активной команды
     */
    fun buildStateMap(): StateMap

    /**
     * Устанавливает флаг паузы FSM.
     */
    fun pause()

    /**
     * Снимает флаг паузы FSM и проверяет условия перехода.
     *
     * @throws TransitionNotAllowedException если условия перехода изменились
     */
    fun resume()
}
