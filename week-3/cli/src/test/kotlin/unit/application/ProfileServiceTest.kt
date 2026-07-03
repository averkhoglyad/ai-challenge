package io.averkhogliad.ai.challenge.week3.cli.unit.application

import io.averkhogliad.ai.challenge.week3.cli.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week3.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ProfileRepository
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ProfileServiceTest : FreeSpec({
    lateinit var repository: ProfileRepository
    lateinit var service: ProfileService

    beforeEach {
        repository = InMemoryProfileRepository()
        service = ProfileService(repository)
    }

    "should create profile as inactive" {
        val profile = service.handleCreateProfile("First", "Description", "Instructions")
        profile.name shouldBe "First"
        profile.description shouldBe "Description"
        profile.instructions shouldBe "Instructions"
        profile.isActive shouldBe false
    }

    "should create second profile as inactive too" {
        service.handleCreateProfile("First", "Desc", "")
        val second = service.handleCreateProfile("Second", "Desc", "")
        second.name shouldBe "Second"
        second.isActive shouldBe false
    }

    "should reject duplicate profile name" {
        service.handleCreateProfile("Unique", "Desc", "")
        shouldThrow<ProfileOperationError.AlreadyExists> {
            service.handleCreateProfile("Unique", "Other", "")
        }
    }

    "should list all profiles" {
        service.handleCreateProfile("A", "Desc A", "")
        service.handleCreateProfile("B", "Desc B", "")
        val profiles = service.handleListProfiles()
        profiles.size shouldBe 2
    }

    "should list empty profiles" {
        val profiles = service.handleListProfiles()
        profiles.isEmpty() shouldBe true
    }

    "should activate profile by id" {
        val first = service.handleCreateProfile("First", "Desc", "")
        val second = service.handleCreateProfile("Second", "Desc", "")
        first.isActive shouldBe false
        second.isActive shouldBe false

        val activated = service.handleActivateProfile(second.id)
        activated.isActive shouldBe true
        val firstAfter = repository.findById(first.id)
        firstAfter.shouldNotBeNull()
        firstAfter.isActive shouldBe false
    }

    "should activate profile by name" {
        service.handleCreateProfile("First", "Desc", "")
        service.handleCreateProfile("Second", "Desc", "")
        service.handleGetActiveProfile().shouldBeNull()

        val activated = service.handleActivateByName("Second")
        activated.isActive shouldBe true
        activated.name shouldBe "Second"

        val active = service.handleGetActiveProfile()
        active.shouldNotBeNull()
        active.name shouldBe "Second"
    }

    "should throw when activating by nonexistent name" {
        shouldThrow<ProfileOperationError.NotFoundByName> {
            service.handleActivateByName("Nonexistent")
        }
    }

    "should deactivate profile" {
        service.handleCreateProfile("First", "Desc", "")
        service.handleActivateByName("First")
        service.handleGetActiveProfile().shouldNotBeNull()

        service.handleDeactivateProfile()
        service.handleGetActiveProfile().shouldBeNull()
    }

    "creating profile should not activate it" {
        val profile = service.handleCreateProfile("New", "Desc", "")
        profile.isActive shouldBe false
        service.handleGetActiveProfile().shouldBeNull()
    }

    "should throw when activating nonexistent profile" {
        shouldThrow<ProfileOperationError.NotFoundById> {
            service.handleActivateProfile(ProfileId("nonexistent"))
        }
    }

    "should get active profile" {
        service.handleCreateProfile("First", "Desc", "")
        service.handleActivateByName("First")
        val active = service.handleGetActiveProfile()
        active.shouldNotBeNull()
        active.name shouldBe "First"
    }

    "should return null when no active profile" {
        val active = service.handleGetActiveProfile()
        active.shouldBeNull()
    }

    "should edit profile name" {
        val profile = service.handleCreateProfile("Original", "Desc", "")
        val updated =
            service.handleEditProfile("Original", newName = "Updated", newDescription = null, newInstructions = null)
        updated.name shouldBe "Updated"
        updated.description shouldBe "Desc"
    }

    "should edit profile description" {
        val profile = service.handleCreateProfile("Original", "Desc", "")
        val updated =
            service.handleEditProfile("Original", newName = null, newDescription = "New Desc", newInstructions = null)
        updated.name shouldBe "Original"
        updated.description shouldBe "New Desc"
    }

    "should edit profile instructions" {
        val profile = service.handleCreateProfile("Original", "", "Instr")
        val updated =
            service.handleEditProfile("Original", newName = null, newDescription = null, newInstructions = "New Instr")
        updated.name shouldBe "Original"
        updated.instructions shouldBe "New Instr"
    }

    "should edit profile name and description" {
        val profile = service.handleCreateProfile("Original", "Desc", "")
        val updated = service.handleEditProfile(
            "Original",
            newName = "Renamed",
            newDescription = "Changed",
            newInstructions = null
        )
        updated.name shouldBe "Renamed"
        updated.description shouldBe "Changed"
    }

    "should throw when editing nonexistent profile" {
        shouldThrow<ProfileOperationError.NotFoundByName> {
            service.handleEditProfile(
                "Nonexistent",
                newName = "Whatever",
                newDescription = null,
                newInstructions = null
            )
        }
    }

    "should reject duplicate name when editing" {
        service.handleCreateProfile("First", "Desc", "")
        service.handleCreateProfile("Second", "Desc", "")
        shouldThrow<ProfileOperationError.AlreadyExists> {
            service.handleEditProfile("Second", newName = "First", newDescription = null, newInstructions = null)
        }
    }

    "should delete profile" {
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
        found.shouldBeNull()
    }

    "should throw CannotDeleteActiveProfile when deleting active profile" {
        val profile = service.handleCreateProfile("Active", "Desc", "")
        service.handleActivateProfile(profile.id)
        shouldThrow<io.averkhogliad.ai.challenge.week3.cli.application.ProfileOperationError.CannotDeleteActiveProfile> {
            service.handleDeleteProfile("Active")
        }
    }

    "should throw when deleting nonexistent profile" {
        shouldThrow<ProfileOperationError.NotFoundByName> {
            service.handleDeleteProfile("Nonexistent")
        }
    }

    "should show profile by name" {
        val created = service.handleCreateProfile("Showable", "Desc", "Instr")
        val shown = service.handleShowProfile("Showable")
        shown.id shouldBe created.id
        shown.name shouldBe "Showable"
        shown.description shouldBe "Desc"
        shown.instructions shouldBe "Instr"
    }

    "should show active profile when name is null" {
        val created = service.handleCreateProfile("Active", "Desc", "")
        service.handleActivateProfile(created.id)
        val shown = service.handleShowProfile(null)
        shown.id shouldBe created.id
        shown.name shouldBe "Active"
    }

    "should throw when showing nonexistent profile by name" {
        shouldThrow<ProfileOperationError.NotFoundByName> {
            service.handleShowProfile("Nonexistent")
        }
    }

    "should throw when showing active profile but none active" {
        shouldThrow<ProfileOperationError.NotFoundByName> {
            service.handleShowProfile(null)
        }
    }

    "should reject content exceeding max length on create" {
        val tooLong = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)
        shouldThrow<ProfileOperationError.ContentTooLong> {
            service.handleCreateProfile("Test", tooLong, "")
        }
    }

    "should reject content exceeding max length on edit" {
        service.handleCreateProfile("Original", "Short", "")
        val tooLong = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)
        shouldThrow<ProfileOperationError.ContentTooLong> {
            service.handleEditProfile("Original", newName = null, newDescription = tooLong, newInstructions = null)
        }
    }

    "should accept content exactly at max length on create" {
        val exact = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)
        val profile = service.handleCreateProfile("Exact", exact, "")
        profile.description.length shouldBe ProfileService.MAX_PROFILE_CONTENT_LENGTH
    }

    "should accept content exactly at max length on edit" {
        service.handleCreateProfile("Original", "Short", "")
        val exact = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)
        val updated =
            service.handleEditProfile("Original", newName = null, newDescription = exact, newInstructions = null)
        updated.description.length shouldBe ProfileService.MAX_PROFILE_CONTENT_LENGTH
    }

    "should create profile with description and instructions" {
        val profile = service.handleCreateProfile("Test", "My description", "My instructions")
        profile.name shouldBe "Test"
        profile.description shouldBe "My description"
        profile.instructions shouldBe "My instructions"
    }

    "should reject empty description and instructions" {
        shouldThrow<ProfileOperationError.EmptyContent> {
            service.handleCreateProfile("Test", "", "")
        }
    }

    "should create profile with only description" {
        val profile = service.handleCreateProfile("Test", "Only desc", "")
        profile.description shouldBe "Only desc"
        profile.instructions shouldBe ""
    }

    "should create profile with only instructions" {
        val profile = service.handleCreateProfile("Test", "", "Only instr")
        profile.description shouldBe ""
        profile.instructions shouldBe "Only instr"
    }
})
