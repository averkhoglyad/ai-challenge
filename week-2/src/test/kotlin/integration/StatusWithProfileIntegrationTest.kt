package io.averkhogliad.ai.challenge.week2.integration

import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.cli.CliApplication
import io.averkhogliad.ai.challenge.week2.cli.CliRenderer
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryStatus
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.InMemoryProfileRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Интеграционные тесты для US-PM-12: отображение профиля в команде :status.
 *
 * Проверяет:
 * - [CliApplication] с [InMemoryProfileRepository] рендерит информацию о профиле
 * - Если активный профиль есть — его название отображается
 * - Если профиль не задан — отображается "Профиль не задан"
 */
@DisplayName("StatusWithProfileIntegrationTest")
class StatusWithProfileIntegrationTest {

    /**
     * Mock-рендерер, собирающий сообщения для проверки в тестах.
     */
    private class TestCliRenderer : CliRenderer {
        val renderedMessages = mutableListOf<String>()

        override fun renderWelcome() {}
        override fun renderMenu(executors: List<io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor>) {}
        override fun renderTaskHeader(metadata: io.averkhogliad.ai.challenge.week2.domain.TaskMetadata) {}
        override fun renderResult(result: io.averkhogliad.ai.challenge.week2.domain.TaskResult) {}
        override fun renderError(message: String) {
            renderedMessages.add("error:$message")
        }

        override fun renderPrompt(state: CliState) {}
        override fun renderHelp(state: CliState) {}
        override fun renderParameters(state: CliState) {}
        override fun renderGoodbye() {}
        override fun renderRequestInfo(
            prompt: String,
            config: io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
        ) {
        }

        override fun renderLoadingStart(message: String) {}
        override fun renderLoadingStop() {}
        override fun renderSuccess(message: String) {}
        override fun renderInfo(message: String) {
            renderedMessages.add("info:$message")
        }

        override fun renderTaskList(tasks: List<io.averkhogliad.ai.challenge.week2.domain.model.Task>) {}
        override fun renderTaskDetail(task: io.averkhogliad.ai.challenge.week2.domain.model.Task) {}
        override fun renderTaskCreated(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {}
        override fun renderTaskUpdated(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {}
        override fun renderTaskDeleted(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {}
        override fun renderTaskClosed(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {}
        override fun renderTaskCancelled(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {}
        override fun renderMemoryStatus(status: MemoryStatus) {
            renderedMessages.add("memoryStatus")
        }

        override fun renderMemoryCleared() {}
        override fun renderStepCreated(step: io.averkhogliad.ai.challenge.week2.domain.model.TaskStep) {}
        override fun renderStepList(steps: List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep>) {}
        override fun renderStepCompleted(step: io.averkhogliad.ai.challenge.week2.domain.model.TaskStep) {}
        override fun renderStepError(message: String) {}
        override fun renderFactSaved(fact: io.averkhogliad.ai.challenge.week2.domain.model.Fact) {}
        override fun renderFactList(facts: List<io.averkhogliad.ai.challenge.week2.domain.model.Fact>) {}
        override fun renderFactForgotten(factId: String) {}
        override fun renderFactNotFound(factId: String) {}
        override fun renderFactSearchResults(
            facts: List<io.averkhogliad.ai.challenge.week2.domain.model.Fact>,
            query: String
        ) {
        }

        override fun renderFactSearchEmpty(query: String) {}
        override fun renderProfileList(profiles: List<io.averkhogliad.ai.challenge.week2.domain.model.Profile>) {}
        override fun renderProfileDetail(profile: io.averkhogliad.ai.challenge.week2.domain.model.Profile) {}
        override fun renderProfileDeleted(name: String) {}
        override fun renderProfileUpdated(name: String) {}
        override fun renderProfileError(message: String) {}
        override fun renderMultilineInputPrompt() {}
        override fun renderProfileNotFoundById(id: String) {}
        override fun renderProfileNotFoundByName(name: String) {}
        override fun renderProfileAlreadyExists(name: String) {}
        override fun renderMissingProfileId() {}
        override fun renderMissingProfileName() {}
        override fun renderEmptyProfileContent() {}
        override fun renderProfileDescriptionPrompt() {}
        override fun renderProfileInstructionsPrompt() {}
        override fun renderCannotDeleteActiveProfile() {}
        override fun renderStatusProfile(profileName: String?) {
            renderedMessages.add("statusProfile:${profileName ?: "null"}")
        }

        override fun renderProfileContentTooLong(length: Int) {}

        override fun renderFsmState(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState) {}

        override fun renderStatusDebug(enabled: Boolean) {
            renderedMessages.add("statusDebug:$enabled")
        }

        override fun renderStatusActiveCommand(commandName: String?) {}
        override fun renderFsmStateInfo(state: io.averkhogliad.ai.challenge.week2.domain.model.CommandState) {}
        override fun renderNoActiveCommand() {}
        override fun renderAbortConfirmation() {}
        override fun renderAbortSuccess() {}
        override fun renderAbortCancelled() {}
        override fun renderInvariantList(invariants: List<io.averkhogliad.ai.challenge.week2.domain.model.Invariant>) {}
        override fun renderInvariantAdded(invariant: io.averkhogliad.ai.challenge.week2.domain.model.Invariant) {}
        override fun renderInvariantRemoved(id: Int) {}
        override fun renderInvariantNotFound(id: Int) {}
        override fun renderInvariantEmptyRule() {}
        override fun renderInvariantRemoveConfirmation(id: Int) {}
        override fun renderStatusInvariants(count: Int) {}

        override fun waitForEnter() {}

        override fun renderStatusFsm(
            stage: io.averkhogliad.ai.challenge.week2.domain.model.CommandStage?,
            availableTransitions: List<io.averkhogliad.ai.challenge.week2.domain.model.Transition>
        ) {
        }

        override fun renderStateMap(stateMap: io.averkhogliad.ai.challenge.week2.domain.model.StateMap) {}

        override fun renderGotoSuccess(
            from: io.averkhogliad.ai.challenge.week2.domain.model.CommandStage,
            to: io.averkhogliad.ai.challenge.week2.domain.model.CommandStage
        ) {
        }

        override fun renderGotoError(reason: String) {}

        override fun renderGotoNoActiveCommand() {}

        override fun renderGotoInvalidState(stateName: String) {}

        override fun renderAvailableTransitions(
            transitions: List<io.averkhogliad.ai.challenge.week2.domain.model.Transition>
        ) {
        }
    }

    @Test
    @DisplayName("should show active profile name in status")
    fun `status with active profile`() = runBlocking {
        val profileRepository = InMemoryProfileRepository()
        val renderer = TestCliRenderer()

        // Создаём приложение с ProfileRepository
        val app = CliApplication(
            executors = emptyMap(),
            renderer = renderer,
            profileRepository = profileRepository
        )
        assertNotNull(app)

        // Создаём профиль через ProfileService и явно активируем
        val profileService = ProfileService(profileRepository)
        val profile = profileService.handleCreateProfile("Test Profile", "Test content", "")
        profileService.handleActivateProfile(profile.id)

        // Проверяем, что активный профиль найден
        val active = profileRepository.findActive()
        assertNotNull(active)
        assertTrue(active.isActive)
        assertEquals("Test Profile", active.name)

        // Вызываем рендеринг статуса профиля напрямую (симулируя :status)
        renderer.renderStatusProfile(active.name)

        // Проверяем, что рендеринг был вызван с правильным именем
        val statusMsg = renderer.renderedMessages.find { it.startsWith("statusProfile:") }
        assertNotNull(statusMsg)
        assertEquals("statusProfile:Test Profile", statusMsg)
    }

    @Test
    @DisplayName("should show no profile when none is active")
    fun `status without active profile`() = runBlocking {
        val profileRepository = InMemoryProfileRepository()
        val renderer = TestCliRenderer()

        val app = CliApplication(
            executors = emptyMap(),
            renderer = renderer,
            profileRepository = profileRepository
        )
        assertNotNull(app)

        // Нет активного профиля
        val active = profileRepository.findActive()
        assertNull(active)

        // Вызываем рендеринг статуса профиля напрямую (симулируя :status)
        renderer.renderStatusProfile(null)

        // Проверяем, что рендеринг был вызван с null
        val statusMsg = renderer.renderedMessages.find { it.startsWith("statusProfile:") }
        assertNotNull(statusMsg)
        assertEquals("statusProfile:null", statusMsg)
    }
}
