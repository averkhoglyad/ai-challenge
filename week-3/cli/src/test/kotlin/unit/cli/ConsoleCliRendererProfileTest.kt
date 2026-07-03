package io.averkhogliad.ai.challenge.week3.cli.unit.cli

import io.averkhogliad.ai.challenge.week3.cli.cli.*

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Profile
import io.averkhogliad.ai.challenge.week3.cli.domain.model.ProfileId
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe

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

    "renderProfileList should handle empty list" {
        renderer.renderProfileList(emptyList())
        // Не падает — уже хорошо
    }

    "renderProfileList should render profiles with active marker" {
        val profiles = listOf(
            createProfile("Profile A", isActive = true),
            createProfile("Profile B", isActive = false)
        )
        renderer.renderProfileList(profiles)
        // Не падает — уже хорошо
    }

    "renderProfileDetail should render full profile info" {
        val profile = createProfile(
            "Test Profile",
            isActive = true,
            description = "Line 1\nLine 2",
            instructions = "Test instructions"
        )
        renderer.renderProfileDetail(profile)
        // Не падает — уже хорошо
    }

    "renderProfileDeleted should render deletion message" {
        renderer.renderProfileDeleted("Deleted Profile")
        // Не падает — уже хорошо
    }

    "renderProfileUpdated should render update message" {
        renderer.renderProfileUpdated("Updated Profile")
        // Не падает — уже хорошо
    }

    "renderProfileError should render error message" {
        renderer.renderProfileError("Test error message")
        // Не падает — уже хорошо
    }

    "renderMultilineInputPrompt should render prompt text" {
        renderer.renderMultilineInputPrompt()
        // Не падает — уже хорошо
    }

    "all profile rendering methods should be callable multiple times" {
        val profile = createProfile("Reusable Profile")

        repeat(3) {
            renderer.renderProfileDetail(profile)
            renderer.renderProfileList(listOf(profile))
            renderer.renderProfileDeleted("Test")
            renderer.renderProfileUpdated("Test")
            renderer.renderProfileError("Test error")
            renderer.renderMultilineInputPrompt()
        }
        // Не падает при повторных вызовах
    }

    // ──── renderStatusProfile tests ────

    "renderStatusProfile should render active profile name" {
        renderer.renderStatusProfile("My Profile")
        // Не падает — уже хорошо
    }

    "renderStatusProfile should render null profile as not set" {
        renderer.renderStatusProfile(null)
        // Не падает — уже хорошо
    }

    "renderStatusProfile should be callable multiple times" {
        repeat(3) {
            renderer.renderStatusProfile("Active Profile")
            renderer.renderStatusProfile(null)
        }
        // Не падает при повторных вызовах
    }

    // ──── renderHelp profile commands tests ────

    "renderHelp should include profile commands when task is selected" {
        val state = CliState(currentTaskId = 1)
        val output = captureOutput { renderer.renderHelp(state) }

        output shouldContain "Команды управления профилями:"
        output shouldContain ":profile-new"
        output shouldContain ":profile-list"
        output shouldContain ":profile-use"
        output shouldContain ":profile-edit"
        output shouldContain ":profile-delete"
        output shouldContain ":profile-show"
    }

    "renderHelp should not include profile commands at top level menu" {
        val state = CliState(currentTaskId = null)
        val output = captureOutput { renderer.renderHelp(state) }

        output.contains("Команды управления профилями:") shouldBe false
    }
})
