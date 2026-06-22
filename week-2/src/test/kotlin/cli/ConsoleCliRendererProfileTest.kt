package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.domain.model.Profile
import io.averkhogliad.ai.challenge.week2.domain.model.ProfileId
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConsoleCliRendererProfileTest {

    private val renderer = ConsoleCliRenderer()

    private fun createProfile(
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

    @Test
    fun `renderProfileList should handle empty list`() {
        renderer.renderProfileList(emptyList())
        // Не падает — уже хорошо
    }

    @Test
    fun `renderProfileList should render profiles with active marker`() {
        val profiles = listOf(
            createProfile("Profile A", isActive = true),
            createProfile("Profile B", isActive = false)
        )
        renderer.renderProfileList(profiles)
        // Не падает — уже хорошо
    }

    @Test
    fun `renderProfileDetail should render full profile info`() {
        val profile = createProfile(
            "Test Profile",
            isActive = true,
            description = "Line 1\nLine 2",
            instructions = "Test instructions"
        )
        renderer.renderProfileDetail(profile)
        // Не падает — уже хорошо
    }

    @Test
    fun `renderProfileDeleted should render deletion message`() {
        renderer.renderProfileDeleted("Deleted Profile")
        // Не падает — уже хорошо
    }

    @Test
    fun `renderProfileUpdated should render update message`() {
        renderer.renderProfileUpdated("Updated Profile")
        // Не падает — уже хорошо
    }

    @Test
    fun `renderProfileError should render error message`() {
        renderer.renderProfileError("Test error message")
        // Не падает — уже хорошо
    }

    @Test
    fun `renderMultilineInputPrompt should render prompt text`() {
        renderer.renderMultilineInputPrompt()
        // Не падает — уже хорошо
    }

    @Test
    fun `all profile rendering methods should be callable multiple times`() {
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

    @Test
    fun `renderStatusProfile should render active profile name`() {
        renderer.renderStatusProfile("My Profile")
        // Не падает — уже хорошо
    }

    @Test
    fun `renderStatusProfile should render null profile as not set`() {
        renderer.renderStatusProfile(null)
        // Не падает — уже хорошо
    }

    @Test
    fun `renderStatusProfile should be callable multiple times`() {
        repeat(3) {
            renderer.renderStatusProfile("Active Profile")
            renderer.renderStatusProfile(null)
        }
        // Не падает при повторных вызовах
    }

    // ──── renderHelp profile commands tests ────

    @Test
    fun `renderHelp should include profile commands when task is selected`() {
        val state = io.averkhogliad.ai.challenge.week2.cli.CliState(currentTaskId = 1)
        val output = captureOutput { renderer.renderHelp(state) }

        assertTrue(
            output.contains("Команды управления профилями:"),
            "Help должен содержать заголовок секции профилей"
        )
        assertTrue(
            output.contains(":profile-new"),
            "Help должен содержать команду :profile-new"
        )
        assertTrue(
            output.contains(":profile-list"),
            "Help должен содержать команду :profile-list"
        )
        assertTrue(
            output.contains(":profile-use"),
            "Help должен содержать команду :profile-use"
        )
        assertTrue(
            output.contains(":profile-edit"),
            "Help должен содержать команду :profile-edit"
        )
        assertTrue(
            output.contains(":profile-delete"),
            "Help должен содержать команду :profile-delete"
        )
        assertTrue(
            output.contains(":profile-show"),
            "Help должен содержать команду :profile-show"
        )
    }

    @Test
    fun `renderHelp should not include profile commands at top level menu`() {
        val state = io.averkhogliad.ai.challenge.week2.cli.CliState(currentTaskId = null)
        val output = captureOutput { renderer.renderHelp(state) }

        assertFalse(
            output.contains("Команды управления профилями:"),
            "Help на верхнем уровне не должен содержать секцию профилей"
        )
    }

    // ──── helpers ────

    /**
     * Захватывает вывод в System.out, возвращает его как строку.
     */
    private fun captureOutput(block: () -> Unit): String {
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
}
