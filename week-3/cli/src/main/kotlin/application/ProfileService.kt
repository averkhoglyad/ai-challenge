package io.averkhogliad.ai.challenge.week3.cli.application

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ProfileRepository
import java.time.Instant
import java.util.*

/**
 * Сервис для управления профилями пользователя.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация операций с профилями
 * - **Не зависит** от UI (CLI, Mordant)
 * - **Зависит только** от domain port [ProfileRepository]
 *
 * ## Обработка ошибок
 * Все методы выбрасывают типизированные [ProfileOperationError] вместо
 * обобщённого [IllegalArgumentException] для точной обработки в CLI-слое.
 */
class ProfileService(
    private val profileRepository: ProfileRepository
) {
    companion object {
        /** Максимальная допустимая длина содержимого профиля */
        const val MAX_PROFILE_CONTENT_LENGTH = 1000
    }

    suspend fun handleCreateProfile(name: String, description: String, instructions: String): Profile {
        // Проверка, что хотя бы одно из полей заполнено
        if (description.isBlank() && instructions.isBlank()) throw ProfileOperationError.EmptyContent()
        val totalLength = description.length + instructions.length

        if (totalLength > MAX_PROFILE_CONTENT_LENGTH) throw ProfileOperationError.ContentTooLong(totalLength)
        if (profileRepository.existsByName(name)) {
            throw ProfileOperationError.AlreadyExists(name)
        }
        val profileId = ProfileId(UUID.randomUUID().toString())
        val now = Instant.now()
        // Профиль создаётся неактивным (isActive = false), автоматическая активация не происходит
        val profile = Profile(
            id = profileId,
            name = name,
            description = description,
            instructions = instructions,
            isActive = false,
            createdAt = now,
            updatedAt = now
        )
        return profileRepository.save(profile)
    }

    suspend fun handleListProfiles(): List<Profile> = profileRepository.findAll()

    suspend fun handleActivateProfile(id: ProfileId): Profile {
        val profile = profileRepository.findById(id)
            ?: throw ProfileOperationError.NotFoundById(id.value)
        profileRepository.clearActive()
        val activatedProfile = profile.activate()
        return profileRepository.save(activatedProfile)
    }

    suspend fun handleActivateByName(name: String): Profile {
        val profile = profileRepository.findByName(name)
            ?: throw ProfileOperationError.NotFoundByName(name)
        profileRepository.clearActive()
        val activatedProfile = profile.activate()
        return profileRepository.save(activatedProfile)
    }

    suspend fun handleDeactivateProfile() {
        profileRepository.clearActive()
    }

    suspend fun handleGetActiveProfile(): Profile? = profileRepository.findActive()

    suspend fun handleEditProfile(
        name: String,
        newName: String?,
        newDescription: String?,
        newInstructions: String?
    ): Profile {
        val profile = profileRepository.findByName(name)
            ?: throw ProfileOperationError.NotFoundByName(name)
        var updated = profile
        if (newName != null && newName.isNotBlank() && newName != name) {
            if (profileRepository.existsByName(newName)) {
                throw ProfileOperationError.AlreadyExists(newName)
            }
            updated = updated.updateName(newName)
        }
        if (newDescription != null) {
            val totalLength = newDescription.length + updated.instructions.length
            if (totalLength > MAX_PROFILE_CONTENT_LENGTH) throw ProfileOperationError.ContentTooLong(totalLength)
            updated = updated.updateDescription(newDescription)
        }
        if (newInstructions != null) {
            val totalLength = updated.description.length + newInstructions.length
            if (totalLength > MAX_PROFILE_CONTENT_LENGTH) throw ProfileOperationError.ContentTooLong(totalLength)
            updated = updated.updateInstructions(newInstructions)
        }
        return profileRepository.save(updated)
    }

    suspend fun handleDeleteProfile(name: String) {
        val profile = profileRepository.findByName(name)
            ?: throw ProfileOperationError.NotFoundByName(name)
        if (profile.isActive) {
            throw ProfileOperationError.CannotDeleteActiveProfile()
        }

        profileRepository.delete(profile.id)
    }

    suspend fun handleShowProfile(name: String?): Profile {
        return if (name != null) {
            profileRepository.findByName(name)
                ?: throw ProfileOperationError.NotFoundByName(name)
        } else {
            profileRepository.findActive()
                ?: throw ProfileOperationError.NotFoundByName("нет активного профиля")
        }
    }
}
