package io.averkhogliad.ai.challenge.week4.cli.application

/**
 * Типизированные ошибки операций с профилями.
 *
 * ## Архитектурная роль
 * - **Application Layer** — ошибки прикладного уровня
 * - Наследуют [Exception] для совместимости с try-catch
 * - Сообщения на русском языке, готовые для рендеринга
 */
sealed class ProfileOperationError(message: String) : Exception(message) {
    /** Профиль с указанным ID не найден (для activate) */
    class NotFoundById(id: String) : ProfileOperationError("Профиль с ID \"$id\" не найден")

    /** Профиль с указанным именем не найден (для edit, delete, show) */
    class NotFoundByName(name: String) : ProfileOperationError("Профиль с именем \"$name\" не найден")

    /** Профиль с таким названием уже существует (для create, edit) */
    class AlreadyExists(val profileName: String) :
        ProfileOperationError("Профиль с названием \"$profileName\" уже существует")

    /** Пустое содержимое профиля */
    class EmptyContent : ProfileOperationError("Содержимое профиля не может быть пустым")


    /** Содержимое профиля превышает допустимый лимит (1000 символов) */
    class ContentTooLong(val length: Int) : ProfileOperationError(
        "Ошибка: Содержимое профиля не может превышать 1000 символов (текущая длина: $length)"
    )

    /** Попытка удалить активный профиль */
    class CannotDeleteActiveProfile : ProfileOperationError(
        "Нельзя удалить активный профиль. Сначала переключитесь на другой профиль."
    )

}
