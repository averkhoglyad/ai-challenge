package io.averkhogliad.ai.challenge.week3.cli.unit.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.InMemoryProfileRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.runBlocking
import java.time.Instant

class InMemoryProfileRepositoryTest : FreeSpec({

    lateinit var repository: InMemoryProfileRepository
    val now = Instant.now()

    beforeTest {
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

    "InMemoryProfileRepository" - {

        "save and findById" - {
            "should save and retrieve profile by id" {
                val profile = createProfile()
                runBlocking { repository.save(profile) }
                val found = runBlocking { repository.findById(ProfileId("id-1")) }
                found shouldNotBe null
                found!!.name shouldBe "Test"
            }

            "should return null when profile not found by id" {
                val found = runBlocking { repository.findById(ProfileId("nonexistent")) }
                found shouldBe null
            }
        }

        "findByName" - {
            "should find profile by name" {
                val profile = createProfile(name = "UniqueName")
                runBlocking { repository.save(profile) }
                val found = runBlocking { repository.findByName("UniqueName") }
                found shouldNotBe null
                found!!.name shouldBe "UniqueName"
            }

            "should return null when profile not found by name" {
                val found = runBlocking { repository.findByName("Nonexistent") }
                found shouldBe null
            }
        }

        "findAll" - {
            "should return all profiles" {
                runBlocking {
                    repository.save(createProfile("id-1", "Profile A"))
                    repository.save(createProfile("id-2", "Profile B"))
                }
                val all = runBlocking { repository.findAll() }
                all shouldHaveSize 2
            }

            "should return empty list when no profiles" {
                val all = runBlocking { repository.findAll() }
                all.isEmpty() shouldBe true
            }
        }

        "findActive" - {
            "should find active profile" {
                runBlocking {
                    repository.save(createProfile("id-1", "Inactive"))
                    repository.save(createProfile("id-2", "Active", isActive = true))
                }
                val active = runBlocking { repository.findActive() }
                active shouldNotBe null
                active!!.name shouldBe "Active"
            }

            "should return null when no active profile" {
                runBlocking { repository.save(createProfile("id-1", "Inactive")) }
                val active = runBlocking { repository.findActive() }
                active shouldBe null
            }
        }

        "delete" - {
            "should delete profile" {
                val profile = createProfile()
                runBlocking { repository.save(profile) }
                runBlocking { repository.delete(ProfileId("id-1")) }
                val found = runBlocking { repository.findById(ProfileId("id-1")) }
                found shouldBe null
            }
        }

        "existsByName" - {
            "should check existence by name" {
                runBlocking { repository.save(createProfile(name = "Existing")) }
                runBlocking { repository.existsByName("Existing") } shouldBe true
                runBlocking { repository.existsByName("Nonexistent") } shouldBe false
            }
        }

        "clearActive" - {
            "should clear active profile" {
                runBlocking {
                    repository.save(createProfile("id-1", "Profile A", isActive = true))
                    repository.save(createProfile("id-2", "Profile B"))
                    repository.clearActive()
                }
                val active = runBlocking { repository.findActive() }
                active shouldBe null
                val profileA = runBlocking { repository.findById(ProfileId("id-1")) }
                profileA shouldNotBe null
                profileA!!.isActive shouldBe false
            }
        }

        "save and update" - {
            "should save profile and update existing" {
                val profile = createProfile()
                runBlocking { repository.save(profile) }
                val updated = profile.activate()
                runBlocking { repository.save(updated) }
                val found = runBlocking { repository.findById(ProfileId("id-1")) }
                found shouldNotBe null
                found!!.isActive shouldBe true
            }
        }
    }
})
