package io.averkhogliad.ai.challenge.week2.unit.application

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import java.time.Instant

class DialogServiceTest : FreeSpec({

    lateinit var mockLlmPort: LlmPort
    lateinit var sessionRepository: InMemoryDialogSessionRepository
    lateinit var memoryService: MemoryService
    lateinit var promptBuilder: PromptBuilder
    lateinit var dialogService: DialogService
    lateinit var stubProfileRepository: StubProfileRepository
    lateinit var stubInvariantRepository: StubInvariantRepository
    lateinit var invariantService: InvariantService
    lateinit var mockTaskRepository: TaskRepository
    lateinit var mockTaskStepRepository: TaskStepRepository
    lateinit var mockFactRepository: FactRepository

    beforeEach {
        mockLlmPort = mockk(relaxed = true)
        sessionRepository = InMemoryDialogSessionRepository()
        mockTaskRepository = mockk(relaxed = true)
        mockTaskStepRepository = mockk(relaxed = true)
        coEvery { mockTaskRepository.findAll() } returns emptyList()
        coEvery { mockTaskRepository.findById(any()) } returns null
        coEvery { mockTaskStepRepository.findByTaskId(any()) } returns emptyList()
        mockFactRepository = mockk()
        coEvery { mockFactRepository.search(any()) } returns emptyList()
        coEvery { mockFactRepository.count() } returns 0
        memoryService = MemoryService(sessionRepository, mockTaskRepository, mockTaskStepRepository, mockFactRepository)
        promptBuilder = PromptBuilder()
        stubProfileRepository = StubProfileRepository()
        stubInvariantRepository = StubInvariantRepository()
        invariantService = InvariantService(stubInvariantRepository)

        dialogService = DialogService(
            llmPort = mockLlmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            taskExecutionConfig = io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig(),
            profileRepository = stubProfileRepository,
            invariantService = invariantService
        )
    }

    "chat" - {
        "should return successful result from LLM" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Reply")

                val result = dialogService.chat("Hi", SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Success>()
                result.content shouldBe "Reply"
            }
        }

        "should save user message to STM" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Ok")

                dialogService.chat("Question", SessionLevel.TASK_LIST)

                val msgs = memoryService.getRecentMessages(SessionLevel.TASK_LIST)
                msgs.any { it.content == "Question" && it.role.name == "USER" } shouldBe true
            }
        }

        "should save assistant response to STM" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Answer")

                dialogService.chat("Q", SessionLevel.TASK_LIST)

                val msgs = memoryService.getRecentMessages(SessionLevel.TASK_LIST)
                msgs.any { it.content == "Answer" && it.role.name == "ASSISTANT" } shouldBe true
            }
        }

        "should pass system message to LLM" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Ok")

                val result = dialogService.chat("Q", SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Success>()
            }
        }

        "should handle LLM errors" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Error(
                    "LLM down",
                    RuntimeException("fail")
                )

                val result = dialogService.chat("Q", SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Error>()
                result.message shouldContain "LLM down"
            }
        }

        "should support TASK_DETAIL level" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Task reply")

                val taskId = TaskId("t1")
                val result = dialogService.chat("What?", SessionLevel.TASK_DETAIL, taskId)

                result.shouldBeInstanceOf<TaskResult.Success>()
                result.content shouldBe "Task reply"
            }
        }
    }

    "planSteps" - {
        "should return plan" {
            runTest {
                coEvery { mockLlmPort.chat(any(), any()) } returns TaskResult.Success("1. Step one\n2. Step two")

                val result = dialogService.planSteps("Task", null, SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Success>()
                result.content shouldContain "Step one"
            }
        }

        "should build prompt with title" {
            runTest {
                coEvery { mockLlmPort.chat(any(), any()) } returns TaskResult.Success("1. A\n2. B")

                dialogService.planSteps("API", "Desc", SessionLevel.TASK_LIST)
            }
        }

        "should handle plan errors" {
            runTest {
                coEvery { mockLlmPort.chat(any(), any()) } returns TaskResult.Error("Plan fail", RuntimeException("x"))

                val result = dialogService.planSteps("T", null, SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Error>()
                result.message shouldContain "Plan fail"
            }
        }
    }

    "Profile integration" - {
        val now = Instant.now()

        "should pass active profile to prompt" {
            runTest {
                val activeProfile = Profile(
                    id = ProfileId("active-1"),
                    name = "ActiveProfile",
                    description = "Пиратский стиль общения",
                    instructions = "Отвечай как пират",
                    isActive = true,
                    createdAt = now,
                    updatedAt = now
                )
                stubProfileRepository.save(activeProfile)
                stubProfileRepository.setActive(activeProfile)
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Ok")

                val result = dialogService.chat("Ахой!", SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Success>()
            }
        }

        "should work correctly with no active profile" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Ok")

                val result = dialogService.chat("Обычный запрос", SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Success>()
            }
        }
    }

    "Backward compatibility" - {
        "should work with stub repositories" {
            runTest {
                coEvery { mockLlmPort.chatWithMessages(any(), any()) } returns TaskResult.Success("Ok")

                val result = dialogService.chat("Test", SessionLevel.TASK_LIST)

                result.shouldBeInstanceOf<TaskResult.Success>()
            }
        }
    }
})

private class InMemoryDialogSessionRepository : DialogSessionRepository {
    private val sessions = mutableMapOf<String, DialogSession>()
    override fun findById(id: SessionId): DialogSession? = sessions[id.value]
    override fun save(session: DialogSession): DialogSession {
        sessions[session.id.value] = session; return session
    }

    override fun findByTaskId(taskId: TaskId): DialogSession? =
        sessions.values.firstOrNull { it.taskId == taskId }

    override fun findActiveSession(): DialogSession? = sessions.values.firstOrNull()
    override fun delete(id: SessionId) {
        sessions.remove(id.value)
    }
}

private class StubInvariantRepository : InvariantRepository {
    private val invariants = mutableMapOf<Int, Invariant>()
    private var nextId = 1

    override suspend fun save(invariant: Invariant): Invariant {
        val id = InvariantId(nextId++)
        val saved = invariant.copy(id = id)
        invariants[id.value] = saved
        return saved
    }

    override suspend fun findById(id: InvariantId): Invariant? = invariants[id.value]
    override suspend fun findAll(): List<Invariant> = invariants.values.toList()
    override suspend fun delete(id: InvariantId): Boolean = invariants.remove(id.value) != null
    override suspend fun count(): Int = invariants.size
}

private class StubProfileRepository : ProfileRepository {
    private val profiles = mutableMapOf<String, Profile>()
    private var activeProfile: Profile? = null

    fun setActive(profile: Profile) {
        activeProfile = profile
    }

    override suspend fun save(profile: Profile): Profile {
        profiles[profile.id.value] = profile
        return profile
    }

    override suspend fun findById(id: ProfileId): Profile? = profiles[id.value]
    override suspend fun findActive(): Profile? = activeProfile
    override suspend fun findByName(name: String): Profile? =
        profiles.values.firstOrNull { it.name == name }

    override suspend fun findAll(): List<Profile> = profiles.values.toList()
    override suspend fun delete(id: ProfileId) {
        profiles.remove(id.value)
    }

    override suspend fun existsByName(name: String): Boolean = profiles.values.any { it.name == name }
    override suspend fun clearActive() {
        activeProfile = null
    }
}
