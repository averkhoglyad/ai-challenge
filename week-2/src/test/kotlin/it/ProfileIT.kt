package io.averkhogliad.ai.challenge.week2.it

import io.averkhogliad.ai.challenge.week2.application.ProfileOperationError
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteProfileRepository
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files

class ProfileIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteProfileRepository
    lateinit var service: ProfileService

    beforeEach {
        tempDbFile = Files.createTempFile("test-profile-it-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteProfileRepository(database)
        service = ProfileService(repository)
    }

    afterEach {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "full flow" - {

        "creates, lists and activates profiles" {
            runTest {
                // given — два неактивных профиля
                val profileA = service.handleCreateProfile("Profile A", "Content for A", "")
                profileA.isActive shouldBe false
                profileA.name shouldBe "Profile A"

                val profileB = service.handleCreateProfile("Profile B", "Content for B", "")
                profileB.isActive shouldBe false

                // when — список и активация
                val profiles = service.handleListProfiles()
                profiles shouldHaveSize 2
                profiles.find { it.isActive }.shouldBeNull()

                service.handleActivateByName("Profile A")
                val active = service.handleGetActiveProfile()
                active.shouldNotBeNull()
                active.name shouldBe "Profile A"

                // when — переключение на второй профиль
                service.handleActivateProfile(profileB.id)

                // then
                val active2 = service.handleGetActiveProfile()
                active2.shouldNotBeNull()
                active2.name shouldBe "Profile B"

                val profileAAfter = repository.findById(profileA.id)
                profileAAfter.shouldNotBeNull()
                profileAAfter.isActive shouldBe false
            }
        }

        "edits profile name and description, persists changes" {
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

                val found = repository.findById(profile.id)
                found.shouldNotBeNull()
                found.name shouldBe "Renamed"
            }
        }

        "deletes inactive profile and keeps active one" {
            runTest {
                // given
                val profile = service.handleCreateProfile("Solo", "Content", "")
                profile.isActive shouldBe false
                val second = service.handleCreateProfile("Backup", "Content", "")

                service.handleActivateProfile(profile.id)
                repository.findById(profile.id)!!.isActive shouldBe true
                service.handleActivateProfile(second.id)

                // when
                service.handleDeleteProfile("Solo")

                // then
                val active = service.handleGetActiveProfile()
                active.shouldNotBeNull()
                active.name shouldBe "Backup"
                repository.findById(profile.id).shouldBeNull()
            }
        }

        "shows active profile after switch" {
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

    "validation" - {

        "rejects duplicate profile names" {
            runTest {
                service.handleCreateProfile("Unique Name", "Content", "")

                shouldThrowExactly<ProfileOperationError.AlreadyExists> {
                    service.handleCreateProfile("Unique Name", "Other Content", "")
                }
            }
        }

        "throws when activating nonexistent profile by id" {
            runTest {
                shouldThrowExactly<ProfileOperationError.NotFoundById> {
                    service.handleActivateProfile(ProfileId("nonexistent-id"))
                }
            }
        }

        "persists profiles across operations in the real database" {
            runTest {
                // given
                val profile = service.handleCreateProfile("Persistent", "Description", "Instructions")

                // when — прямой запрос к репозиторию
                val found = repository.findById(profile.id)

                // then
                found.shouldNotBeNull()
                found.name shouldBe "Persistent"
                found.description shouldBe "Description"
                found.instructions shouldBe "Instructions"
            }
        }
    }
})
