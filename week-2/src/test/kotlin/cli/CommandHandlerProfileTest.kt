package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.application.executor.Task2Executor
import io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week2.cli.commands.Command
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.*
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
        val profileRepository = InMemoryProfileRepository()
        val invariantService = InvariantService(InMemoryInvariantRepository())
        val dialogService = DialogService(
            llmPort = null,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            profileRepository = profileRepository,
            invariantService = invariantService
        )
        val executor = Task2Executor(dialogService, memoryService, profileService)
        val taskRepo = InMemoryTaskRepository()
        val todoTaskService = TodoTaskService(taskRepo)
        val taskStepRepository = InMemoryTaskStepRepository()
        val factRepository = InMemoryFactRepository()
        return CommandHandler(
            executors = mapOf(TaskId("2") to executor),
            todoTaskService = todoTaskService,
            memoryService = memoryService,
            taskStepRepository = taskStepRepository,
            factRepository = factRepository
        )
    }

    /** In-memory реализация TaskRepository для тестов. */
    private class InMemoryTaskRepository : TaskRepository {
        private val tasks = mutableMapOf<String, Task>()

        override suspend fun save(task: Task) {
            tasks[task.id.value] = task
        }

        override suspend fun findById(id: TaskId): Task? = tasks[id.value]
        override suspend fun findAll(): List<Task> = tasks.values.toList()
        override suspend fun delete(id: TaskId) {
            tasks.remove(id.value)
        }

        override suspend fun exists(id: TaskId): Boolean = tasks.containsKey(id.value)
        override suspend fun saveSteps(taskId: TaskId, steps: List<TaskStep>) {}
        override suspend fun findStepsByTaskId(taskId: TaskId): List<TaskStep> = emptyList()
    }

    /** In-memory реализация FactRepository для тестов. */
    private class InMemoryFactRepository : FactRepository {
        private val facts = mutableMapOf<FactId, Fact>()

        override suspend fun save(fact: Fact): Fact {
            facts[fact.id] = fact; return fact
        }

        override suspend fun findById(id: FactId): Fact? = facts[id]
        override suspend fun findAll(): List<Fact> = facts.values.toList()
        override suspend fun search(query: String): List<Fact> = emptyList()
        override suspend fun searchBatch(queries: List<String>): List<Fact> = emptyList()
        override suspend fun delete(id: FactId): Boolean = facts.remove(id) != null
        override suspend fun count(): Int = facts.size
    }

    /** In-memory реализация TaskStepRepository для тестов. */
    private class InMemoryTaskStepRepository : TaskStepRepository {
        private val steps = mutableMapOf<TaskStepId, TaskStep>()

        override fun save(step: TaskStep): TaskStep {
            steps[step.id] = step; return step
        }

        override fun findByTaskId(taskId: TaskId): List<TaskStep> =
            steps.values.filter { it.taskId == taskId }.sortedBy { it.order }

        override fun findById(stepId: TaskStepId): TaskStep? = steps[stepId]
        override fun delete(stepId: TaskStepId): Boolean = steps.remove(stepId) != null
        override fun deleteByTaskId(taskId: TaskId): Int {
            val toRemove = steps.values.filter { it.taskId == taskId }
            toRemove.forEach { steps.remove(it.id) }
            return toRemove.size
        }

        override fun countByTaskId(taskId: TaskId): Int =
            steps.values.count { it.taskId == taskId }
    }

    /** In-memory реализация InvariantRepository для тестов. */
    private class InMemoryInvariantRepository : InvariantRepository {
        private val invariants = mutableMapOf<Int, Invariant>()

        override suspend fun save(invariant: Invariant): Invariant {
            val id = invariant.id.value.toInt()
            invariants[id] = invariant
            return invariant
        }

        override suspend fun findById(id: InvariantId): Invariant? =
            invariants[id.value.toInt()]

        override suspend fun findAll(): List<Invariant> =
            invariants.values.toList()

        override suspend fun delete(id: InvariantId): Boolean =
            invariants.remove(id.value.toInt()) != null

        override suspend fun count(): Int = invariants.size

        override fun close() {}
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
