package io.averkhogliad.ai.challenge.week2.domain.model

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class ProfileTest {

    private val sampleId = ProfileId("test-id-123")
    private val now = Instant.now()

    @Test
    fun `should create profile with valid data`() {
        val profile = Profile(
            id = sampleId,
            name = "Test Profile",
            description = "Test description",
            instructions = "Test instructions",
            createdAt = now,
            updatedAt = now
        )
        assertEquals(sampleId, profile.id)
        assertEquals("Test Profile", profile.name)
        assertEquals("Test description", profile.description)
        assertEquals("Test instructions", profile.instructions)
        assertFalse(profile.isActive)
        assertEquals(now, profile.createdAt)
        assertEquals(now, profile.updatedAt)
    }

    @Test
    fun `should create profile with isActive true`() {
        val profile = Profile(
            id = sampleId,
            name = "Active Profile",
            description = "Desc",
            instructions = "Instr",
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        assertTrue(profile.isActive)
    }

    @Test
    fun `should activate profile`() {
        val profile = Profile(
            id = sampleId,
            name = "Test",
            description = "Desc",
            createdAt = now,
            updatedAt = now
        )
        val activated = profile.activate()
        assertTrue(activated.isActive)
        assertTrue(activated.updatedAt >= now)
    }

    @Test
    fun `should deactivate profile`() {
        val profile = Profile(
            id = sampleId,
            name = "Test",
            description = "Desc",
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val deactivated = profile.deactivate()
        assertFalse(deactivated.isActive)
        assertTrue(deactivated.updatedAt >= now)
    }

    @Test
    fun `should update description`() {
        val profile = Profile(
            id = sampleId,
            name = "Test",
            description = "Old description",
            createdAt = now,
            updatedAt = now
        )
        val updated = profile.updateDescription("New description")
        assertEquals("New description", updated.description)
        assertTrue(updated.updatedAt >= now)
    }

    @Test
    fun `should update instructions`() {
        val profile = Profile(
            id = sampleId,
            name = "Test",
            instructions = "Old instructions",
            createdAt = now,
            updatedAt = now
        )
        val updated = profile.updateInstructions("New instructions")
        assertEquals("New instructions", updated.instructions)
        assertTrue(updated.updatedAt >= now)
    }

    @Test
    fun `should update name`() {
        val profile = Profile(
            id = sampleId,
            name = "Old Name",
            description = "Desc",
            createdAt = now,
            updatedAt = now
        )
        val updated = profile.updateName("New Name")
        assertEquals("New Name", updated.name)
        assertTrue(updated.updatedAt >= now)
    }

    @Test
    fun `should reject blank name`() {
        assertThrows<IllegalArgumentException> {
            Profile(
                id = sampleId,
                name = "",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )
        }
    }

    @Test
    fun `should allow blank description and instructions`() {
        // description и instructions могут быть пустыми на уровне модели
        val profile = Profile(
            id = sampleId,
            name = "Test",
            description = "",
            instructions = "",
            createdAt = now,
            updatedAt = now
        )
        assertEquals("", profile.description)
        assertEquals("", profile.instructions)
    }

    @Test
    fun `should reject blank name in updateName`() {
        val profile = Profile(
            id = sampleId,
            name = "Test",
            description = "Desc",
            createdAt = now,
            updatedAt = now
        )
        assertThrows<IllegalArgumentException> {
            profile.updateName("")
        }
    }

    @Test
    fun `should be immutable - activate returns new instance`() {
        val profile = Profile(
            id = sampleId,
            name = "Test",
            description = "Desc",
            createdAt = now,
            updatedAt = now
        )
        val activated = profile.activate()
        assertNotSame(profile, activated)
        assertFalse(profile.isActive)
        assertTrue(activated.isActive)
    }

    @Test
    fun `should be immutable - deactivate returns new instance`() {
        val profile = Profile(
            id = sampleId,
            name = "Test",
            description = "Desc",
            isActive = true,
            createdAt = now,
            updatedAt = now
        )
        val deactivated = profile.deactivate()
        assertNotSame(profile, deactivated)
        assertTrue(profile.isActive)
        assertFalse(deactivated.isActive)
    }
}
