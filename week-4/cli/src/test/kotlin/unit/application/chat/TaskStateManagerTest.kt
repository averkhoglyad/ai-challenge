package io.averkhogliad.ai.challenge.week4.cli.unit.application.chat

import io.averkhogliad.ai.challenge.week4.cli.application.chat.TaskStateManager
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.ChatSession
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.util.*

/**
 * Тесты для [TaskStateManager] — ручное управление памятью задачи.
 *
 * Используем Fake-реализацию репозитория для unit-тестов.
 */
class TaskStateManagerTest : FreeSpec({

    /**
     * In-memory fake реализация [ChatSessionRepository].
     */
    class FakeChatSessionRepository : ChatSessionRepository {
        private val sessions = mutableMapOf<UUID, ChatSession>()

        override suspend fun save(session: ChatSession): Result<ChatSession> {
            sessions[session.metadata.id] = session
            return Result.success(session)
        }

        override suspend fun loadActive(): Result<ChatSession?> =
            Result.success(sessions.values.find { it.metadata.active && !it.metadata.archived })

        override suspend fun loadById(id: UUID): Result<ChatSession?> =
            Result.success(sessions[id])

        override suspend fun listSessions(): Result<List<ChatSession>> =
            Result.success(sessions.values.toList())

        override suspend fun setActive(id: UUID): Result<Unit> {
            sessions.values.forEach { s ->
                if (s.metadata.id in sessions) {
                    sessions[s.metadata.id] = s.copy(metadata = s.metadata.copy(active = s.metadata.id == id))
                }
            }
            return Result.success(Unit)
        }

        override suspend fun archiveSession(id: UUID): Result<Unit> {
            val s = sessions[id] ?: return Result.failure(NoSuchElementException("Not found"))
            sessions[id] = s.copy(metadata = s.metadata.copy(archived = true, active = false))
            return Result.success(Unit)
        }

        override suspend fun deleteSession(id: UUID): Result<Unit> {
            sessions.remove(id)
            return Result.success(Unit)
        }
    }

    fun createManager(repository: ChatSessionRepository = FakeChatSessionRepository()) =
        TaskStateManager(repository = repository, config = ChatConfig())

    "Установка цели (setGoal)" - {

        "should set goal on task state" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)

                // when
                manager.setGoal(session.metadata.id, "Build a REST API")

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.goal shouldBe "Build a REST API"
            }
        }

        "should replace existing goal" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)
                manager.setGoal(session.metadata.id, "First goal")

                // when
                manager.setGoal(session.metadata.id, "Second goal")

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.goal shouldBe "Second goal"
            }
        }
    }

    "Добавление термина (addTerm)" - {

        "should add term to task state" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)

                // when
                manager.addTerm(session.metadata.id, "API", "Application Programming Interface")

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.definedTerms?.size shouldBe 1
                updated?.taskState?.definedTerms?.get(0) shouldBe ("API" to "Application Programming Interface")
            }
        }
    }

    "Удаление термина (removeTerm)" - {

        "should remove term by name" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)
                manager.addTerm(session.metadata.id, "API", "Definition")
                manager.addTerm(session.metadata.id, "REST", "Definition 2")

                // when
                manager.removeTerm(session.metadata.id, "API")

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.definedTerms?.size shouldBe 1
                updated?.taskState?.definedTerms?.get(0)?.first shouldBe "REST"
            }
        }

        "should have no effect when term not found" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)
                manager.addTerm(session.metadata.id, "API", "Definition")

                // when
                manager.removeTerm(session.metadata.id, "NonExistent")

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.definedTerms?.size shouldBe 1
            }
        }
    }

    "Добавление ограничения (addConstraint)" - {

        "should add constraint" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)

                // when
                manager.addConstraint(session.metadata.id, "Must use Kotlin")

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.constraints?.size shouldBe 1
                updated?.taskState?.constraints?.get(0) shouldBe "Must use Kotlin"
            }
        }
    }

    "Удаление ограничения (removeConstraint)" - {

        "should remove constraint by index" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)
                manager.addConstraint(session.metadata.id, "C1")
                manager.addConstraint(session.metadata.id, "C2")

                // when
                manager.removeConstraint(session.metadata.id, 0)

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.constraints?.size shouldBe 1
                updated?.taskState?.constraints?.get(0) shouldBe "C2"
            }
        }
    }

    "Сброс памяти (resetTaskState)" - {

        "should reset all task state" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)
                manager.setGoal(session.metadata.id, "Goal")
                manager.addTerm(session.metadata.id, "T1", "D1")
                manager.addConstraint(session.metadata.id, "C1")

                // when
                manager.resetTaskState(session.metadata.id)

                // then
                val updated = fakeRepo.loadById(session.metadata.id).getOrNull()
                updated?.taskState?.goal shouldBe null
                updated?.taskState?.definedTerms?.isEmpty() shouldBe true
                updated?.taskState?.constraints?.isEmpty() shouldBe true
            }
        }
    }

    "getTaskState" - {

        "should return current task state" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)
                manager.setGoal(session.metadata.id, "Test Goal")

                // when
                val state = manager.getTaskState(session.metadata.id)

                // then
                state.goal shouldBe "Test Goal"
            }
        }

        "should return EMPTY for new session" {
            runTest {
                // given
                val fakeRepo = FakeChatSessionRepository()
                val session = ChatSession.create()
                fakeRepo.save(session)
                val manager = createManager(fakeRepo)

                // when
                val state = manager.getTaskState(session.metadata.id)

                // then
                state.goal shouldBe null
                state.definedTerms.isEmpty() shouldBe true
                state.constraints.isEmpty() shouldBe true
            }
        }
    }
})
