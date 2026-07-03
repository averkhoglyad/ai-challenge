package io.averkhogliad.ai.challenge.week4.cli.unit.application

import io.averkhogliad.ai.challenge.week4.cli.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week4.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ProfileRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.InMemoryProfileRepository
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

    "create profile" - {
        "should create profile as inactive" {
            // when
            val profile = service.handleCreateProfile("First", "Description", "Instructions")

            // then
            profile.name shouldBe "First"
            profile.description shouldBe "Description"
            profile.instructions shouldBe "Instructions"
            profile.isActive shouldBe false
        }

        "should create second profile as inactive too" {
            // given
            service.handleCreateProfile("First", "Desc", "")

            // when
            val second = service.handleCreateProfile("Second", "Desc", "")

            // then
            second.name shouldBe "Second"
            second.isActive shouldBe false
        }

        "should reject duplicate profile name" {
            // given
            service.handleCreateProfile("Unique", "Desc", "")

            // when & then
            shouldThrow<ProfileOperationError.AlreadyExists> {
                service.handleCreateProfile("Unique", "Other", "")
            }
        }

        "creating profile should not activate it" {
            // when
            val profile = service.handleCreateProfile("New", "Desc", "")

            // then
            profile.isActive shouldBe false
            service.handleGetActiveProfile().shouldBeNull()
        }

        "should create profile with description and instructions" {
            // when
            val profile = service.handleCreateProfile("Test", "My description", "My instructions")

            // then
            profile.name shouldBe "Test"
            profile.description shouldBe "My description"
            profile.instructions shouldBe "My instructions"
        }

        "should reject empty description and instructions" {
            // when & then
            shouldThrow<ProfileOperationError.EmptyContent> {
                service.handleCreateProfile("Test", "", "")
            }
        }

        "should create profile with only description" {
            // when
            val profile = service.handleCreateProfile("Test", "Only desc", "")

            // then
            profile.description shouldBe "Only desc"
            profile.instructions shouldBe ""
        }

        "should create profile with only instructions" {
            // when
            val profile = service.handleCreateProfile("Test", "", "Only instr")

            // then
            profile.description shouldBe ""
            profile.instructions shouldBe "Only instr"
        }
    }

    "list profiles" - {
        "should list all profiles" {
            // given
            service.handleCreateProfile("A", "Desc A", "")
            service.handleCreateProfile("B", "Desc B", "")

            // when
            val profiles = service.handleListProfiles()

            // then
            profiles.size shouldBe 2
        }

        "should list empty profiles" {
            // when
            val profiles = service.handleListProfiles()

            // then
            profiles.isEmpty() shouldBe true
        }
    }

    "activate profile" - {
        "should activate profile by id" {
            // given
            val first = service.handleCreateProfile("First", "Desc", "")
            val second = service.handleCreateProfile("Second", "Desc", "")
            first.isActive shouldBe false
            second.isActive shouldBe false

            // when
            val activated = service.handleActivateProfile(second.id)

            // then
            activated.isActive shouldBe true
            val firstAfter = repository.findById(first.id)
            firstAfter.shouldNotBeNull()
            firstAfter.isActive shouldBe false
        }

        "should activate profile by name" {
            // given
            service.handleCreateProfile("First", "Desc", "")
            service.handleCreateProfile("Second", "Desc", "")
            service.handleGetActiveProfile().shouldBeNull()

            // when
            val activated = service.handleActivateByName("Second")

            // then
            activated.isActive shouldBe true
            activated.name shouldBe "Second"

            val active = service.handleGetActiveProfile()
            active.shouldNotBeNull()
            active.name shouldBe "Second"
        }

        "should throw when activating by nonexistent name" {
            // when & then
            shouldThrow<ProfileOperationError.NotFoundByName> {
                service.handleActivateByName("Nonexistent")
            }
        }

        "should throw when activating nonexistent profile" {
            // when & then
            shouldThrow<ProfileOperationError.NotFoundById> {
                service.handleActivateProfile(ProfileId("nonexistent"))
            }
        }
    }

    "deactivate profile" - {
        "should deactivate profile" {
            // given
            service.handleCreateProfile("First", "Desc", "")
            service.handleActivateByName("First")
            service.handleGetActiveProfile().shouldNotBeNull()

            // when
            service.handleDeactivateProfile()

            // then
            service.handleGetActiveProfile().shouldBeNull()
        }
    }

    "get active profile" - {
        "should get active profile" {
            // given
            service.handleCreateProfile("First", "Desc", "")
            service.handleActivateByName("First")

            // when
            val active = service.handleGetActiveProfile()

            // then
            active.shouldNotBeNull()
            active.name shouldBe "First"
        }

        "should return null when no active profile" {
            // when
            val active = service.handleGetActiveProfile()

            // then
            active.shouldBeNull()
        }
    }

    "edit profile" - {
        "should edit profile name" {
            // given
            val profile = service.handleCreateProfile("Original", "Desc", "")

            // when
            val updated =
                service.handleEditProfile(
                    "Original",
                    newName = "Updated",
                    newDescription = null,
                    newInstructions = null
                )

            // then
            updated.name shouldBe "Updated"
            updated.description shouldBe "Desc"
        }

        "should edit profile description" {
            // given
            val profile = service.handleCreateProfile("Original", "Desc", "")

            // when
            val updated =
                service.handleEditProfile(
                    "Original",
                    newName = null,
                    newDescription = "New Desc",
                    newInstructions = null
                )

            // then
            updated.name shouldBe "Original"
            updated.description shouldBe "New Desc"
        }

        "should edit profile instructions" {
            // given
            val profile = service.handleCreateProfile("Original", "", "Instr")

            // when
            val updated =
                service.handleEditProfile(
                    "Original",
                    newName = null,
                    newDescription = null,
                    newInstructions = "New Instr"
                )

            // then
            updated.name shouldBe "Original"
            updated.instructions shouldBe "New Instr"
        }

        "should edit profile name and description" {
            // given
            val profile = service.handleCreateProfile("Original", "Desc", "")

            // when
            val updated = service.handleEditProfile(
                "Original",
                newName = "Renamed",
                newDescription = "Changed",
                newInstructions = null
            )

            // then
            updated.name shouldBe "Renamed"
            updated.description shouldBe "Changed"
        }

        "should throw when editing nonexistent profile" {
            // when & then
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
            // given
            service.handleCreateProfile("First", "Desc", "")
            service.handleCreateProfile("Second", "Desc", "")

            // when & then
            shouldThrow<ProfileOperationError.AlreadyExists> {
                service.handleEditProfile("Second", newName = "First", newDescription = null, newInstructions = null)
            }
        }
    }

    "delete profile" - {
        "should delete profile" {
            // given - создаём два профиля: первый будет активным
            val profileA = service.handleCreateProfile("ToDelete", "Desc", "")
            service.handleCreateProfile("NotActive", "Desc", "")

            // given - переключаемся на второй профиль, чтобы снять активность с первого
            val profileB = service.handleActivateProfile(profileA.id)
            val second = service.handleCreateProfile("Another", "Desc", "")
            service.handleActivateProfile(second.id)

            // when - теперь ToDelete неактивен — можно удалить
            service.handleDeleteProfile("ToDelete")

            // then
            val found = repository.findById(profileA.id)
            found.shouldBeNull()
        }

        "should throw CannotDeleteActiveProfile when deleting active profile" {
            // given
            val profile = service.handleCreateProfile("Active", "Desc", "")
            service.handleActivateProfile(profile.id)

            // when & then
            shouldThrow<io.averkhogliad.ai.challenge.week4.cli.application.ProfileOperationError.CannotDeleteActiveProfile> {
                service.handleDeleteProfile("Active")
            }
        }

        "should throw when deleting nonexistent profile" {
            // when & then
            shouldThrow<ProfileOperationError.NotFoundByName> {
                service.handleDeleteProfile("Nonexistent")
            }
        }
    }

    "show profile" - {
        "should show profile by name" {
            // given
            val created = service.handleCreateProfile("Showable", "Desc", "Instr")

            // when
            val shown = service.handleShowProfile("Showable")

            // then
            shown.id shouldBe created.id
            shown.name shouldBe "Showable"
            shown.description shouldBe "Desc"
            shown.instructions shouldBe "Instr"
        }

        "should show active profile when name is null" {
            // given
            val created = service.handleCreateProfile("Active", "Desc", "")
            service.handleActivateProfile(created.id)

            // when
            val shown = service.handleShowProfile(null)

            // then
            shown.id shouldBe created.id
            shown.name shouldBe "Active"
        }

        "should throw when showing nonexistent profile by name" {
            // when & then
            shouldThrow<ProfileOperationError.NotFoundByName> {
                service.handleShowProfile("Nonexistent")
            }
        }

        "should throw when showing active profile but none active" {
            // when & then
            shouldThrow<ProfileOperationError.NotFoundByName> {
                service.handleShowProfile(null)
            }
        }
    }

    "content validation" - {
        "should reject content exceeding max length on create" {
            // given
            val tooLong = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)

            // when & then
            shouldThrow<ProfileOperationError.ContentTooLong> {
                service.handleCreateProfile("Test", tooLong, "")
            }
        }

        "should reject content exceeding max length on edit" {
            // given
            service.handleCreateProfile("Original", "Short", "")
            val tooLong = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)

            // when & then
            shouldThrow<ProfileOperationError.ContentTooLong> {
                service.handleEditProfile("Original", newName = null, newDescription = tooLong, newInstructions = null)
            }
        }

        "should accept content exactly at max length on create" {
            // given
            val exact = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)

            // when
            val profile = service.handleCreateProfile("Exact", exact, "")

            // then
            profile.description.length shouldBe ProfileService.MAX_PROFILE_CONTENT_LENGTH
        }

        "should accept content exactly at max length on edit" {
            // given
            service.handleCreateProfile("Original", "Short", "")
            val exact = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)

            // when
            val updated =
                service.handleEditProfile("Original", newName = null, newDescription = exact, newInstructions = null)

            // then
            updated.description.length shouldBe ProfileService.MAX_PROFILE_CONTENT_LENGTH
        }
    }
})
