package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.application.executor.Task2Executor
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.domain.TaskId
import io.averkhogliad.ai.challenge.week2.domain.model.DialogSession
import io.averkhogliad.ai.challenge.week2.domain.service.DialogSessionRepository
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.PromptBuilder
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.InMemoryProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.*

class CommandHandlerProfileTest {

    // ═══════════════════════════════════════════
    // ProfileUse — делегирование активации/деактивации
    // ═══════════════════════════════════════════

    @Test
    fun `ProfileUse should delegate to Task2Executor and activate by name`() = runBlocking {
        val repo = InMemoryProfileRepository()
        val profileService = ProfileService(repo)
        val handler = createHandler(profileService)

        // Создаём профиль
        val profile = profileService.handleCreateProfile("Test Profile", "Test content", "")
        val state = CliState()

        val result = handler.handle(Command.ProfileUse("Test Profile"), state)

        // Профиль активирован по имени
        val activated = repo.findByName("Test Profile")
        assertNotNull(activated)
        assertTrue(activated!!.isActive)
        assertEquals(state, result)
    }

    @Test
    fun `ProfileUse with nonexistent name should throw`() = runBlocking {
        val repo = InMemoryProfileRepository()
        val profileService = ProfileService(repo)
        val handler = createHandler(profileService)
        val state = CliState()

        assertFailsWith<io.averkhogliad.ai.challenge.week2.application.ProfileOperationError.NotFoundByName> {
            handler.handle(Command.ProfileUse("nonexistent-name"), state)
        }
    }

    @Test
    fun `ProfileUse with none should deactivate profile`() = runBlocking {
        val repo = InMemoryProfileRepository()
        val profileService = ProfileService(repo)
        val handler = createHandler(profileService)

        profileService.handleCreateProfile("Test Profile", "Test content", "")
        profileService.handleActivateByName("Test Profile")
        assertNotNull(repo.findActive())

        val state = CliState()
        val result = handler.handle(Command.ProfileUse("none"), state)

        assertNull(repo.findActive())
        assertEquals(state, result)
    }

    // ═══════════════════════════════════════════
    // Остальные профильные команды — no-op в handler
    // ═══════════════════════════════════════════

    @Test
    fun `ProfileList is no-op in handler`() = runBlocking {
        val handler = createHandler(ProfileService(InMemoryProfileRepository()))
        val state = CliState()
        val result = handler.handle(Command.ProfileList, state)
        assertEquals(state, result)
    }

    @Test
    fun `ProfileNew is no-op in handler`() = runBlocking {
        val handler = createHandler(ProfileService(InMemoryProfileRepository()))
        val state = CliState()
        val result = handler.handle(Command.ProfileNew("Test"), state)
        assertEquals(state, result)
    }

    @Test
    fun `ProfileEdit is no-op in handler`() = runBlocking {
        val handler = createHandler(ProfileService(InMemoryProfileRepository()))
        val state = CliState()
        val result = handler.handle(Command.ProfileEdit("Test"), state)
        assertEquals(state, result)
    }

    @Test
    fun `ProfileDelete is no-op in handler`() = runBlocking {
        val handler = createHandler(ProfileService(InMemoryProfileRepository()))
        val state = CliState()
        val result = handler.handle(Command.ProfileDelete("Test"), state)
        assertEquals(state, result)
    }

    @Test
    fun `ProfileShow is no-op in handler`() = runBlocking {
        val handler = createHandler(ProfileService(InMemoryProfileRepository()))
        val state = CliState()
        val result = handler.handle(Command.ProfileShow("Test"), state)
        assertEquals(state, result)
    }

    // ═══════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════

    private fun createHandler(profileService: ProfileService): CommandHandler {
        val sessionRepo = InMemoryDialogSessionRepository()
        val memoryService = MemoryService(sessionRepo)
        val promptBuilder = PromptBuilder()
        val dialogService = DialogService(
            llmPort = null,
            memoryService = memoryService,
            promptBuilder = promptBuilder
        )
        val executor = Task2Executor(dialogService, memoryService, profileService)
        return CommandHandler(
            executors = mapOf(TaskId(2) to executor),
            taskManagerExecutor = null,
            memoryService = memoryService,
            taskStepRepository = null,
            factRepository = null,
            dialogService = null
        )
    }

    /** Простейшая in-memory реализация [DialogSessionRepository] для тестов. */
    private class InMemoryDialogSessionRepository : DialogSessionRepository {
        private val sessions = mutableMapOf<String, DialogSession>()

        override fun findById(id: io.averkhogliad.ai.challenge.week2.domain.model.SessionId): DialogSession? {
            return sessions[id.value]
        }

        override fun save(session: DialogSession): DialogSession {
            sessions[session.id.value] = session
            return session
        }

        override fun findByTaskId(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId): DialogSession? {
            return sessions.values.firstOrNull { it.taskId == taskId }
        }

        override fun findActiveSession(): DialogSession? {
            return sessions.values.firstOrNull()
        }

        override fun delete(id: io.averkhogliad.ai.challenge.week2.domain.model.SessionId) {
            sessions.remove(id.value)
        }
    }
}
