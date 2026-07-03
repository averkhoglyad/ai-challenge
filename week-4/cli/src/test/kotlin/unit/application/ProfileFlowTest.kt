package io.averkhogliad.ai.challenge.week4.cli.unit.application

import io.averkhogliad.ai.challenge.week4.cli.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week4.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ProfileRepository
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest

/**
 * Unit-тесты для полного потока операций с профилями.
 *
 * Проверяет взаимодействие между слоями:
 * - [ProfileService] (Application)
 * - [InMemoryProfileRepository] (Infrastructure)
 */
class ProfileFlowTest : FreeSpec({

    lateinit var repository: ProfileRepository
    lateinit var service: ProfileService

    beforeEach {
        repository = InMemoryProfileRepository()
        service = ProfileService(repository)
    }

    "full flow - create list activate" {
        runTest {
            // given - создаём первый профиль (теперь создаётся неактивным)
            val profileA = service.handleCreateProfile("Profile A", "Content for A", "")
            profileA.isActive shouldBe false
            profileA.name shouldBe "Profile A"

            // given - создаём второй профиль (должен быть неактивным)
            val profileB = service.handleCreateProfile("Profile B", "Content for B", "")
            profileB.isActive shouldBe false

            // when - проверяем список
            val profiles = service.handleListProfiles()
            profiles shouldHaveSize 2
            val activeInList = profiles.find { it.isActive }

            // then
            activeInList shouldBe null

            // when - активируем Profile A по имени
            service.handleActivateByName("Profile A")

            // then
            val active = service.handleGetActiveProfile()
            active shouldNotBe null
            active!!.name shouldBe "Profile A"

            // when - активируем второй профиль
            service.handleActivateProfile(profileB.id)

            // then - теперь активен Profile B
            val active2 = service.handleGetActiveProfile()
            active2 shouldNotBe null
            active2!!.name shouldBe "Profile B"

            // then - Profile A теперь неактивен
            val profileAAfter = repository.findById(profileA.id)
            profileAAfter shouldNotBe null
            profileAAfter!!.isActive shouldBe false
        }
    }

    "error handling" - {
        "should reject duplicate profile names" {
            runTest {
                // given
                service.handleCreateProfile("Unique Name", "Content", "")

                // when & then
                shouldThrow<ProfileOperationError.AlreadyExists> {
                    service.handleCreateProfile("Unique Name", "Other Content", "")
                }
            }
        }

        "should throw when activating nonexistent profile" {
            runTest {
                // when & then
                shouldThrow<ProfileOperationError.NotFoundById> {
                    service.handleActivateProfile(ProfileId("nonexistent-id"))
                }
            }
        }
    }

    "persistence" - {
        "should persist profiles across operations" {
            runTest {
                // given
                val profile = service.handleCreateProfile("Persistent", "Description", "Instructions")

                // when
                val found = repository.findById(profile.id)

                // then
                found shouldNotBe null
                found!!.name shouldBe "Persistent"
                found.description shouldBe "Description"
                found.instructions shouldBe "Instructions"
            }
        }
    }

    "edit profile" - {
        "full flow - edit profile name and description" {
            runTest {
                // given
                val profile = service.handleCreateProfile("Original", "Original Description", "Original Instructions")

                // when
                val updated = service.handleEditProfile(
                    "Original",
                    newName = "Renamed",
                    newDescription = "Updated Description",
                    newInstructions = null
                )

                // then
                updated.name shouldBe "Renamed"
                updated.description shouldBe "Updated Description"

                // Verify persistence
                val found = repository.findById(profile.id)
                found shouldNotBe null
                found!!.name shouldBe "Renamed"
            }
        }
    }

    "delete profile" - {
        "full flow - delete active profile and verify active cleared" {
            runTest {
                // given - создаём два профиля
                val profile = service.handleCreateProfile("Solo", "Content", "")
                profile.isActive shouldBe false
                val second = service.handleCreateProfile("Backup", "Content", "")

                // given - активируем Solo, затем переключаемся на Backup, чтобы Solo стал неактивным
                service.handleActivateProfile(profile.id)
                repository.findById(profile.id)!!.isActive shouldBe true
                service.handleActivateProfile(second.id)

                // when - удаляем первый
                service.handleDeleteProfile("Solo")

                // then
                val active = service.handleGetActiveProfile()
                active shouldNotBe null
                active!!.name shouldBe "Backup"
                val found = repository.findById(profile.id)
                found shouldBe null
            }
        }
    }

    "show profile" - {
        "full flow - show active profile after switch" {
            runTest {
                // given
                service.handleCreateProfile("First", "Description A", "Instructions A")
                val second = service.handleCreateProfile("Second", "Description B", "Instructions B")
                service.handleActivateProfile(second.id)

                // when
                val shown = service.handleShowProfile(null)

                // then
                shown.name shouldBe "Second"
                shown.description shouldBe "Description B"
            }
        }
    }
})
