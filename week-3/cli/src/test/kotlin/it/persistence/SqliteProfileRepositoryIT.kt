package io.averkhogliad.ai.challenge.week3.cli.it.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence.SqliteProfileRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.Instant

class SqliteProfileRepositoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteProfileRepository

    beforeTest {
        tempDbFile = Files.createTempFile("test-profile-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteProfileRepository(database)
    }

    afterTest {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    fun createProfile(
        id: String,
        name: String,
        description: String,
        instructions: String,
        isActive: Boolean = false
    ) = Profile(
        id = ProfileId(id),
        name = name,
        description = description,
        instructions = instructions,
        isActive = isActive,
        createdAt = Instant.now(),
        updatedAt = Instant.now()
    )

    "save() и findById()" - {
        "save and find profile by id" {
            runTest {
                val p = createProfile("p-1", "Test", "Desc", "Instr")
                repository.save(p)
                val found = repository.findById(p.id)
                found shouldNotBe null
                found!!.name shouldBe "Test"
                found.description shouldBe "Desc"
                found.instructions shouldBe "Instr"
            }
        }

        "return null for missing id" {
            runTest {
                repository.findById(ProfileId("nope")) shouldBe null
            }
        }

        "upsert on re-save" {
            runTest {
                val p = createProfile("p-1", "Old", "Old desc", "Old instr")
                repository.save(p)
                repository.save(p.copy(name = "New", description = "New desc", instructions = "New instr"))
                val updated = repository.findById(p.id)
                updated shouldNotBe null
                updated!!.name shouldBe "New"
                updated.description shouldBe "New desc"
                updated.instructions shouldBe "New instr"
            }
        }
    }

    "findByName()" - {
        "find profile by name" {
            runTest {
                val p = createProfile("p-1", "Unique", "Desc", "Instr")
                repository.save(p)
                val found = repository.findByName("Unique")
                found shouldNotBe null
                found!!.id shouldBe p.id
            }
        }

        "return null when name not found" {
            runTest {
                repository.findByName("Nonexistent") shouldBe null
            }
        }
    }

    "findAll()" - {
        "empty initially" {
            runTest {
                repository.findAll().isEmpty() shouldBe true
            }
        }

        "returns all saved sorted by created_at" {
            runTest {
                repository.save(createProfile("a", "A", "Desc A", "Instr A"))
                repository.save(createProfile("b", "B", "Desc B", "Instr B"))
                val all = repository.findAll()
                all shouldHaveSize 2
                all[0].name shouldBe "A"
                all[1].name shouldBe "B"
            }
        }
    }

    "findActive()" - {
        "return null when no active" {
            runTest {
                repository.findActive() shouldBe null
            }
        }

        "find active profile" {
            runTest {
                val active = createProfile("p-1", "Active", "Desc", "Instr", isActive = true)
                val inactive = createProfile("p-2", "Inactive", "Desc", "Instr", isActive = false)
                repository.save(active)
                repository.save(inactive)
                val found = repository.findActive()
                found shouldNotBe null
                found!!.name shouldBe "Active"
                found.isActive shouldBe true
            }
        }

        "return only first active" {
            runTest {
                repository.save(createProfile("p-1", "First", "Desc", "Instr", isActive = true))
                repository.save(createProfile("p-2", "Second", "Desc", "Instr", isActive = true))
                val found = repository.findActive()
                found shouldNotBe null
                found!!.name shouldBe "First"
            }
        }
    }

    "delete()" - {
        "deletes profile" {
            runTest {
                val p = createProfile("p-1", "ToDelete", "Desc", "Instr")
                repository.save(p)
                repository.delete(p.id)
                repository.findById(p.id) shouldBe null
            }
        }

        "no-op on missing id" {
            runTest {
                repository.delete(ProfileId("nonexistent"))
            }
        }
    }

    "existsByName()" - {
        "returns true when exists" {
            runTest {
                repository.save(createProfile("p-1", "Exists", "Desc", "Instr"))
                repository.existsByName("Exists") shouldBe true
            }
        }

        "returns false when not exists" {
            runTest {
                repository.existsByName("Nonexistent") shouldBe false
            }
        }
    }

    "clearActive()" - {
        "deactivates active profile" {
            runTest {
                repository.save(createProfile("p-1", "WasActive", "Desc", "Instr", isActive = true))
                repository.clearActive()
                repository.findActive() shouldBe null
            }
        }

        "does nothing when no active profile" {
            runTest {
                repository.clearActive()
                repository.findActive() shouldBe null
            }
        }
    }
})
