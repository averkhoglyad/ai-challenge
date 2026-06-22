package io.averkhogliad.ai.challenge.week2.domain.model

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Тесты для доменной модели [WorkingMemory].
 */
@DisplayName("WorkingMemory")
class WorkingMemoryTest {

    @Nested
    @DisplayName("Создание рабочей памяти")
    inner class Creation {

        @Test
        @DisplayName("should create empty working memory")
        fun `should create empty working memory`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)

            assertEquals(sessionId, memory.sessionId)
            assertTrue(memory.currentMessages.isEmpty())
            assertNull(memory.summary)
        }
    }

    @Nested
    @DisplayName("Добавление сообщений")
    inner class AddMessage {

        @Test
        @DisplayName("should add message to working memory")
        fun `should add message to working memory`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val message = Message.create(sessionId, MessageRole.USER, "Hello")

            val updatedMemory = memory.addMessage(message)

            assertEquals(1, updatedMemory.currentMessages.size)
            assertEquals(message, updatedMemory.currentMessages[0])
        }

        @Test
        @DisplayName("should throw exception when message sessionId doesn't match")
        fun `should throw when message sessionId doesn't match`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val otherSessionId = SessionId.generate()
            val message = Message.create(otherSessionId, MessageRole.USER, "Hello")

            assertThrows<IllegalArgumentException> {
                memory.addMessage(message)
            }
        }

        @Test
        @DisplayName("should add multiple messages")
        fun `should add multiple messages`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val msg1 = Message.create(sessionId, MessageRole.USER, "First")
            val msg2 = Message.create(sessionId, MessageRole.ASSISTANT, "Second")

            val memoryWithMsg1 = memory.addMessage(msg1)
            val memoryWithMsg2 = memoryWithMsg1.addMessage(msg2)

            assertEquals(2, memoryWithMsg2.currentMessages.size)
            assertEquals(msg1, memoryWithMsg2.currentMessages[0])
            assertEquals(msg2, memoryWithMsg2.currentMessages[1])
        }
    }

    @Nested
    @DisplayName("Обновление свёртки")
    inner class UpdateSummary {

        @Test
        @DisplayName("should update summary and clear messages")
        fun `should update summary and clear messages`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val msg1 = Message.create(sessionId, MessageRole.USER, "First")
            val msg2 = Message.create(sessionId, MessageRole.ASSISTANT, "Second")
            val memoryWithMessages = memory.addMessage(msg1).addMessage(msg2)

            val updatedMemory = memoryWithMessages.updateSummary("Summary of conversation")

            assertEquals("Summary of conversation", updatedMemory.summary)
            assertTrue(updatedMemory.currentMessages.isEmpty())
        }

        @Test
        @DisplayName("should preserve sessionId after summary update")
        fun `should preserve sessionId after summary update`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val message = Message.create(sessionId, MessageRole.USER, "Hello")
            val memoryWithMessage = memory.addMessage(message)

            val updatedMemory = memoryWithMessage.updateSummary("New summary")

            assertEquals(sessionId, updatedMemory.sessionId)
        }
    }

    @Nested
    @DisplayName("Валидация сообщений")
    inner class Validation {

        @Test
        @DisplayName("should throw exception when creating with messages from different sessions")
        fun `should throw when creating with messages from different sessions`() {
            val sessionId1 = SessionId.generate()
            val sessionId2 = SessionId.generate()
            val message1 = Message.create(sessionId1, MessageRole.USER, "First")
            val message2 = Message.create(sessionId2, MessageRole.USER, "Second")

            assertThrows<IllegalArgumentException> {
                WorkingMemory(
                    sessionId = sessionId1,
                    currentMessages = listOf(message1, message2),
                    summary = null
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Работа с шагами задачи (TaskStep)
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Создание с шагами (forTaskLevel)")
    inner class ForTaskLevel {

        @Test
        @DisplayName("should create WorkingMemory with steps for task level")
        fun `should create WorkingMemory with steps`() {
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step1 = TaskStep(TaskStepId("step-1"), taskId, "Step 1", false, 0, now)
            val step2 = TaskStep(TaskStepId("step-2"), taskId, "Step 2", true, 1, now)
            val steps = listOf(step1, step2)

            val memory = WorkingMemory.forTaskLevel(sessionId, steps)

            assertEquals(sessionId, memory.sessionId)
            assertEquals(2, memory.steps.size)
            assertEquals(step1, memory.steps[0])
            assertEquals(step2, memory.steps[1])
            assertTrue(memory.currentMessages.isEmpty())
            assertNull(memory.summary)
        }

        @Test
        @DisplayName("should create WorkingMemory with empty steps by default")
        fun `should create WorkingMemory with empty steps by default`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.forTaskLevel(sessionId)

            assertEquals(sessionId, memory.sessionId)
            assertTrue(memory.steps.isEmpty())
        }
    }

    @Nested
    @DisplayName("Обновление шагов (updateSteps)")
    inner class UpdateSteps {

        @Test
        @DisplayName("should update steps list")
        fun `should update steps list`() {
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val initialStep = TaskStep(TaskStepId("step-1"), taskId, "Initial step", false, 0, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(initialStep))

            val newStep = TaskStep(TaskStepId("step-2"), taskId, "New step", true, 1, now)
            val updatedMemory = memory.updateSteps(listOf(newStep))

            assertEquals(1, updatedMemory.steps.size)
            assertEquals("New step", updatedMemory.steps[0].text)
            assertTrue(updatedMemory.steps[0].isCompleted)
        }

        @Test
        @DisplayName("should clear steps with empty list")
        fun `should clear steps with empty list`() {
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step = TaskStep(TaskStepId("step-1"), taskId, "Step", false, 0, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step))

            val updatedMemory = memory.updateSteps(emptyList())

            assertTrue(updatedMemory.steps.isEmpty())
        }
    }

    @Nested
    @DisplayName("Форматирование контекста с шагами (toPromptContext)")
    inner class ToPromptContext {

        @Test
        @DisplayName("should display steps in [x]/[ ] format in prompt context")
        fun `should display steps in checkbox format`() {
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step1 = TaskStep(TaskStepId("step-1"), taskId, "Buy milk", false, 0, now)
            val step2 = TaskStep(TaskStepId("step-2"), taskId, "Write code", true, 1, now)
            val step3 = TaskStep(TaskStepId("step-3"), taskId, "Test feature", false, 2, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step1, step2, step3))

            val context = memory.toPromptContext()

            assertTrue(context.contains("Steps:"))
            assertTrue(context.contains("1. [ ] Buy milk"))
            assertTrue(context.contains("2. [x] Write code"))
            assertTrue(context.contains("3. [ ] Test feature"))
        }

        @Test
        @DisplayName("should sort steps by order field")
        fun `should sort steps by order field`() {
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            // Создаём шаги в неправильном порядке
            val step2 = TaskStep(TaskStepId("step-2"), taskId, "Second", false, 1, now)
            val step0 = TaskStep(TaskStepId("step-0"), taskId, "First", false, 0, now)
            val step3 = TaskStep(TaskStepId("step-3"), taskId, "Third", true, 2, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step2, step0, step3))

            val context = memory.toPromptContext()
            val lines = context.lines()

            val firstStepLine = lines.indexOfFirst { it.contains("1. [ ] First") }
            val secondStepLine = lines.indexOfFirst { it.contains("2. [ ] Second") }
            val thirdStepLine = lines.indexOfFirst { it.contains("3. [x] Third") }

            assertTrue(firstStepLine >= 0, "First step not found")
            assertTrue(secondStepLine >= 0, "Second step not found")
            assertTrue(thirdStepLine >= 0, "Third step not found")
            assertTrue(firstStepLine < secondStepLine, "First step should come before second")
            assertTrue(secondStepLine < thirdStepLine, "Second step should come before third")
        }

        @Test
        @DisplayName("should omit Steps section when no steps present")
        fun `should omit Steps section when no steps`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList())

            val context = memory.toPromptContext()

            assertTrue(!context.contains("Steps:"))
        }

        @Test
        @DisplayName("should combine summary, steps, and messages in prompt context")
        fun `should combine all sections in prompt context`() {
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step = TaskStep(TaskStepId("step-1"), taskId, "Do something", true, 0, now)

            val message = Message.create(sessionId, MessageRole.USER, "Hello")
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step))
                .updateSummary("Previous summary")
                .addMessage(message)

            val context = memory.toPromptContext()

            assertTrue(context.contains("Context Summary:"), "Should contain summary")
            assertTrue(context.contains("Previous summary"), "Should contain summary text")
            assertTrue(context.contains("Steps:"), "Should contain steps")
            assertTrue(context.contains("1. [x] Do something"), "Should contain step")
            assertTrue(context.contains("Recent Messages:"), "Should contain messages")
            assertTrue(context.contains("[USER] Hello"), "Should contain message text")
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // US-DESC-4: Отображение description в WM
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Отображение description задачи (US-DESC-4)")
    inner class TaskDescription {

        @Test
        @DisplayName("should create WorkingMemory with taskDescription")
        fun `should create WorkingMemory with taskDescription`() {
            val sessionId = SessionId.generate()
            val description = "This is a detailed task description"
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), description)

            assertEquals(description, memory.taskDescription)
        }

        @Test
        @DisplayName("should create WorkingMemory with null taskDescription by default")
        fun `should create WorkingMemory with null taskDescription by default`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.forTaskLevel(sessionId)

            assertNull(memory.taskDescription)
        }

        @Test
        @DisplayName("should include taskDescription in prompt context")
        fun `should include taskDescription in prompt context`() {
            val sessionId = SessionId.generate()
            val description = "Implement user authentication with OAuth2"
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), description)

            val context = memory.toPromptContext()

            assertTrue(context.contains("Task Description:"), "Should contain Task Description header")
            assertTrue(context.contains(description), "Should contain description text")
        }

        @Test
        @DisplayName("should omit Task Description section when description is null")
        fun `should omit Task Description section when description is null`() {
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), null)

            val context = memory.toPromptContext()

            assertTrue(!context.contains("Task Description:"), "Should not contain Task Description header")
        }

        @Test
        @DisplayName("should combine taskDescription with steps and summary in prompt context")
        fun `should combine taskDescription with steps and summary`() {
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val description = "Build a REST API"
            val step = TaskStep(TaskStepId("step-1"), taskId, "Create endpoints", false, 0, now)

            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step), description)
                .updateSummary("Previous work on API")

            val context = memory.toPromptContext()

            assertTrue(context.contains("Task Description:"), "Should contain description")
            assertTrue(context.contains(description), "Should contain description text")
            assertTrue(context.contains("Context Summary:"), "Should contain summary")
            assertTrue(context.contains("Steps:"), "Should contain steps")
        }
    }
}
