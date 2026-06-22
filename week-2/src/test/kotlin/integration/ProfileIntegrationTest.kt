package io.averkhogliad.ai.challenge.week2.integration

import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week2.domain.service.ProfileRepository
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.InMemoryProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

/**
 * Интеграционные тесты для полного потока операций с профилями.
 *
 * Проверяет взаимодействие между слоями:
 * - [ProfileService] (Application)
 * - [InMemoryProfileRepository] (Infrastructure)
 */
class ProfileIntegrationTest {

    private val repository: ProfileRepository = InMemoryProfileRepository()
    private val service = ProfileService(repository)

    @Test
    fun `full flow - create list activate`() = runBlocking {
        // Создаём первый профиль (теперь создаётся неактивным)
        val profileA = service.handleCreateProfile("Profile A", "Content for A", "")
        assertFalse(profileA.isActive)
        assertEquals("Profile A", profileA.name)

        // Создаём второй профиль (должен быть неактивным)
        val profileB = service.handleCreateProfile("Profile B", "Content for B", "")
        assertFalse(profileB.isActive)

        // Проверяем список
        val profiles = service.handleListProfiles()
        assertEquals(2, profiles.size)
        val activeInList = profiles.find { it.isActive }
        assertNull(activeInList, "После создания профилей ни один не должен быть активным")

        // Активируем Profile A по имени
        service.handleActivateByName("Profile A")
        val active = service.handleGetActiveProfile()
        assertNotNull(active)
        assertEquals("Profile A", active.name)

        // Активируем второй профиль
        service.handleActivateProfile(profileB.id)

        // Проверяем, что теперь активен Profile B
        val active2 = service.handleGetActiveProfile()
        assertNotNull(active2)
        assertEquals("Profile B", active2.name)

        // Проверяем, что Profile A теперь неактивен
        val profileAAfter = repository.findById(profileA.id)
        assertNotNull(profileAAfter)
        assertFalse(profileAAfter.isActive)
    }

    @Test
    fun `should reject duplicate profile names`() = runBlocking {
        service.handleCreateProfile("Unique Name", "Content", "")
        assertThrows<IllegalArgumentException> {
            service.handleCreateProfile("Unique Name", "Other Content", "")
        }
    }

    @Test
    fun `should throw when activating nonexistent profile`() = runBlocking {
        assertThrows<IllegalArgumentException> {
            service.handleActivateProfile(ProfileId("nonexistent-id"))
        }
    }

    @Test
    fun `should persist profiles across operations`() = runBlocking {
        val profile = service.handleCreateProfile("Persistent", "Description", "Instructions")
        val found = repository.findById(profile.id)
        assertNotNull(found)
        assertEquals("Persistent", found.name)
        assertEquals("Description", found.description)
        assertEquals("Instructions", found.instructions)
    }

    @Test
    fun `full flow - edit profile name and description`() = runBlocking {
        val profile = service.handleCreateProfile("Original", "Original Description", "Original Instructions")
        val updated = service.handleEditProfile(
            "Original",
            newName = "Renamed",
            newDescription = "Updated Description",
            newInstructions = null
        )
        assertEquals("Renamed", updated.name)
        assertEquals("Updated Description", updated.description)
        // Verify persistence
        val found = repository.findById(profile.id)
        assertNotNull(found)
        assertEquals("Renamed", found.name)
    }

    @Test
    fun `full flow - delete active profile and verify active cleared`() = runBlocking {
        // Создаём два профиля
        val profile = service.handleCreateProfile("Solo", "Content", "")
        assertFalse(profile.isActive)
        val second = service.handleCreateProfile("Backup", "Content", "")
        // Активируем Solo, затем переключаемся на Backup, чтобы Solo стал неактивным
        service.handleActivateProfile(profile.id)
        assertTrue(repository.findById(profile.id)!!.isActive)
        service.handleActivateProfile(second.id)
        // Теперь удаляем первый
        service.handleDeleteProfile("Solo")
        val active = service.handleGetActiveProfile()
        assertNotNull(active)
        assertEquals("Backup", active.name)
        val found = repository.findById(profile.id)
        assertNull(found)
    }

    @Test
    fun `full flow - show active profile after switch`() = runBlocking {
        service.handleCreateProfile("First", "Description A", "Instructions A")
        val second = service.handleCreateProfile("Second", "Description B", "Instructions B")
        service.handleActivateProfile(second.id)
        val shown = service.handleShowProfile(null)
        assertEquals("Second", shown.name)
        assertEquals("Description B", shown.description)
    }
}
