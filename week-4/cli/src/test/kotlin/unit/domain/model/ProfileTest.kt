package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ProfileId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import java.time.Instant

class ProfileTest : FreeSpec({

    val sampleId = ProfileId("test-id-123")
    val now = Instant.now()

    "creation" - {

        "should create profile with valid data" {
            // when
            val profile = Profile(
                id = sampleId,
                name = "Test Profile",
                description = "Test description",
                instructions = "Test instructions",
                createdAt = now,
                updatedAt = now
            )

            // then
            profile.id shouldBe sampleId
            profile.name shouldBe "Test Profile"
            profile.description shouldBe "Test description"
            profile.instructions shouldBe "Test instructions"
            profile.isActive shouldBe false
            profile.createdAt shouldBe now
            profile.updatedAt shouldBe now
        }

        "should create profile with isActive true" {
            // when
            val profile = Profile(
                id = sampleId,
                name = "Active Profile",
                description = "Desc",
                instructions = "Instr",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            // then
            profile.isActive shouldBe true
        }

        "should reject blank name" {
            // when & then
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
            // description и instructions могут быть пустыми на уровне модели
            // when
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "",
                instructions = "",
                createdAt = now,
                updatedAt = now
            )

            // then
            profile.description shouldBe ""
            profile.instructions shouldBe ""
        }
    }

    "activate / deactivate" - {

        "should activate profile" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )

            // when
            val activated = profile.activate()

            // then
            activated.isActive shouldBe true
            (activated.updatedAt >= now) shouldBe true
        }

        "should deactivate profile" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            // when
            val deactivated = profile.deactivate()

            // then
            deactivated.isActive shouldBe false
            (deactivated.updatedAt >= now) shouldBe true
        }
    }

    "update methods" - {

        "should update description" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Old description",
                createdAt = now,
                updatedAt = now
            )

            // when
            val updated = profile.updateDescription("New description")

            // then
            updated.description shouldBe "New description"
            (updated.updatedAt >= now) shouldBe true
        }

        "should update instructions" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Test",
                instructions = "Old instructions",
                createdAt = now,
                updatedAt = now
            )

            // when
            val updated = profile.updateInstructions("New instructions")

            // then
            updated.instructions shouldBe "New instructions"
            (updated.updatedAt >= now) shouldBe true
        }

        "should update name" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Old Name",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )

            // when
            val updated = profile.updateName("New Name")

            // then
            updated.name shouldBe "New Name"
            (updated.updatedAt >= now) shouldBe true
        }

        "should reject blank name in updateName" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )

            // when & then
            shouldThrow<IllegalArgumentException> {
                profile.updateName("")
            }
        }
    }

    "immutability" - {

        "should be immutable - activate returns new instance" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                createdAt = now,
                updatedAt = now
            )

            // when
            val activated = profile.activate()

            // then
            activated shouldNotBeSameInstanceAs profile
            profile.isActive shouldBe false
            activated.isActive shouldBe true
        }

        "should be immutable - deactivate returns new instance" {
            // given
            val profile = Profile(
                id = sampleId,
                name = "Test",
                description = "Desc",
                isActive = true,
                createdAt = now,
                updatedAt = now
            )

            // when
            val deactivated = profile.deactivate()

            // then
            deactivated shouldNotBeSameInstanceAs profile
            profile.isActive shouldBe true
            deactivated.isActive shouldBe false
        }
    }
})
