package io.averkhogliad.ai.challenge.week4.cli.cli.handlers

import io.averkhogliad.ai.challenge.week4.cli.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week4.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week4.cli.cli.CliRenderer
import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.commands.Command

/**
 * Handler для обработки команд управления профилями.
 */
class ProfileCommandHandler(
    private val profileService: ProfileService,
    private val renderer: CliRenderer,
    private val readLine: () -> String? = { readlnOrNull() },
    private val readMultiline: () -> String
) {
    suspend fun handleProfileList(state: CliState): CliState {
        try {
            renderer.renderProfileList(profileService.handleListProfiles())
        } catch (e: Exception) {
            renderer.renderError(e.message ?: "Unknown error")
        }
        return state
    }

    suspend fun handleProfileNew(command: Command.ProfileNew, state: CliState): CliState {
        try {
            renderer.renderProfileDescriptionPrompt()
            val description = readMultiline()

            renderer.renderProfileInstructionsPrompt()
            val instructions = readMultiline()

            if (description.isBlank() && instructions.isBlank()) {
                renderer.renderEmptyProfileContent()
            } else {
                val profile = profileService.handleCreateProfile(command.name, description, instructions)
                renderer.renderSuccess("Профиль \"${profile.name}\" создан")
            }
        } catch (_: ProfileOperationError.AlreadyExists) {
            renderer.renderProfileAlreadyExists(command.name)
        } catch (_: ProfileOperationError.EmptyContent) {
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

    suspend fun handleProfileUse(command: Command.ProfileUse, state: CliState): CliState {
        try {
            if (command.name == "none") {
                profileService.handleDeactivateProfile()
                renderer.renderInfo("Профиль деактивирован")
            } else {
                val profile = profileService.handleActivateByName(command.name)
                renderer.renderProfileDetail(profile)
            }
        } catch (_: ProfileOperationError.NotFoundByName) {
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
                val profile = profileService.handleEditProfile(
                    command.name,
                    newName.ifEmpty { null },
                    newDescription.ifBlank { null },
                    newInstructions.ifBlank { null }
                )
                renderer.renderProfileUpdated(profile.name)
            }
        } catch (_: ProfileOperationError.NotFoundByName) {
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

    suspend fun handleProfileDelete(command: Command.ProfileDelete, state: CliState): CliState {
        try {
            profileService.handleDeleteProfile(command.name)
            renderer.renderProfileDeleted(command.name)
        } catch (_: ProfileOperationError.NotFoundByName) {
            renderer.renderProfileNotFoundByName(command.name)
        } catch (_: ProfileOperationError.CannotDeleteActiveProfile) {
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

    suspend fun handleProfileShow(command: Command.ProfileShow, state: CliState): CliState {
        try {
            val profile = profileService.handleShowProfile(command.name)
            renderer.renderProfileDetail(profile)
        } catch (_: ProfileOperationError.NotFoundByName) {
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
