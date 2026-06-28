package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ProfileRepository

/**
 * In-memory реализация [ProfileRepository].
 *
 * ## Архитектурная роль
 * - **Infrastructure Layer** — реализация порта для хранения в памяти
 * - Используется для начала разработки, позже заменяется на SQLite
 */
class InMemoryProfileRepository : ProfileRepository {
    private val profiles = mutableMapOf<ProfileId, Profile>()

    override suspend fun save(profile: Profile): Profile {
        profiles[profile.id] = profile
        return profile
    }

    override suspend fun findById(id: ProfileId): Profile? = profiles[id]

    override suspend fun findByName(name: String): Profile? =
        profiles.values.find { it.name == name }

    override suspend fun findAll(): List<Profile> =
        profiles.values.toList()

    override suspend fun findActive(): Profile? =
        profiles.values.find { it.isActive }

    override suspend fun delete(id: ProfileId) {
        profiles.remove(id)
    }

    override suspend fun existsByName(name: String): Boolean =
        profiles.values.any { it.name == name }

    override suspend fun clearActive() {
        val active = findActive() ?: return
        profiles[active.id] = active.deactivate()
    }
}
