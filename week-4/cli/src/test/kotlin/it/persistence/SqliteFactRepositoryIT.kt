package io.averkhogliad.ai.challenge.week4.cli.it.persistence

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Fact
import io.averkhogliad.ai.challenge.week4.cli.domain.model.FactId
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week4.cli.infrastructure.persistence.SqliteFactRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.time.Instant

class SqliteFactRepositoryIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteFactRepository

    beforeEach {
        tempDbFile = Files.createTempFile("test-fact-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteFactRepository(database)
    }

    afterEach {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "save() и findById()" - {
        "save and find fact" {
            runTest {
                val f = createFact("f-1", "Hello Kotlin")
                repository.save(f)
                val found = repository.findById(f.id)
                (found != null) shouldBe true
                found!!.content shouldBe f.content
            }
        }

        "return null for missing" {
            runTest {
                repository.findById(FactId("nope")) shouldBe null
            }
        }

        "upsert on re-save" {
            runTest {
                val f = createFact("f-1", "old")
                repository.save(f)
                repository.save(f.copy(content = "new"))
                repository.findById(f.id)!!.content shouldBe "new"
            }
        }
    }

    "findAll()" - {
        "empty initially" {
            runTest {
                repository.findAll().isEmpty() shouldBe true
            }
        }

        "returns all saved" {
            runTest {
                repository.save(createFact("a", "A"))
                repository.save(createFact("b", "B"))
                repository.findAll().size shouldBe 2
            }
        }
    }

    "delete()" - {
        "deletes fact" {
            runTest {
                val f = createFact("f-1", "del")
                repository.save(f)
                repository.delete(f.id)
                repository.findById(f.id) shouldBe null
            }
        }

        "no-op on missing" {
            runTest {
                repository.delete(FactId("no"))
            }
        }
    }

    "count()" - {
        "zero initially" {
            runTest { repository.count() shouldBe 0 }
        }

        "counts saved" {
            runTest {
                repository.save(createFact("a", "a"))
                repository.save(createFact("b", "b"))
                repository.count() shouldBe 2
            }
        }
    }

    "search() — FTS5" - {
        "exact word match" {
            runTest {
                repository.save(createFact("f-1", "Kotlin is modern"))
                repository.save(createFact("f-2", "Java classic"))
                repository.search("Kotlin").size shouldBe 1
            }
        }

        "multiple matches" {
            runTest {
                repository.save(createFact("f-1", "Kotlin great"))
                repository.save(createFact("f-2", "Learning Kotlin"))
                repository.search("Kotlin").size shouldBe 2
            }
        }

        "no match returns empty" {
            runTest {
                repository.save(createFact("f-1", "Kotlin"))
                repository.search("xyzunknown").isEmpty() shouldBe true
            }
        }

        "case insensitive" {
            runTest {
                repository.save(createFact("f-1", "KOTLIN"))
                repository.search("kotlin").isNotEmpty() shouldBe true
            }
        }
    }
})

private fun createFact(id: String, content: String) =
    Fact(FactId(id), content, Instant.now())
