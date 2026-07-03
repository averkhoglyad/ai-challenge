package io.averkhogliad.ai.challenge.week4.cli.unit.cli

import io.averkhogliad.ai.challenge.week4.cli.cli.CliState
import io.averkhogliad.ai.challenge.week4.cli.cli.ConsoleCliRenderer
import io.averkhogliad.ai.challenge.week4.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ProfileId
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class ConsoleCliRendererProfileTest : FreeSpec({

    val renderer = ConsoleCliRenderer()

    fun createProfile(
        name: String,
        isActive: Boolean = false,
        description: String = "Test description",
        instructions: String = "Test instructions"
    ): Profile = Profile(
        id = ProfileId(java.util.UUID.randomUUID().toString()),
        name = name,
        description = description,
        instructions = instructions,
        isActive = isActive,
        createdAt = java.time.Instant.now(),
        updatedAt = java.time.Instant.now()
    )

    /**
     * Захватывает вывод в System.out, возвращает его как строку.
     */
    fun captureOutput(block: () -> Unit): String {
        val originalOut = System.out
        val stream = java.io.ByteArrayOutputStream()
        try {
            System.setOut(java.io.PrintStream(stream))
            block()
        } finally {
            System.setOut(originalOut)
        }
        return stream.toString()
    }

    "renderProfileList" - {
        "should handle empty list" {
            // when & then — не падает
            renderer.renderProfileList(emptyList())
        }

        "should render profiles with active marker" {
            // given
            val profiles = listOf(
                createProfile("Profile A", isActive = true),
                createProfile("Profile B", isActive = false)
            )

            // when & then — не падает
            renderer.renderProfileList(profiles)
        }
    }

    "renderProfileDetail" - {
        "should render full profile info" {
            // given
            val profile = createProfile(
                "Test Profile",
                isActive = true,
                description = "Line 1\nLine 2",
                instructions = "Test instructions"
            )

            // when & then — не падает
            renderer.renderProfileDetail(profile)
        }
    }

    "renderProfileDeleted" - {
        "should render deletion message" {
            // when & then — не падает
            renderer.renderProfileDeleted("Deleted Profile")
        }
    }

    "renderProfileUpdated" - {
        "should render update message" {
            // when & then — не падает
            renderer.renderProfileUpdated("Updated Profile")
        }
    }

    "renderProfileError" - {
        "should render error message" {
            // when & then — не падает
            renderer.renderProfileError("Test error message")
        }
    }

    "renderMultilineInputPrompt" - {
        "should render prompt text" {
            // when & then — не падает
            renderer.renderMultilineInputPrompt()
        }
    }

    "all profile rendering methods" - {
        "should be callable multiple times" {
            // given
            val profile = createProfile("Reusable Profile")

            // when & then — не падает при повторных вызовах
            repeat(3) {
                renderer.renderProfileDetail(profile)
                renderer.renderProfileList(listOf(profile))
                renderer.renderProfileDeleted("Test")
                renderer.renderProfileUpdated("Test")
                renderer.renderProfileError("Test error")
                renderer.renderMultilineInputPrompt()
            }
        }
    }

    "renderStatusProfile" - {
        "should render active profile name" {
            // when & then — не падает
            renderer.renderStatusProfile("My Profile")
        }

        "should render null profile as not set" {
            // when & then — не падает
            renderer.renderStatusProfile(null)
        }

        "should be callable multiple times" {
            // when & then — не падает при повторных вызовах
            repeat(3) {
                renderer.renderStatusProfile("Active Profile")
                renderer.renderStatusProfile(null)
            }
        }
    }

    "renderHelp profile commands" - {
        "should include profile commands when task is selected" {
            // given
            val state = CliState(currentTaskId = 1)

            // when
            val output = captureOutput { renderer.renderHelp(state) }

            // then
            output shouldContain "Команды управления профилями:"
            output shouldContain ":profile-new"
            output shouldContain ":profile-list"
            output shouldContain ":profile-use"
            output shouldContain ":profile-edit"
            output shouldContain ":profile-delete"
            output shouldContain ":profile-show"
        }

        "should not include profile commands at top level menu" {
            // given
            val state = CliState(currentTaskId = null)

            // when
            val output = captureOutput { renderer.renderHelp(state) }

            // then
            output.contains("Команды управления профилями:") shouldBe false
        }
    }
})
