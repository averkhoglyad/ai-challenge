package io.averkhogliad.ai.challenge.week3.cli.infrastructure.persistence

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Fact
import io.averkhogliad.ai.challenge.week3.cli.domain.model.FactId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.*

@DisplayName("SqliteFactRepository")
class SqliteFactRepositoryTest {

    private lateinit var tempDbFile: File
    private lateinit var database: SqliteDatabase
    private lateinit var repository: SqliteFactRepository

    @BeforeEach
    fun setUp() {
        tempDbFile = Files.createTempFile("test-fact-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteFactRepository(database)
    }

    @AfterEach
    fun tearDown() {
        database.close()
        tempDbFile.delete()

        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    @Nested
    @DisplayName("save() и findById()")
    inner class SaveFind {
        @Test
        fun `save and find fact`() = runBlocking {
            val f = createFact("f-1", "Hello Kotlin")
            repository.save(f)
            val found = repository.findById(f.id)
            assertNotNull(found)
            assertEquals(f.content, found.content)
        }

        @Test
        fun `return null for missing`() = runBlocking {
            assertNull(repository.findById(FactId("nope")))
        }

        @Test
        fun `upsert on re-save`() = runBlocking {
            val f = createFact("f-1", "old")
            repository.save(f)
            repository.save(f.copy(content = "new"))
            assertEquals("new", repository.findById(f.id)!!.content)
        }
    }

    @Nested
    @DisplayName("findAll()")
    inner class FindAll {
        @Test
        fun `empty initially`() = runBlocking {
            assertTrue(repository.findAll().isEmpty())
        }

        @Test
        fun `returns all saved`() = runBlocking {
            repository.save(createFact("a", "A"))
            repository.save(createFact("b", "B"))
            assertEquals(2, repository.findAll().size)
        }
    }

    @Nested
    @DisplayName("delete()")
    inner class Delete {
        @Test
        fun `deletes fact`() = runBlocking {
            val f = createFact("f-1", "del")
            repository.save(f)
            repository.delete(f.id)
            assertNull(repository.findById(f.id))
        }

        @Test
        fun `no-op on missing`() {
            runBlocking {
                repository.delete(FactId("no"))
            }
        }

    }

    @Nested
    @DisplayName("count()")
    inner class Count {
        @Test
        fun `zero initially`() = runBlocking { assertEquals(0, repository.count()) }

        @Test
        fun `counts saved`() = runBlocking {
            repository.save(createFact("a", "a"))
            repository.save(createFact("b", "b"))
            assertEquals(2, repository.count())
        }
    }

    @Nested
    @DisplayName("search() — FTS5")
    inner class Search {
        @Test
        fun `exact word match`() = runBlocking {
            repository.save(createFact("f-1", "Kotlin is modern"))
            repository.save(createFact("f-2", "Java classic"))
            assertEquals(1, repository.search("Kotlin").size)
        }

        @Test
        fun `multiple matches`() = runBlocking {
            repository.save(createFact("f-1", "Kotlin great"))
            repository.save(createFact("f-2", "Learning Kotlin"))
            assertEquals(2, repository.search("Kotlin").size)
        }

        @Test
        fun `no match returns empty`() = runBlocking {
            repository.save(createFact("f-1", "Kotlin"))
            assertTrue(repository.search("xyzunknown").isEmpty())
        }

        @Test
        fun `case insensitive`() = runBlocking {
            repository.save(createFact("f-1", "KOTLIN"))
            assertTrue(repository.search("kotlin").isNotEmpty())
        }
    }

    private fun createFact(id: String, content: String) =
        Fact(FactId(id), content, Instant.now())
}
