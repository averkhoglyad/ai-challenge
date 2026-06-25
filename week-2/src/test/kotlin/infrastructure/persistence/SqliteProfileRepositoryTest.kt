package io.averkhogliad.ai.challenge.week2.infrastructure.persistence

import io.averkhogliad.ai.challenge.week2.domain.model.Profile
import io.averkhogliad.ai.challenge.week2.domain.model.ProfileId
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.*

@DisplayName("SqliteProfileRepository")
class SqliteProfileRepositoryTest {

    private lateinit var tempDbFile: File
    private lateinit var database: SqliteDatabase
    private lateinit var repository: SqliteProfileRepository

    @BeforeEach
    fun setUp() {
        tempDbFile = Files.createTempFile("test-profile-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteProfileRepository(database)
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
        fun `save and find profile by id`() = runBlocking {
            val p = createProfile("p-1", "Test", "Desc", "Instr")
            repository.save(p)
            val found = repository.findById(p.id)
            assertNotNull(found)
            assertEquals("Test", found.name)
            assertEquals("Desc", found.description)
            assertEquals("Instr", found.instructions)
        }

        @Test
        fun `return null for missing id`() = runBlocking {
            assertNull(repository.findById(ProfileId("nope")))
        }

        @Test
        fun `upsert on re-save`() = runBlocking {
            val p = createProfile("p-1", "Old", "Old desc", "Old instr")
            repository.save(p)
            repository.save(p.copy(name = "New", description = "New desc", instructions = "New instr"))
            val updated = repository.findById(p.id)
            assertNotNull(updated)
            assertEquals("New", updated.name)
            assertEquals("New desc", updated.description)
            assertEquals("New instr", updated.instructions)
        }
    }

    @Nested
    @DisplayName("findByName()")
    inner class FindByName {
        @Test
        fun `find profile by name`() = runBlocking {
            val p = createProfile("p-1", "Unique", "Desc", "Instr")
            repository.save(p)
            val found = repository.findByName("Unique")
            assertNotNull(found)
            assertEquals(p.id, found.id)
        }

        @Test
        fun `return null when name not found`() = runBlocking {
            assertNull(repository.findByName("Nonexistent"))
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
        fun `returns all saved sorted by created_at`() = runBlocking {
            repository.save(createProfile("a", "A", "Desc A", "Instr A"))
            repository.save(createProfile("b", "B", "Desc B", "Instr B"))
            val all = repository.findAll()
            assertEquals(2, all.size)
            assertEquals("A", all[0].name)
            assertEquals("B", all[1].name)
        }
    }

    @Nested
    @DisplayName("findActive()")
    inner class FindActive {
        @Test
        fun `return null when no active`() = runBlocking {
            assertNull(repository.findActive())
        }

        @Test
        fun `find active profile`() = runBlocking {
            val active = createProfile("p-1", "Active", "Desc", "Instr", isActive = true)
            val inactive = createProfile("p-2", "Inactive", "Desc", "Instr", isActive = false)
            repository.save(active)
            repository.save(inactive)
            val found = repository.findActive()
            assertNotNull(found)
            assertEquals("Active", found.name)
            assertTrue(found.isActive)
        }

        @Test
        fun `return only first active`() = runBlocking {
            repository.save(createProfile("p-1", "First", "Desc", "Instr", isActive = true))
            repository.save(createProfile("p-2", "Second", "Desc", "Instr", isActive = true))
            val found = repository.findActive()
            assertNotNull(found)
            assertEquals("First", found.name)
        }
    }

    @Nested
    @DisplayName("delete()")
    inner class Delete {
        @Test
        fun `deletes profile`() = runBlocking {
            val p = createProfile("p-1", "ToDelete", "Desc", "Instr")
            repository.save(p)
            repository.delete(p.id)
            assertNull(repository.findById(p.id))
        }

        @Test
        fun `no-op on missing id`() = runBlocking {
            repository.delete(ProfileId("nonexistent"))
        }
    }

    @Nested
    @DisplayName("existsByName()")
    inner class ExistsByName {
        @Test
        fun `returns true when exists`() = runBlocking {
            repository.save(createProfile("p-1", "Exists", "Desc", "Instr"))
            assertTrue(repository.existsByName("Exists"))
        }

        @Test
        fun `returns false when not exists`() = runBlocking {
            assertFalse(repository.existsByName("Nonexistent"))
        }
    }

    @Nested
    @DisplayName("clearActive()")
    inner class ClearActive {
        @Test
        fun `deactivates active profile`() = runBlocking {
            repository.save(createProfile("p-1", "WasActive", "Desc", "Instr", isActive = true))
            repository.clearActive()
            assertNull(repository.findActive())
        }

        @Test
        fun `does nothing when no active profile`() = runBlocking {
            repository.clearActive()
            assertNull(repository.findActive())
        }
    }

    private fun createProfile(
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
}
