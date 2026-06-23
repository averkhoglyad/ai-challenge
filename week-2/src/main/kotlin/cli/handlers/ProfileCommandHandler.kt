package io.averkhogliad.ai.challenge.week2.cli.handlers

import io.averkhogliad.ai.challenge.week2.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week2.cli.CliRenderer
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.cli.CommandHandler
import io.averkhogliad.ai.challenge.week2.cli.commands.Command

/**
 * Handler для обработки команд управления профилями.
 *
 * Отвечает за:
 * - Список профилей (`:profile list`)
 * - Создание профиля (`:profile new <name>`)
 * - Активация/деактивация профиля (`:profile use <name>`)
 * - Редактирование профиля (`:profile edit <name>`)
 * - Удаление профиля (`:profile delete <name>`)
 * - Просмотр профиля (`:profile show [name]`)
 *
 * @param handler CommandHandler для доступа к Task2Executor
 * @param renderer рендерер CLI вывода
 * @param readLine функция для чтения одной строки ввода
 * @param readMultiline функция для чтения многострочного ввода
 */
class ProfileCommandHandler(
    private val handler: CommandHandler,
    private val renderer: CliRenderer,
    private val readLine: () -> String? = { readlnOrNull() },
    private val readMultiline: () -> String
) {

    /**
     * Обрабатывает команду `:profile list` — показывает список профилей.
     */
    suspend fun handleProfileList(state: CliState): CliState {
        try {
            val profiles = handler.getTask2Executor()?.handleListProfiles() ?: emptyList()
            renderer.renderProfileList(profiles)
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    /**
     * Обрабатывает команду `:profile new <name>` — создаёт новый профиль.
     */
    suspend fun handleProfileNew(command: Command.ProfileNew, state: CliState): CliState {
        try {
            renderer.renderProfileDescriptionPrompt()
            val description = readMultiline()

            renderer.renderProfileInstructionsPrompt()
            val instructions = readMultiline()

            if (description.isBlank() && instructions.isBlank()) {
                renderer.renderEmptyProfileContent()
            } else {
                val profile = handler.getTask2Executor()?.handleCreateProfile(
                    command.name, description, instructions
                )
                if (profile != null) {
                    renderer.renderSuccess("Профиль \"${profile.name}\" создан")
                }
            }
        } catch (e: ProfileOperationError.AlreadyExists) {
            renderer.renderProfileAlreadyExists(command.name)
        } catch (e: ProfileOperationError.EmptyContent) {
            renderer.renderEmptyProfileContent()
        } catch (e: ProfileOperationError.ContentTooLong) {
            renderer.renderProfileContentTooLong(e.length)
        } catch (e: ProfileOperationError) {
            renderer.renderProfileError(e.message ?: "Ошибка создания профиля")
        } catch (e: IllegalArgumentException) {
            renderer.renderError(e.message ?: "Ошибка создания профиля")
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    /**
     * Обрабатывает команду `:profile use <name>` — активирует или деактивирует профиль.
     */
    suspend fun handleProfileUse(command: Command.ProfileUse, state: CliState): CliState {
        try {
            if (command.name == "none") {
                handler.getTask2Executor()?.handleDeactivateProfile()
                renderer.renderInfo("Профиль деактивирован")
            } else {
                val profile = handler.getTask2Executor()?.handleActivateByName(command.name)
                if (profile != null) {
                    renderer.renderProfileDetail(profile)
                }
            }
        } catch (e: ProfileOperationError.NotFoundByName) {
            renderer.renderProfileNotFoundByName(command.name)
        } catch (e: ProfileOperationError) {
            renderer.renderProfileError(e.message ?: "Ошибка активации профиля")
        } catch (e: IllegalArgumentException) {
            renderer.renderProfileError(e.message ?: "Ошибка активации профиля")
        } catch (e: Exception) {
            renderer.renderProfileError(e.message ?: "Unknown error")
        }
        return state
    }

    /**
     * Обрабатывает команду `:profile edit <name>` — редактирует профиль.
     */
    suspend fun handleProfileEdit(command: Command.ProfileEdit, state: CliState): CliState {
        try {
            renderer.renderInfo("Введите новое название профиля (Enter — оставить прежним):")
            val newName = readLine()?.trim() ?: ""
            renderer.renderProfileDescriptionPrompt()
            val newDescription = readMultiline()
            renderer.renderProfileInstructionsPrompt()
            val newInstructions = readMultiline()

            if (newName.isEmpty() && newDescription.isBlank() && newInstructions.isBlank()) {
                renderer.renderError("Не указаны изменения для профиля")
            } else {
                val profile = handler.getTask2Executor()?.handleEditProfile(
                    command.name,
                    newName.ifEmpty { null },
                    newDescription.ifBlank { null },
                    newInstructions.ifBlank { null }
                )
                if (profile != null) {
                    renderer.renderProfileUpdated(profile.name)
                }
            }
        } catch (e: ProfileOperationError.NotFoundByName) {
            renderer.renderProfileNotFoundByName(command.name)
        } catch (e: ProfileOperationError.AlreadyExists) {
            renderer.renderProfileAlreadyExists(e.profileName)
        } catch (e: ProfileOperationError.ContentTooLong) {
            renderer.renderProfileContentTooLong(e.length)
        } catch (e: ProfileOperationError) {
            renderer.renderProfileError(e.message ?: "Ошибка редактирования профиля")
        } catch (e: IllegalArgumentException) {
            renderer.renderError(e.message ?: "Ошибка редактирования профиля")
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    /**
     * Обрабатывает команду `:profile delete <name>` — удаляет профиль.
     */
    suspend fun handleProfileDelete(command: Command.ProfileDelete, state: CliState): CliState {
        try {
            handler.getTask2Executor()?.handleDeleteProfile(command.name)
            renderer.renderProfileDeleted(command.name)
        } catch (e: ProfileOperationError.NotFoundByName) {
            renderer.renderProfileNotFoundByName(command.name)
        } catch (e: ProfileOperationError.CannotDeleteActiveProfile) {
            renderer.renderCannotDeleteActiveProfile()
        } catch (e: ProfileOperationError) {
            renderer.renderProfileError(e.message ?: "Ошибка удаления профиля")
        } catch (e: IllegalArgumentException) {
            renderer.renderError(e.message ?: "Ошибка удаления профиля")
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    /**
     * Обрабатывает команду `:profile show [name]` — показывает детали профиля.
     */
    suspend fun handleProfileShow(command: Command.ProfileShow, state: CliState): CliState {
        try {
            val profile = handler.getTask2Executor()?.handleShowProfile(command.name)
            if (profile != null) {
                renderer.renderProfileDetail(profile)
            }
        } catch (e: ProfileOperationError.NotFoundByName) {
            renderer.renderProfileNotFoundByName(command.name ?: "неизвестно")
        } catch (e: ProfileOperationError) {
            renderer.renderProfileError(e.message ?: "Ошибка просмотра профиля")
        } catch (e: IllegalArgumentException) {
            renderer.renderError(e.message ?: "Ошибка просмотра профиля")
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }
}
