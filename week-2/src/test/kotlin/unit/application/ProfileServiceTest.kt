package io.averkhogliad.ai.challenge.week2.unit.application

import io.averkhogliad.ai.challenge.week2.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.domain.model.Profile
import io.averkhogliad.ai.challenge.week2.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week2.domain.service.ProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant

class ProfileServiceTest : FreeSpec({

    lateinit var repository: ProfileRepository
    lateinit var service: ProfileService

    val now = Instant.now()

    beforeEach {
        repository = mockk()
        service = ProfileService(repository)
    }

    "handleCreateProfile" - {
        "should create profile as inactive" {
            runTest {
                coEvery { repository.existsByName(any()) } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                val profile = service.handleCreateProfile("First", "Description", "Instructions")

                profile.name shouldBe "First"
                profile.description shouldBe "Description"
                profile.instructions shouldBe "Instructions"
                profile.isActive shouldBe false
            }
        }

        "should create second profile as inactive too" {
            runTest {
                coEvery { repository.existsByName(any()) } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                service.handleCreateProfile("First", "Desc", "")
                val second = service.handleCreateProfile("Second", "Desc", "")

                second.name shouldBe "Second"
                second.isActive shouldBe false
            }
        }

        "should reject duplicate profile name" {
            runTest {
                coEvery { repository.existsByName("Unique") } returns false andThen true
                coEvery { repository.save(any()) } answers { firstArg() }

                service.handleCreateProfile("Unique", "Desc", "")

                shouldThrow<ProfileOperationError.AlreadyExists> {
                    service.handleCreateProfile("Unique", "Other", "")
                }
            }
        }

        "should create profile with description and instructions" {
            runTest {
                coEvery { repository.existsByName(any()) } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                val profile = service.handleCreateProfile("Test", "My description", "My instructions")

                profile.name shouldBe "Test"
                profile.description shouldBe "My description"
                profile.instructions shouldBe "My instructions"
            }
        }

        "should reject empty description and instructions" {
            runTest {
                shouldThrow<ProfileOperationError.EmptyContent> {
                    service.handleCreateProfile("Test", "", "")
                }
            }
        }

        "should create profile with only description" {
            runTest {
                coEvery { repository.existsByName(any()) } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                val profile = service.handleCreateProfile("Test", "Only desc", "")

                profile.description shouldBe "Only desc"
                profile.instructions shouldBe ""
            }
        }

        "should create profile with only instructions" {
            runTest {
                coEvery { repository.existsByName(any()) } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                val profile = service.handleCreateProfile("Test", "", "Only instr")

                profile.description shouldBe ""
                profile.instructions shouldBe "Only instr"
            }
        }

        "should reject content exceeding max length on create" {
            runTest {
                val tooLong = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)

                shouldThrow<ProfileOperationError.ContentTooLong> {
                    service.handleCreateProfile("Test", tooLong, "")
                }
            }
        }

        "should accept content exactly at max length on create" {
            runTest {
                coEvery { repository.existsByName(any()) } returns false
                coEvery { repository.save(any()) } answers { firstArg() }
                val exact = "x".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)

                val profile = service.handleCreateProfile("Exact", exact, "")

                profile.description.length shouldBe ProfileService.MAX_PROFILE_CONTENT_LENGTH
            }
        }

        "creating profile should not activate it" {
            runTest {
                coEvery { repository.existsByName(any()) } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                val profile = service.handleCreateProfile("New", "Desc", "")

                profile.isActive shouldBe false
            }
        }
    }

    "handleListProfiles" - {
        "should list all profiles" {
            runTest {
                val profileA = makeProfile("A")
                val profileB = makeProfile("B")
                coEvery { repository.findAll() } returns listOf(profileA, profileB)

                val profiles = service.handleListProfiles()

                profiles shouldHaveSize 2
            }
        }

        "should list empty profiles" {
            runTest {
                coEvery { repository.findAll() } returns emptyList()

                val profiles = service.handleListProfiles()

                profiles.shouldBeEmpty()
            }
        }
    }

    "handleActivateProfile" - {
        "should activate profile by id" {
            runTest {
                val first = makeProfile("First", id = ProfileId("id-first"), isActive = false)
                val second = makeProfile("Second", id = ProfileId("id-second"), isActive = false)
                coEvery { repository.findById(ProfileId("id-second")) } returns second
                coEvery { repository.clearActive() } returns Unit
                coEvery { repository.save(any()) } answers { firstArg() }

                val activated = service.handleActivateProfile(ProfileId("id-second"))

                activated.isActive shouldBe true
            }
        }

        "should throw when activating nonexistent profile" {
            runTest {
                coEvery { repository.findById(ProfileId("nonexistent")) } returns null

                shouldThrow<ProfileOperationError.NotFoundById> {
                    service.handleActivateProfile(ProfileId("nonexistent"))
                }
            }
        }
    }

    "handleActivateByName" - {
        "should activate profile by name" {
            runTest {
                val second = makeProfile("Second", isActive = false)
                coEvery { repository.findByName("Second") } returns second
                coEvery { repository.clearActive() } returns Unit
                coEvery { repository.save(any()) } answers { firstArg() }

                val activated = service.handleActivateByName("Second")

                activated.isActive shouldBe true
                activated.name shouldBe "Second"
            }
        }

        "should throw when activating by nonexistent name" {
            runTest {
                coEvery { repository.findByName("Nonexistent") } returns null

                shouldThrow<ProfileOperationError.NotFoundByName> {
                    service.handleActivateByName("Nonexistent")
                }
            }
        }
    }

    "handleDeactivateProfile" - {
        "should clear active profile" {
            runTest {
                coEvery { repository.clearActive() } returns Unit

                service.handleDeactivateProfile()

                coVerify(exactly = 1) { repository.clearActive() }
            }
        }
    }

    "handleGetActiveProfile" - {
        "should return active profile" {
            runTest {
                val active = makeProfile("First", isActive = true)
                coEvery { repository.findActive() } returns active

                val result = service.handleGetActiveProfile()

                result.shouldNotBeNull()
                result.name shouldBe "First"
            }
        }

        "should return null when no active profile" {
            runTest {
                coEvery { repository.findActive() } returns null

                val result = service.handleGetActiveProfile()

                result.shouldBeNull()
            }
        }
    }

    "handleEditProfile" - {
        "should edit profile name" {
            runTest {
                val original = makeProfile("Original")
                coEvery { repository.findByName("Original") } returns original
                coEvery { repository.existsByName("Updated") } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                val updated = service.handleEditProfile(
                    "Original",
                    newName = "Updated",
                    newDescription = null,
                    newInstructions = null
                )

                updated.name shouldBe "Updated"
                updated.description shouldBe "Desc"
            }
        }

        "should edit profile description" {
            runTest {
                val original = makeProfile("Original")
                coEvery { repository.findByName("Original") } returns original
                coEvery { repository.save(any()) } answers { firstArg() }

                val updated = service.handleEditProfile(
                    "Original",
                    newName = null,
                    newDescription = "New Desc",
                    newInstructions = null
                )

                updated.name shouldBe "Original"
                updated.description shouldBe "New Desc"
            }
        }

        "should edit profile instructions" {
            runTest {
                val original = makeProfile("Original", description = "", instructions = "Instr")
                coEvery { repository.findByName("Original") } returns original
                coEvery { repository.save(any()) } answers { firstArg() }

                val updated = service.handleEditProfile(
                    "Original",
                    newName = null,
                    newDescription = null,
                    newInstructions = "New Instr"
                )

                updated.name shouldBe "Original"
                updated.instructions shouldBe "New Instr"
            }
        }

        "should edit profile name and description" {
            runTest {
                val original = makeProfile("Original")
                coEvery { repository.findByName("Original") } returns original
                coEvery { repository.existsByName("Renamed") } returns false
                coEvery { repository.save(any()) } answers { firstArg() }

                val updated = service.handleEditProfile(
                    "Original",
                    newName = "Renamed",
                    newDescription = "Changed",
                    newInstructions = null
                )

                updated.name shouldBe "Renamed"
                updated.description shouldBe "Changed"
            }
        }

        "should throw when editing nonexistent profile" {
            runTest {
                coEvery { repository.findByName("Nonexistent") } returns null

                shouldThrow<ProfileOperationError.NotFoundByName> {
                    service.handleEditProfile(
                        "Nonexistent",
                        newName = "Whatever",
                        newDescription = null,
                        newInstructions = null
                    )
                }
            }
        }

        "should reject duplicate name when editing" {
            runTest {
                val second = makeProfile("Second")
                coEvery { repository.findByName("Second") } returns second
                coEvery { repository.existsByName("First") } returns true

                shouldThrow<ProfileOperationError.AlreadyExists> {
                    service.handleEditProfile(
                        "Second",
                        newName = "First",
                        newDescription = null,
                        newInstructions = null
                    )
                }
            }
        }

        "should reject content exceeding max length on edit" {
            runTest {
                val original = makeProfile("Original", description = "Short")
                val tooLong = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH + 1)
                coEvery { repository.findByName("Original") } returns original

                shouldThrow<ProfileOperationError.ContentTooLong> {
                    service.handleEditProfile(
                        "Original",
                        newName = null,
                        newDescription = tooLong,
                        newInstructions = null
                    )
                }
            }
        }

        "should accept content exactly at max length on edit" {
            runTest {
                val original = makeProfile("Original", description = "Short")
                val exact = "y".repeat(ProfileService.MAX_PROFILE_CONTENT_LENGTH)
                coEvery { repository.findByName("Original") } returns original
                coEvery { repository.save(any()) } answers { firstArg() }

                val updated = service.handleEditProfile(
                    "Original",
                    newName = null,
                    newDescription = exact,
                    newInstructions = null
                )

                updated.description.length shouldBe ProfileService.MAX_PROFILE_CONTENT_LENGTH
            }
        }
    }

    "handleDeleteProfile" - {
        "should delete non-active profile" {
            runTest {
                val profile = makeProfile("ToDelete", isActive = false)
                coEvery { repository.findByName("ToDelete") } returns profile
                coEvery { repository.delete(profile.id) } returns Unit

                service.handleDeleteProfile("ToDelete")

                coVerify(exactly = 1) { repository.delete(profile.id) }
            }
        }

        "should throw CannotDeleteActiveProfile when deleting active profile" {
            runTest {
                val profile = makeProfile("Active", isActive = true)
                coEvery { repository.findByName("Active") } returns profile

                shouldThrow<ProfileOperationError.CannotDeleteActiveProfile> {
                    service.handleDeleteProfile("Active")
                }
            }
        }

        "should throw when deleting nonexistent profile" {
            runTest {
                coEvery { repository.findByName("Nonexistent") } returns null

                shouldThrow<ProfileOperationError.NotFoundByName> {
                    service.handleDeleteProfile("Nonexistent")
                }
            }
        }
    }

    "handleShowProfile" - {
        "should show profile by name" {
            runTest {
                val created = makeProfile("Showable", description = "Desc", instructions = "Instr")
                coEvery { repository.findByName("Showable") } returns created

                val shown = service.handleShowProfile("Showable")

                shown.id shouldBe created.id
                shown.name shouldBe "Showable"
                shown.description shouldBe "Desc"
                shown.instructions shouldBe "Instr"
            }
        }

        "should show active profile when name is null" {
            runTest {
                val active = makeProfile("Active", isActive = true)
                coEvery { repository.findActive() } returns active

                val shown = service.handleShowProfile(null)

                shown.id shouldBe active.id
                shown.name shouldBe "Active"
            }
        }

        "should throw when showing nonexistent profile by name" {
            runTest {
                coEvery { repository.findByName("Nonexistent") } returns null

                shouldThrow<ProfileOperationError.NotFoundByName> {
                    service.handleShowProfile("Nonexistent")
                }
            }
        }

        "should throw when showing active profile but none active" {
            runTest {
                coEvery { repository.findActive() } returns null

                shouldThrow<ProfileOperationError.NotFoundByName> {
                    service.handleShowProfile(null)
                }
            }
        }
    }
}) {
    companion object {
        private val now = Instant.now()

        fun makeProfile(
            name: String,
            id: ProfileId = ProfileId(java.util.UUID.randomUUID().toString()),
            description: String = "Desc",
            instructions: String = "",
            isActive: Boolean = false
        ): Profile = Profile(
            id = id,
            name = name,
            description = description,
            instructions = instructions,
            isActive = isActive,
            createdAt = now,
            updatedAt = now
        )
    }
}
