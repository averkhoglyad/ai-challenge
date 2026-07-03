package io.averkhogliad.ai.challenge.week4.cli.unit.infrastructure.persistence

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.time.Instant

class InMemoryProfileRepositoryTest : FreeSpec({

    lateinit var repository: InMemoryProfileRepository
    val now = Instant.now()

    beforeEach {
        repository = InMemoryProfileRepository()
    }

    fun createProfile(id: String = "id-1", name: String = "Test", isActive: Boolean = false): Profile {
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

    "findById" - {

        "should save and retrieve profile by id" {
            runTest {
                // given
                val profile = createProfile()
                repository.save(profile)

                // when
                val found = repository.findById(ProfileId("id-1"))

                // then
                (found != null) shouldBe true
                found!!.name shouldBe "Test"
            }
        }

        "should return null when profile not found by id" {
            runTest {
                // when
                val found = repository.findById(ProfileId("nonexistent"))

                // then
                found shouldBe null
            }
        }
    }

    "findByName" - {

        "should find profile by name" {
            runTest {
                // given
                val profile = createProfile(name = "UniqueName")
                repository.save(profile)

                // when
                val found = repository.findByName("UniqueName")

                // then
                (found != null) shouldBe true
                found!!.name shouldBe "UniqueName"
            }
        }

        "should return null when profile not found by name" {
            runTest {
                // when
                val found = repository.findByName("Nonexistent")

                // then
                found shouldBe null
            }
        }
    }

    "findAll" - {

        "should return all profiles" {
            runTest {
                // given
                repository.save(createProfile("id-1", "Profile A"))
                repository.save(createProfile("id-2", "Profile B"))

                // when
                val all = repository.findAll()

                // then
                all.size shouldBe 2
            }
        }

        "should return empty list when no profiles" {
            runTest {
                // when
                val all = repository.findAll()

                // then
                all.isEmpty() shouldBe true
            }
        }
    }

    "findActive" - {

        "should find active profile" {
            runTest {
                // given
                repository.save(createProfile("id-1", "Inactive"))
                repository.save(createProfile("id-2", "Active", isActive = true))

                // when
                val active = repository.findActive()

                // then
                (active != null) shouldBe true
                active!!.name shouldBe "Active"
            }
        }

        "should return null when no active profile" {
            runTest {
                // given
                repository.save(createProfile("id-1", "Inactive"))

                // when
                val active = repository.findActive()

                // then
                active shouldBe null
            }
        }
    }

    "delete" - {

        "should delete profile" {
            runTest {
                // given
                val profile = createProfile()
                repository.save(profile)

                // when
                repository.delete(ProfileId("id-1"))

                // then
                val found = repository.findById(ProfileId("id-1"))
                found shouldBe null
            }
        }
    }

    "existsByName" - {

        "should check existence by name" {
            runTest {
                // given
                repository.save(createProfile(name = "Existing"))

                // then
                repository.existsByName("Existing") shouldBe true
                repository.existsByName("Nonexistent") shouldBe false
            }
        }
    }

    "clearActive" - {

        "should clear active profile" {
            runTest {
                // given
                repository.save(createProfile("id-1", "Profile A", isActive = true))
                repository.save(createProfile("id-2", "Profile B"))

                // when
                repository.clearActive()

                // then
                val active = repository.findActive()
                active shouldBe null
                val profileA = repository.findById(ProfileId("id-1"))
                (profileA != null) shouldBe true
                profileA!!.isActive shouldBe false
            }
        }
    }

    "save" - {

        "should save profile and update existing" {
            runTest {
                // given
                val profile = createProfile()
                repository.save(profile)
                val updated = profile.activate()

                // when
                repository.save(updated)

                // then
                val found = repository.findById(ProfileId("id-1"))
                (found != null) shouldBe true
                found!!.isActive shouldBe true
            }
        }
    }
})
