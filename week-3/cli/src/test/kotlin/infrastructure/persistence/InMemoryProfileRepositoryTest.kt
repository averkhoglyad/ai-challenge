package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

class InMemoryProfileRepositoryTest {

    private val repository = InMemoryProfileRepository()
    private val now = Instant.now()

    private fun createProfile(id: String = "id-1", name: String = "Test", isActive: Boolean = false): Profile {
        return Profile(
            id = ProfileId(id),
            name = name,
            description = "Description for $name",
            instructions = "Instructions for $name",
            isActive = isActive,
            createdAt = now,
            updatedAt = now
        )
    }

    @Test
    fun `should save and retrieve profile by id`() = runBlocking {
        val profile = createProfile()
        repository.save(profile)
        val found = repository.findById(ProfileId("id-1"))
        assertNotNull(found)
        assertEquals("Test", found.name)
    }

    @Test
    fun `should return null when profile not found by id`() = runBlocking {
        val found = repository.findById(ProfileId("nonexistent"))
        assertNull(found)
    }

    @Test
    fun `should find profile by name`() = runBlocking {
        val profile = createProfile(name = "UniqueName")
        repository.save(profile)
        val found = repository.findByName("UniqueName")
        assertNotNull(found)
        assertEquals("UniqueName", found.name)
    }

    @Test
    fun `should return null when profile not found by name`() = runBlocking {
        val found = repository.findByName("Nonexistent")
        assertNull(found)
    }

    @Test
    fun `should return all profiles`() = runBlocking {
        repository.save(createProfile("id-1", "Profile A"))
        repository.save(createProfile("id-2", "Profile B"))
        val all = repository.findAll()
        assertEquals(2, all.size)
    }

    @Test
    fun `should return empty list when no profiles`() = runBlocking {
        val all = repository.findAll()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `should find active profile`() = runBlocking {
        repository.save(createProfile("id-1", "Inactive"))
        repository.save(createProfile("id-2", "Active", isActive = true))
        val active = repository.findActive()
        assertNotNull(active)
        assertEquals("Active", active.name)
    }

    @Test
    fun `should return null when no active profile`() = runBlocking {
        repository.save(createProfile("id-1", "Inactive"))
        val active = repository.findActive()
        assertNull(active)
    }

    @Test
    fun `should delete profile`() = runBlocking {
        val profile = createProfile()
        repository.save(profile)
        repository.delete(ProfileId("id-1"))
        val found = repository.findById(ProfileId("id-1"))
        assertNull(found)
    }

    @Test
    fun `should check existence by name`() = runBlocking {
        repository.save(createProfile(name = "Existing"))
        assertTrue(repository.existsByName("Existing"))
        assertFalse(repository.existsByName("Nonexistent"))
    }

    @Test
    fun `should clear active profile`() = runBlocking {
        repository.save(createProfile("id-1", "Profile A", isActive = true))
        repository.save(createProfile("id-2", "Profile B"))
        repository.clearActive()
        val active = repository.findActive()
        assertNull(active)
        val profileA = repository.findById(ProfileId("id-1"))
        assertNotNull(profileA)
        assertFalse(profileA.isActive)
    }

    @Test
    fun `should save profile and update existing`() = runBlocking {
        val profile = createProfile()
        repository.save(profile)
        val updated = profile.activate()
        repository.save(updated)
        val found = repository.findById(ProfileId("id-1"))
        assertNotNull(found)
        assertTrue(found.isActive)
    }
}
