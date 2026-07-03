package io.averkhogliad.ai.challenge.week3.cli.it

import io.averkhogliad.ai.challenge.week3.cli.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week3.cli.application.ProfileService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week3.cli.domain.service.ProfileRepository
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest

/**
 * Интеграционные тесты для полного потока операций с профилями.
 *
 * Проверяет взаимодействие между слоями:
 * - [ProfileService] (Application)
 * - [InMemoryProfileRepository] (Infrastructure)
 */
class ProfileIT : FreeSpec({

    val repository: ProfileRepository = InMemoryProfileRepository()
    val service = ProfileService(repository)

    "full flow - create list activate" - {
        runTest {
            // Создаём первый профиль (теперь создаётся неактивным)
            val profileA = service.handleCreateProfile("Profile A", "Content for A", "")
            profileA.isActive shouldBe false
            profileA.name shouldBe "Profile A"

            // Создаём второй профиль (должен быть неактивным)
            val profileB = service.handleCreateProfile("Profile B", "Content for B", "")
            profileB.isActive shouldBe false

            // Проверяем список
            val profiles = service.handleListProfiles()
            profiles.size shouldBe 2
            val activeInList = profiles.find { it.isActive }
            activeInList shouldBe null

            // Активируем Profile A по имени
            service.handleActivateByName("Profile A")
            val active = service.handleGetActiveProfile()
            active.shouldNotBeNull()
            active.name shouldBe "Profile A"

            // Активируем второй профиль
            service.handleActivateProfile(profileB.id)

            // Проверяем, что теперь активен Profile B
            val active2 = service.handleGetActiveProfile()
            active2.shouldNotBeNull()
            active2.name shouldBe "Profile B"

            // Проверяем, что Profile A теперь неактивен
            val profileAAfter = repository.findById(profileA.id)
            profileAAfter.shouldNotBeNull()
            profileAAfter.isActive shouldBe false
        }
    }

    "should reject duplicate profile names" - {
        runTest {
            service.handleCreateProfile("Unique Name", "Content", "")
            shouldThrow<ProfileOperationError.AlreadyExists> {
                service.handleCreateProfile("Unique Name", "Other Content", "")
            }
        }
    }

    "should throw when activating nonexistent profile" - {
        runTest {
            shouldThrow<ProfileOperationError.NotFoundById> {
                service.handleActivateProfile(ProfileId("nonexistent-id"))
            }
        }
    }

    "should persist profiles across operations" - {
        runTest {
            val profile = service.handleCreateProfile("Persistent", "Description", "Instructions")
            val found = repository.findById(profile.id)
            found.shouldNotBeNull()
            found.name shouldBe "Persistent"
            found.description shouldBe "Description"
            found.instructions shouldBe "Instructions"
        }
    }

    "full flow - edit profile name and description" - {
        runTest {
            val profile = service.handleCreateProfile("Original", "Original Description", "Original Instructions")
            val updated = service.handleEditProfile(
                "Original",
                newName = "Renamed",
                newDescription = "Updated Description",
                newInstructions = null
            )
            updated.name shouldBe "Renamed"
            updated.description shouldBe "Updated Description"
            // Verify persistence
            val found = repository.findById(profile.id)
            found.shouldNotBeNull()
            found.name shouldBe "Renamed"
        }
    }

    "full flow - delete active profile and verify active cleared" - {
        runTest {
            // Создаём два профиля
            val profile = service.handleCreateProfile("Solo", "Content", "")
            profile.isActive shouldBe false
            val second = service.handleCreateProfile("Backup", "Content", "")
            // Активируем Solo, затем переключаемся на Backup, чтобы Solo стал неактивным
            service.handleActivateProfile(profile.id)
            repository.findById(profile.id)!!.isActive shouldBe true
            service.handleActivateProfile(second.id)
            // Теперь удаляем первый
            service.handleDeleteProfile("Solo")
            val active = service.handleGetActiveProfile()
            active.shouldNotBeNull()
            active.name shouldBe "Backup"
            val found = repository.findById(profile.id)
            found shouldBe null
        }
    }

    "full flow - show active profile after switch" - {
        runTest {
            service.handleCreateProfile("First", "Description A", "Instructions A")
            val second = service.handleCreateProfile("Second", "Description B", "Instructions B")
            service.handleActivateProfile(second.id)
            val shown = service.handleShowProfile(null)
            shown.name shouldBe "Second"
            shown.description shouldBe "Description B"
        }
    }
})
