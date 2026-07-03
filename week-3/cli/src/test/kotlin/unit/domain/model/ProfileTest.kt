package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.time.Instant

class ProfileTest : FreeSpec({

    "Profile" - {

        val sampleId = ProfileId("test-id-123")
        val now = Instant.now()

        "should create profile with valid data" {
            val profile = Profile(
                id = sampleId,
                name = "Test Profile",
                description = "Test description",
                instructions = "Test instructions",
                createdAt = now,
                updatedAt = now
            )
            profile.id shouldBe sampleId
            profile.name shouldBe "Test Profile"
            profile.description shouldBe "Test description"
            profile.instructions shouldBe "Test instructions"
            profile.isActive shouldBe false
            profile.createdAt shouldBe now
            profile.updatedAt shouldBe now
        }

        "should create profile with isActive true" {
            val profile = Profile(
                id = sampleId,
                name = "Active Profile",
                description = "Desc",
                instructions = "Instr",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            profile.isActive shouldBe true
        }

        "should activate profile" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )
            val activated = profile.activate()
            activated.isActive shouldBe true
            (activated.updatedAt >= now) shouldBe true
        }

        "should deactivate profile" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            val deactivated = profile.deactivate()
            deactivated.isActive shouldBe false
            (deactivated.updatedAt >= now) shouldBe true
        }

        "should update description" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Old description",
                createdAt = now,
                updatedAt = now
            )
            val updated = profile.updateDescription("New description")
            updated.description shouldBe "New description"
            (updated.updatedAt >= now) shouldBe true
        }

        "should update instructions" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                instructions = "Old instructions",
                createdAt = now,
                updatedAt = now
            )
            val updated = profile.updateInstructions("New instructions")
            updated.instructions shouldBe "New instructions"
            (updated.updatedAt >= now) shouldBe true
        }

        "should update name" {
            val profile = Profile(
                id = sampleId,
                name = "Old Name",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )
            val updated = profile.updateName("New Name")
            updated.name shouldBe "New Name"
            (updated.updatedAt >= now) shouldBe true
        }

        "should reject blank name" {
            shouldThrow<IllegalArgumentException> {
                Profile(
                    id = sampleId,
                    name = "",
                    description = "Desc",
                    createdAt = now,
                    updatedAt = now
                )
            }
        }

        "should allow blank description and instructions" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "",
                instructions = "",
                createdAt = now,
                updatedAt = now
            )
            profile.description shouldBe ""
            profile.instructions shouldBe ""
        }

        "should reject blank name in updateName" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )
            shouldThrow<IllegalArgumentException> {
                profile.updateName("")
            }
        }

        "should be immutable - activate returns new instance" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )
            val activated = profile.activate()
            activated shouldNotBeSameInstanceAs profile
            profile.isActive shouldBe false
            activated.isActive shouldBe true
        }

        "should be immutable - deactivate returns new instance" {
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            val deactivated = profile.deactivate()
            deactivated shouldNotBeSameInstanceAs profile
            profile.isActive shouldBe true
            deactivated.isActive shouldBe false
        }
    }
})
