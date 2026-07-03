package io.averkhogliad.ai.challenge.week4.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week4.cli.domain.model.*

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe

/**
 * Тесты для доменной модели [WorkingMemory].
 */
class WorkingMemoryTest : FreeSpec({

    "Создание рабочей памяти" - {

        "should create empty working memory" {
            // given
            val sessionId = SessionId.generate()

            // when
            val memory = WorkingMemory.create(sessionId)

            // then
            memory.sessionId shouldBe sessionId
            memory.currentMessages.isEmpty() shouldBe true
            memory.summary shouldBe null
        }
    }

    "Добавление сообщений" - {

        "should add message to working memory" {
            // given
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val message = Message.create(sessionId, MessageRole.USER, "Hello")

            // when
            val updatedMemory = memory.addMessage(message)

            // then
            updatedMemory.currentMessages.size shouldBe 1
            updatedMemory.currentMessages[0] shouldBe message
        }

        "should throw when message sessionId doesn't match" {
            // given
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val otherSessionId = SessionId.generate()
            val message = Message.create(otherSessionId, MessageRole.USER, "Hello")

            // when & then
            shouldThrow<IllegalArgumentException> {
                memory.addMessage(message)
            }
        }

        "should add multiple messages" {
            // given
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val msg1 = Message.create(sessionId, MessageRole.USER, "First")
            val msg2 = Message.create(sessionId, MessageRole.ASSISTANT, "Second")

            // when
            val memoryWithMsg1 = memory.addMessage(msg1)
            val memoryWithMsg2 = memoryWithMsg1.addMessage(msg2)

            // then
            memoryWithMsg2.currentMessages.size shouldBe 2
            memoryWithMsg2.currentMessages[0] shouldBe msg1
            memoryWithMsg2.currentMessages[1] shouldBe msg2
        }
    }

    "Обновление свёртки" - {

        "should update summary and clear messages" {
            // given
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val msg1 = Message.create(sessionId, MessageRole.USER, "First")
            val msg2 = Message.create(sessionId, MessageRole.ASSISTANT, "Second")
            val memoryWithMessages = memory.addMessage(msg1).addMessage(msg2)

            // when
            val updatedMemory = memoryWithMessages.updateSummary("Summary of conversation")

            // then
            updatedMemory.summary shouldBe "Summary of conversation"
            updatedMemory.currentMessages.isEmpty() shouldBe true
        }

        "should preserve sessionId after summary update" {
            // given
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.create(sessionId)
            val message = Message.create(sessionId, MessageRole.USER, "Hello")
            val memoryWithMessage = memory.addMessage(message)

            // when
            val updatedMemory = memoryWithMessage.updateSummary("New summary")

            // then
            updatedMemory.sessionId shouldBe sessionId
        }
    }

    "Валидация сообщений" - {

        "should throw when creating with messages from different sessions" {
            // given
            val sessionId1 = SessionId.generate()
            val sessionId2 = SessionId.generate()
            val message1 = Message.create(sessionId1, MessageRole.USER, "First")
            val message2 = Message.create(sessionId2, MessageRole.USER, "Second")

            // when & then
            shouldThrow<IllegalArgumentException> {
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

    "Создание с шагами (forTaskLevel)" - {

        "should create WorkingMemory with steps" {
            // given
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step1 = TaskStep(TaskStepId("step-1"), taskId, "Step 1", false, 0, now)
            val step2 = TaskStep(TaskStepId("step-2"), taskId, "Step 2", true, 1, now)
            val steps = listOf(step1, step2)

            // when
            val memory = WorkingMemory.forTaskLevel(sessionId, steps)

            // then
            memory.sessionId shouldBe sessionId
            memory.steps.size shouldBe 2
            memory.steps[0] shouldBe step1
            memory.steps[1] shouldBe step2
            memory.currentMessages.isEmpty() shouldBe true
            memory.summary shouldBe null
        }

        "should create WorkingMemory with empty steps by default" {
            // given
            val sessionId = SessionId.generate()

            // when
            val memory = WorkingMemory.forTaskLevel(sessionId)

            // then
            memory.sessionId shouldBe sessionId
            memory.steps.isEmpty() shouldBe true
        }
    }

    "Обновление шагов (updateSteps)" - {

        "should update steps list" {
            // given
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val initialStep = TaskStep(TaskStepId("step-1"), taskId, "Initial step", false, 0, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(initialStep))
            val newStep = TaskStep(TaskStepId("step-2"), taskId, "New step", true, 1, now)

            // when
            val updatedMemory = memory.updateSteps(listOf(newStep))

            // then
            updatedMemory.steps.size shouldBe 1
            updatedMemory.steps[0].text shouldBe "New step"
            updatedMemory.steps[0].isCompleted shouldBe true
        }

        "should clear steps with empty list" {
            // given
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step = TaskStep(TaskStepId("step-1"), taskId, "Step", false, 0, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step))

            // when
            val updatedMemory = memory.updateSteps(emptyList())

            // then
            updatedMemory.steps.isEmpty() shouldBe true
        }
    }

    "Форматирование контекста с шагами (toPromptContext)" - {

        "should display steps in checkbox format" {
            // given
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step1 = TaskStep(TaskStepId("step-1"), taskId, "Buy milk", false, 0, now)
            val step2 = TaskStep(TaskStepId("step-2"), taskId, "Write code", true, 1, now)
            val step3 = TaskStep(TaskStepId("step-3"), taskId, "Test feature", false, 2, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step1, step2, step3))

            // when
            val context = memory.toPromptContext()

            // then
            context.contains("Steps:") shouldBe true
            context.contains("1. [ ] Buy milk") shouldBe true
            context.contains("2. [x] Write code") shouldBe true
            context.contains("3. [ ] Test feature") shouldBe true
        }

        "should sort steps by order field" {
            // given
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            // Создаём шаги в неправильном порядке
            val step2 = TaskStep(TaskStepId("step-2"), taskId, "Second", false, 1, now)
            val step0 = TaskStep(TaskStepId("step-0"), taskId, "First", false, 0, now)
            val step3 = TaskStep(TaskStepId("step-3"), taskId, "Third", true, 2, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step2, step0, step3))

            // when
            val context = memory.toPromptContext()
            val lines = context.lines()

            // then
            val firstStepLine = lines.indexOfFirst { it.contains("1. [ ] First") }
            val secondStepLine = lines.indexOfFirst { it.contains("2. [ ] Second") }
            val thirdStepLine = lines.indexOfFirst { it.contains("3. [x] Third") }

            (firstStepLine >= 0) shouldBe true
            (secondStepLine >= 0) shouldBe true
            (thirdStepLine >= 0) shouldBe true
            (firstStepLine < secondStepLine) shouldBe true
            (secondStepLine < thirdStepLine) shouldBe true
        }

        "should omit Steps section when no steps" {
            // given
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList())

            // when
            val context = memory.toPromptContext()

            // then
            context.contains("Steps:") shouldBe false
        }

        "should combine all sections in prompt context" {
            // given
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val step = TaskStep(TaskStepId("step-1"), taskId, "Do something", true, 0, now)
            val message = Message.create(sessionId, MessageRole.USER, "Hello")
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step))
                .updateSummary("Previous summary")
                .addMessage(message)

            // when
            val context = memory.toPromptContext()

            // then
            context.contains("Context Summary:") shouldBe true
            context.contains("Previous summary") shouldBe true
            context.contains("Steps:") shouldBe true
            context.contains("1. [x] Do something") shouldBe true
            context.contains("Recent Messages:") shouldBe true
            context.contains("[USER] Hello") shouldBe true
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // US-DESC-4: Отображение description в WM
    // ═══════════════════════════════════════════════════════════════

    "Отображение description задачи (US-DESC-4)" - {

        "should create WorkingMemory with taskDescription" {
            // given
            val sessionId = SessionId.generate()
            val description = "This is a detailed task description"

            // when
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), description)

            // then
            memory.taskDescription shouldBe description
        }

        "should create WorkingMemory with null taskDescription by default" {
            // given
            val sessionId = SessionId.generate()

            // when
            val memory = WorkingMemory.forTaskLevel(sessionId)

            // then
            memory.taskDescription shouldBe null
        }

        "should include taskDescription in prompt context" {
            // given
            val sessionId = SessionId.generate()
            val description = "Implement user authentication with OAuth2"
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), description)

            // when
            val context = memory.toPromptContext()

            // then
            context.contains("Task Description:") shouldBe true
            context.contains(description) shouldBe true
        }

        "should omit Task Description section when description is null" {
            // given
            val sessionId = SessionId.generate()
            val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), null)

            // when
            val context = memory.toPromptContext()

            // then
            context.contains("Task Description:") shouldBe false
        }

        "should combine taskDescription with steps and summary" {
            // given
            val sessionId = SessionId.generate()
            val taskId = TaskId("task-1")
            val now = java.time.Instant.now()
            val description = "Build a REST API"
            val step = TaskStep(TaskStepId("step-1"), taskId, "Create endpoints", false, 0, now)
            val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step), description)
                .updateSummary("Previous work on API")

            // when
            val context = memory.toPromptContext()

            // then
            context.contains("Task Description:") shouldBe true
            context.contains(description) shouldBe true
            context.contains("Context Summary:") shouldBe true
            context.contains("Steps:") shouldBe true
        }
    }
})
