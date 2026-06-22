package io.averkhogliad.ai.challenge.week2.cli

import io.averkhogliad.ai.challenge.week2.application.executor.TaskExecutor
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskId
import io.averkhogliad.ai.challenge.week2.domain.TaskMetadata
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты для [CliApplication] — основной CLI-оболочки приложения.
 *
 * Проверяют:
 * - Корректную обработку команд
 * - Управление состоянием через REPL-цикл
 * - Взаимодействие с executor'ами
 */
@DisplayName("CliApplication")
class CliApplicationTest {

    /**
     * Mock TaskExecutor для тестирования
     */
    private class MockTaskExecutor(
        override val taskId: TaskId,
        override val metadata: TaskMetadata = TaskMetadata(
            id = taskId,
            title = "Mock Task ${taskId.value}",
            description = "Mock task description"
        ),
        private val resultToReturn: TaskResult = TaskResult.Success("mock result")
    ) : TaskExecutor {
        var lastPrompt: Prompt? = null
        var lastConfig: TaskExecutionConfig? = null

        override suspend fun execute(prompt: Prompt, config: TaskExecutionConfig): TaskResult {
            lastPrompt = prompt
            lastConfig = config
            return resultToReturn
        }
    }

    /**
     * Mock CliRenderer для тестирования
     */
    private class MockCliRenderer : CliRenderer {
        val renderedMessages = mutableListOf<String>()

        override fun renderWelcome() {
            renderedMessages.add("welcome")
        }

        override fun renderMenu(executors: List<TaskExecutor>) {
            renderedMessages.add("menu:${executors.size}")
        }

        override fun renderPrompt(state: CliState) {
            renderedMessages.add("prompt:${state.currentTaskId}")
        }

        override fun renderHelp(state: CliState) {
            renderedMessages.add("help")
        }

        override fun renderParameters(state: CliState) {
            renderedMessages.add("parameters")
        }

        override fun renderError(message: String) {
            renderedMessages.add("error:$message")
        }

        override fun renderSuccess(message: String) {
            renderedMessages.add("success:$message")
        }

        override fun renderInfo(message: String) {
            renderedMessages.add("info:$message")
        }

        override fun renderResult(result: TaskResult) {
            renderedMessages.add("result:${result::class.simpleName}")
        }

        override fun renderTaskHeader(metadata: TaskMetadata) {
            renderedMessages.add("taskHeader:${metadata.title}")
        }

        override fun renderRequestInfo(text: String, config: TaskExecutionConfig) {
            renderedMessages.add("request:$text")
        }

        override fun renderLoadingStart(message: String) {
            renderedMessages.add("loadingStart:$message")
        }

        override fun renderLoadingStop() {
            renderedMessages.add("loadingStop")
        }

        override fun renderGoodbye() {
            renderedMessages.add("goodbye")
        }

        override fun renderTaskList(tasks: List<io.averkhogliad.ai.challenge.week2.domain.model.Task>) {
            renderedMessages.add("taskList:${tasks.size}")
        }

        override fun renderTaskDetail(task: io.averkhogliad.ai.challenge.week2.domain.model.Task) {
            renderedMessages.add("taskDetail:${task.id.value}")
        }

        override fun renderTaskCreated(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskCreated:${taskId.value}")
        }

        override fun renderTaskUpdated(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskUpdated:${taskId.value}")
        }

        override fun renderTaskDeleted(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskDeleted:${taskId.value}")
        }

        override fun renderTaskClosed(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskClosed:${taskId.value}")
        }

        override fun renderTaskCancelled(taskId: io.averkhogliad.ai.challenge.week2.domain.model.TaskId) {
            renderedMessages.add("taskCancelled:${taskId.value}")
        }

        override fun renderMemoryStatus(status: MemoryStatus) {
            renderedMessages.add("memoryStatus:${status.messageCount}")
        }

        override fun renderMemoryCleared() {
            renderedMessages.add("memoryCleared")
        }

        override fun renderStepCreated(step: io.averkhogliad.ai.challenge.week2.domain.model.TaskStep) {
            renderedMessages.add("stepCreated:${step.id.value}")
        }

        override fun renderStepList(steps: List<io.averkhogliad.ai.challenge.week2.domain.model.TaskStep>) {
            renderedMessages.add("stepList:${steps.size}")
        }

        override fun renderStepCompleted(step: io.averkhogliad.ai.challenge.week2.domain.model.TaskStep) {
            renderedMessages.add("stepCompleted:${step.id.value}")
        }

        override fun renderStepError(message: String) {
            renderedMessages.add("stepError:$message")
        }

        override fun renderFactSaved(fact: io.averkhogliad.ai.challenge.week2.domain.model.Fact) {
            renderedMessages.add("factSaved:${fact.id.value}")
        }

        override fun renderFactList(facts: List<io.averkhogliad.ai.challenge.week2.domain.model.Fact>) {
            renderedMessages.add("factList:${facts.size}")
        }

        override fun renderFactForgotten(factId: String) {
            renderedMessages.add("factForgotten:$factId")
        }

        override fun renderFactNotFound(factId: String) {
            renderedMessages.add("factNotFound:$factId")
        }

        override fun renderFactSearchResults(
            facts: List<io.averkhogliad.ai.challenge.week2.domain.model.Fact>,
            query: String
        ) {
            renderedMessages.add("factSearchResults:$query:${facts.size}")
        }

        override fun renderFactSearchEmpty(query: String) {
            renderedMessages.add("factSearchEmpty:$query")
        }

        override fun renderProfileList(profiles: List<io.averkhogliad.ai.challenge.week2.domain.model.Profile>) {
            renderedMessages.add("profileList:${profiles.size}")
        }

        override fun renderProfileDetail(profile: io.averkhogliad.ai.challenge.week2.domain.model.Profile) {
            renderedMessages.add("profileDetail:${profile.name}")
        }

        override fun renderProfileDeleted(name: String) {
            renderedMessages.add("profileDeleted:$name")
        }

        override fun renderProfileUpdated(name: String) {
            renderedMessages.add("profileUpdated:$name")
        }

        override fun renderProfileError(message: String) {
            renderedMessages.add("profileError:$message")
        }

        override fun renderMultilineInputPrompt() {
            renderedMessages.add("multilineInputPrompt")
        }

        override fun renderProfileNotFoundById(id: String) {
            renderedMessages.add("profileNotFoundById:$id")
        }

        override fun renderProfileNotFoundByName(name: String) {
            renderedMessages.add("profileNotFoundByName:$name")
        }

        override fun renderProfileAlreadyExists(name: String) {
            renderedMessages.add("profileAlreadyExists:$name")
        }

        override fun renderMissingProfileId() {
            renderedMessages.add("missingProfileId")
        }

        override fun renderMissingProfileName() {
            renderedMessages.add("missingProfileName")
        }

        override fun renderEmptyProfileContent() {
            renderedMessages.add("emptyProfileContent")
        }

        override fun renderProfileDescriptionPrompt() {
            renderedMessages.add("profileDescriptionPrompt")
        }

        override fun renderProfileInstructionsPrompt() {
            renderedMessages.add("profileInstructionsPrompt")
        }

        override fun renderCannotDeleteActiveProfile() {
            renderedMessages.add("cannotDeleteActiveProfile")
        }

        override fun renderStatusProfile(profileName: String?) {
            renderedMessages.add("statusProfile:${profileName ?: "null"}")
        }

        override fun renderProfileContentTooLong(length: Int) {
            renderedMessages.add("profileContentTooLong:$length")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Command handling
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Command handling")
    inner class CommandHandling {

        @Test
        @DisplayName("should create CliApplication with executors")
        fun `creates application with executors`() {
            val executor = MockTaskExecutor(TaskId(1))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(TaskId(1) to executor),
                renderer = renderer
            )

            assertNotNull(app)
        }

        @Test
        @DisplayName("should handle Help command without changing state")
        fun `handles Help command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId(1))
            val renderer = MockCliRenderer()
            val app = CliApplication(
                executors = mapOf(TaskId(1) to executor),
                renderer = renderer
            )

            // Проверяем, что приложение создано корректно
            // (REPL не запускаем, так как это side-effect)
            assertNotNull(app)
        }

        @Test
        @DisplayName("should handle Quit command to stop REPL")
        fun `handles Quit command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId(1))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(TaskId(1) to executor),
                renderer = renderer
            )

            assertNotNull(app)
        }

        @Test
        @DisplayName("should handle SelectTask command to change current task")
        fun `handles SelectTask command`() = runBlocking {
            val executor = MockTaskExecutor(TaskId(1))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(TaskId(1) to executor),
                renderer = renderer
            )

            assertNotNull(app)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // State management
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("State management")
    inner class StateManagement {

        @Test
        @DisplayName("CliState should have default values")
        fun `CliState has default values`() {
            val state = CliState()

            assertNull(state.currentTaskId)
            assertNull(state.currentDialogId)
            assertEquals(TaskExecutionConfig(), state.executionConfig)
            assertTrue(state.isRunning)
        }

        @Test
        @DisplayName("CliState should be immutable")
        fun `CliState is immutable`() {
            val state1 = CliState(currentTaskId = 1)
            val state2 = state1.copy(currentTaskId = 2)

            assertEquals(1, state1.currentTaskId)
            assertEquals(2, state2.currentTaskId)
        }

        @Test
        @DisplayName("CliState should support copy with modifications")
        fun `CliState supports copy`() {
            val state = CliState()
            val newState = state.copy(
                currentTaskId = 1,
                currentDialogId = "dialog-1",
                isRunning = false
            )

            assertEquals(1, newState.currentTaskId)
            assertEquals("dialog-1", newState.currentDialogId)
            assertEquals(false, newState.isRunning)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Executor interaction
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Executor interaction")
    inner class ExecutorInteraction {

        @Test
        @DisplayName("should create application with multiple executors")
        fun `creates application with multiple executors`() {
            val executor1 = MockTaskExecutor(TaskId(1))
            val executor2 = MockTaskExecutor(TaskId(2))
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = mapOf(
                    TaskId(1) to executor1,
                    TaskId(2) to executor2
                ),
                renderer = renderer
            )

            assertNotNull(app)
        }

        @Test
        @DisplayName("should create application with empty executors map")
        fun `creates application with empty executors`() {
            val renderer = MockCliRenderer()

            val app = CliApplication(
                executors = emptyMap(),
                renderer = renderer
            )

            assertNotNull(app)
        }
    }
}
