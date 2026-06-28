package io.averkhogliad.ai.challenge.week3.cli.application

import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ProfileRepository
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.*

class ProfileServiceTest {

    private val repository: ProfileRepository = InMemoryProfileRepository()
    private val service = ProfileService(repository)

    @Test
    fun `should create profile as inactive`() = runBlocking {
        val profile = service.handleCreateProfile("First", "Description", "Instructions")
        assertEquals("First", profile.name)
        assertEquals("Description", profile.description)
        assertEquals("Instructions", profile.instructions)
        assertFalse(profile.isActive, "Созданный профиль не должен быть активным автоматически")
    }

    @Test
    fun `should create second profile as inactive too`() = runBlocking {
        service.handleCreateProfile("First", "Desc", "")
        val second = service.handleCreateProfile("Second", "Desc", "")
        assertEquals("Second", second.name)
        assertFalse(second.isActive)
    }

    @Test
    fun `should reject duplicate profile name`() = runBlocking {
        service.handleCreateProfile("Unique", "Desc", "")
        assertThrows<ProfileOperationError.AlreadyExists> {
            service.handleCreateProfile("Unique", "Other", "")
        }
    }

    @Test
    fun `should list all profiles`() = runBlocking {
        service.handleCreateProfile("A", "Desc A", "")
        service.handleCreateProfile("B", "Desc B", "")
        val profiles = service.handleListProfiles()
        assertEquals(2, profiles.size)
    }

    @Test
    fun `should list empty profiles`() = runBlocking {
        val profiles = service.handleListProfiles()
        assertTrue(profiles.isEmpty())
    }

    @Test
    fun `should activate profile by id`() = runBlocking {
        val first = service.handleCreateProfile("First", "Desc", "")
        val second = service.handleCreateProfile("Second", "Desc", "")
        assertFalse(first.isActive)
        assertFalse(second.isActive)

        val activated = service.handleActivateProfile(second.id)
        assertTrue(activated.isActive)
        val firstAfter = repository.findById(first.id)
        assertNotNull(firstAfter)
        assertFalse(firstAfter.isActive)
    }

    @Test
    fun `should activate profile by name`() = runBlocking {
        service.handleCreateProfile("First", "Desc", "")
        service.handleCreateProfile("Second", "Desc", "")
        assertNull(service.handleGetActiveProfile())

        val activated = service.handleActivateByName("Second")
        assertTrue(activated.isActive)
        assertEquals("Second", activated.name)

        val active = service.handleGetActiveProfile()
        assertNotNull(active)
        assertEquals("Second", active.name)
    }

    @Test
    fun `should throw when activating by nonexistent name`() = runBlocking {
        assertThrows<ProfileOperationError.NotFoundByName> {
            service.handleActivateByName("Nonexistent")
        }
    }

    @Test
    fun `should deactivate profile`() = runBlocking {
        service.handleCreateProfile("First", "Desc", "")
        service.handleActivateByName("First")
        assertNotNull(service.handleGetActiveProfile())

        service.handleDeactivateProfile()
        assertNull(service.handleGetActiveProfile())
    }

    @Test
    fun `creating profile should not activate it`() = runBlocking {
        val profile = service.handleCreateProfile("New", "Desc", "")
        assertFalse(profile.isActive, "Новый профиль не должен быть активным")
        assertNull(service.handleGetActiveProfile(), "После создания профиля активного быть не должно")
    }

    @Test
    fun `should throw when activating nonexistent profile`() = runBlocking {
        assertThrows<ProfileOperationError.NotFoundById> {
            service.handleActivateProfile(ProfileId("nonexistent"))
        }
    }

    @Test
    fun `should get active profile`() = runBlocking {
        service.handleCreateProfile("First", "Desc", "")
        service.handleActivateByName("First")
        val active = service.handleGetActiveProfile()
        assertNotNull(active)
        assertEquals("First", active.name)
    }

    @Test
    fun `should return null when no active profile`() = runBlocking {
        val active = service.handleGetActiveProfile()
        assertNull(active)
    }

    @Test
    fun `should edit profile name`() = runBlocking {
        val profile = service.handleCreateProfile("Original", "Desc", "")
        val updated =
            service.handleEditProfile("Original", newName = "Updated", newDescription = null, newInstructions = null)
        assertEquals("Updated", updated.name)
        assertEquals("Desc", updated.description)
    }

    @Test
    fun `should edit profile description`() = runBlocking {
        val profile = service.handleCreateProfile("Original", "Desc", "")
        val updated =
            service.handleEditProfile("Original", newName = null, newDescription = "New Desc", newInstructions = null)
        assertEquals("Original", updated.name)
        assertEquals("New Desc", updated.description)
    }

    @Test
    fun `should edit profile instructions`() = runBlocking {
        val profile = service.handleCreateProfile("Original", "", "Instr")
        val updated =
            service.handleEditProfile("Original", newName = null, newDescription = null, newInstructions = "New Instr")
        assertEquals("Original", updated.name)
        assertEquals("New Instr", updated.instructions)
    }

    @Test
    fun `should edit profile name and description`() = runBlocking {
        val profile = service.handleCreateProfile("Original", "Desc", "")
        val updated = service.handleEditProfile(
            "Original",
            newName = "Renamed",
            newDescription = "Changed",
            newInstructions = null
        )
        assertEquals("Renamed", updated.name)
        assertEquals("Changed", updated.description)
    }

    @Test
    fun `should throw when editing nonexistent profile`() = runBlocking {
        assertThrows<ProfileOperationError.NotFoundByName> {
            service.handleEditProfile(
                "Nonexistent",
                newName = "Whatever",
                newDescription = null,
                newInstructions = null
            )
        }
    }

    @Test
    fun `should reject duplicate name when editing`() = runBlocking {
        service.handleCreateProfile("First", "Desc", "")
        service.handleCreateProfile("Second", "Desc", "")
        assertThrows<ProfileOperationError.AlreadyExists> {
            service.handleEditProfile("Second", newName = "First", newDescription = null, newInstructions = null)
        }
    }

    @Test
    fun `should delete profile`() = runBlocking {
        // Создаём два профиля: первый будет активным, второй — нет
        val profileA = service.handleCreateProfile("ToDelete", "Desc", "")
        service.handleCreateProfile("NotActive", "Desc", "")
        // Переключаемся на второй профиль, чтобы снять активность с первого
        val profileB = service.handleActivateProfile(profileA.id) // activate first — stays active
        val second = service.handleCreateProfile("Another", "Desc", "")
        service.handleActivateProfile(second.id) // make second active
        // Теперь ToDelete неактивен — можно удалить
        service.handleDeleteProfile("ToDelete")
        val found = repository.findById(profileA.id)
        assertNull(found)
    }

    @Test
    fun `should throw CannotDeleteActiveProfile when deleting active profile`() = runBlocking {
        val profile = service.handleCreateProfile("Active", "Desc", "")
        service.handleActivateProfile(profile.id)
        assertThrows<io.averkhogliad.ai.challenge.week3.cli.application.ProfileOperationError.CannotDeleteActiveProfile> {
            service.handleDeleteProfile("Active")
        }
    }

    @Test
    fun `should throw when deleting nonexistent profile`() = runBlocking {
        assertThrows<ProfileOperationError.NotFoundByName> {
            service.handleDeleteProfile("Nonexistent")
        }
    }

    @Test
    fun `should show profile by name`() = runBlocking {
        val created = service.handleCreateProfile("Showable", "Desc", "Instr")
        val shown = service.handleShowProfile("Showable")
        assertEquals(created.id, shown.id)
        assertEquals("Showable", shown.name)
        assertEquals("Desc", shown.description)
        assertEquals("Instr", shown.instructions)
    }

    @Test
    fun `should show active profile when name is null`() = runBlocking {
        val created = service.handleCreateProfile("Active", "Desc", "")
        service.handleActivateProfile(created.id)
        val shown = service.handleShowProfile(null)
        assertEquals(created.id, shown.id)
        assertEquals("Active", shown.name)
    }

    @Test
    fun `should throw when showing nonexistent profile by name`() = runBlocking {
        assertThrows<ProfileOperationError.NotFoundByName> {
            service.handleShowProfile("Nonexistent")
        }
    }

    @Test
    fun `should throw when showing active profile but none active`() = runBlocking {
        assertThrows<ProfileOperationError.NotFoundByName> {
            service.handleShowProfile(null)
        }
    }

    @Test
    fun `should reject content exceeding max length on create`() = runBlocking {
        val tooLong = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)
        assertThrows<ProfileOperationError.ContentTooLong> {
            service.handleCreateProfile("Test", tooLong, "")
        }
    }

    @Test
    fun `should reject content exceeding max length on edit`() = runBlocking {
        service.handleCreateProfile("Original", "Short", "")
        val tooLong = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)
        assertThrows<ProfileOperationError.ContentTooLong> {
            service.handleEditProfile("Original", newName = null, newDescription = tooLong, newInstructions = null)
        }
    }

    @Test
    fun `should accept content exactly at max length on create`() = runBlocking {
        val exact = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)
        val profile = service.handleCreateProfile("Exact", exact, "")
        assertEquals(ProfileService.MAX_PROFILE_CONTENT_LENGTH, profile.description.length)
    }

    @Test
    fun `should accept content exactly at max length on edit`() = runBlocking {
        service.handleCreateProfile("Original", "Short", "")
        val exact = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)
        val updated =
            service.handleEditProfile("Original", newName = null, newDescription = exact, newInstructions = null)
        assertEquals(ProfileService.MAX_PROFILE_CONTENT_LENGTH, updated.description.length)
    }

    @Test
    fun `should create profile with description and instructions`() = runBlocking {
        val profile = service.handleCreateProfile("Test", "My description", "My instructions")
        assertEquals("Test", profile.name)
        assertEquals("My description", profile.description)
        assertEquals("My instructions", profile.instructions)
    }

    @Test
    fun `should reject empty description and instructions`() = runBlocking {
        assertThrows<ProfileOperationError.EmptyContent> {
            service.handleCreateProfile("Test", "", "")
        }
    }

    @Test
    fun `should create profile with only description`() = runBlocking {
        val profile = service.handleCreateProfile("Test", "Only desc", "")
        assertEquals("Only desc", profile.description)
        assertEquals("", profile.instructions)
    }

    @Test
    fun `should create profile with only instructions`() = runBlocking {
        val profile = service.handleCreateProfile("Test", "", "Only instr")
        assertEquals("", profile.description)
        assertEquals("Only instr", profile.instructions)
    }
}
