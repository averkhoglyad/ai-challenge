package io.averkhogliad.ai.challenge.week3.cli.unit.domain.model

import io.averkhogliad.ai.challenge.week3.cli.domain.model.Message
import io.averkhogliad.ai.challenge.week3.cli.domain.model.MessageRole
import io.averkhogliad.ai.challenge.week3.cli.domain.model.SessionId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStep
import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskStepId
import io.averkhogliad.ai.challenge.week3.cli.domain.model.WorkingMemory
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.time.Instant

class WorkingMemoryTest : FreeSpec({

    "WorkingMemory" - {

        "Creation" - {

            "should create empty working memory" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.create(sessionId)

                memory.sessionId shouldBe sessionId
                memory.currentMessages.isEmpty() shouldBe true
                memory.summary shouldBe null
            }
        }

        "AddMessage" - {

            "should add message to working memory" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.create(sessionId)
                val message = Message.create(sessionId, MessageRole.USER, "Hello")

                val updatedMemory = memory.addMessage(message)

                updatedMemory.currentMessages.size shouldBe 1
                updatedMemory.currentMessages[0] shouldBe message
            }

            "should throw exception when message sessionId doesn't match" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.create(sessionId)
                val otherSessionId = SessionId.generate()
                val message = Message.create(otherSessionId, MessageRole.USER, "Hello")

                shouldThrow<IllegalArgumentException> {
                    memory.addMessage(message)
                }
            }

            "should add multiple messages" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.create(sessionId)
                val msg1 = Message.create(sessionId, MessageRole.USER, "First")
                val msg2 = Message.create(sessionId, MessageRole.ASSISTANT, "Second")

                val memoryWithMsg1 = memory.addMessage(msg1)
                val memoryWithMsg2 = memoryWithMsg1.addMessage(msg2)

                memoryWithMsg2.currentMessages.size shouldBe 2
                memoryWithMsg2.currentMessages[0] shouldBe msg1
                memoryWithMsg2.currentMessages[1] shouldBe msg2
            }
        }

        "UpdateSummary" - {

            "should update summary and clear messages" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.create(sessionId)
                val msg1 = Message.create(sessionId, MessageRole.USER, "First")
                val msg2 = Message.create(sessionId, MessageRole.ASSISTANT, "Second")
                val memoryWithMessages = memory.addMessage(msg1).addMessage(msg2)

                val updatedMemory = memoryWithMessages.updateSummary("Summary of conversation")

                updatedMemory.summary shouldBe "Summary of conversation"
                updatedMemory.currentMessages.isEmpty() shouldBe true
            }

            "should preserve sessionId after summary update" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.create(sessionId)
                val message = Message.create(sessionId, MessageRole.USER, "Hello")
                val memoryWithMessage = memory.addMessage(message)

                val updatedMemory = memoryWithMessage.updateSummary("New summary")

                updatedMemory.sessionId shouldBe sessionId
            }
        }

        "Validation" - {

            "should throw exception when creating with messages from different sessions" {
                val sessionId1 = SessionId.generate()
                val sessionId2 = SessionId.generate()
                val message1 = Message.create(sessionId1, MessageRole.USER, "First")
                val message2 = Message.create(sessionId2, MessageRole.USER, "Second")

                shouldThrow<IllegalArgumentException> {
                    WorkingMemory(
                        sessionId = sessionId1,
                        currentMessages = listOf(message1, message2),
                        summary = null
                    )
                }
            }
        }

        "ForTaskLevel" - {

            "should create WorkingMemory with steps for task level" {
                val sessionId = SessionId.generate()
                val taskId = TaskId("task-1")
                val now = Instant.now()
                val step1 = TaskStep(TaskStepId("step-1"), taskId, "Step 1", false, 0, now)
                val step2 = TaskStep(TaskStepId("step-2"), taskId, "Step 2", true, 1, now)
                val steps = listOf(step1, step2)

                val memory = WorkingMemory.forTaskLevel(sessionId, steps)

                memory.sessionId shouldBe sessionId
                memory.steps.size shouldBe 2
                memory.steps[0] shouldBe step1
                memory.steps[1] shouldBe step2
                memory.currentMessages.isEmpty() shouldBe true
                memory.summary shouldBe null
            }

            "should create WorkingMemory with empty steps by default" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.forTaskLevel(sessionId)

                memory.sessionId shouldBe sessionId
                memory.steps.isEmpty() shouldBe true
            }
        }

        "UpdateSteps" - {

            "should update steps list" {
                val sessionId = SessionId.generate()
                val taskId = TaskId("task-1")
                val now = Instant.now()
                val initialStep = TaskStep(TaskStepId("step-1"), taskId, "Initial step", false, 0, now)
                val memory = WorkingMemory.forTaskLevel(sessionId, listOf(initialStep))

                val newStep = TaskStep(TaskStepId("step-2"), taskId, "New step", true, 1, now)
                val updatedMemory = memory.updateSteps(listOf(newStep))

                updatedMemory.steps.size shouldBe 1
                updatedMemory.steps[0].text shouldBe "New step"
                updatedMemory.steps[0].isCompleted shouldBe true
            }

            "should clear steps with empty list" {
                val sessionId = SessionId.generate()
                val taskId = TaskId("task-1")
                val now = Instant.now()
                val step = TaskStep(TaskStepId("step-1"), taskId, "Step", false, 0, now)
                val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step))

                val updatedMemory = memory.updateSteps(emptyList())

                updatedMemory.steps.isEmpty() shouldBe true
            }
        }

        "ToPromptContext" - {

            "should display steps in checkbox format in prompt context" {
                val sessionId = SessionId.generate()
                val taskId = TaskId("task-1")
                val now = Instant.now()
                val step1 = TaskStep(TaskStepId("step-1"), taskId, "Buy milk", false, 0, now)
                val step2 = TaskStep(TaskStepId("step-2"), taskId, "Write code", true, 1, now)
                val step3 = TaskStep(TaskStepId("step-3"), taskId, "Test feature", false, 2, now)
                val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step1, step2, step3))

                val context = memory.toPromptContext()

                context.shouldContain("Steps:")
                context.shouldContain("1. [ ] Buy milk")
                context.shouldContain("2. [x] Write code")
                context.shouldContain("3. [ ] Test feature")
            }

            "should sort steps by order field" {
                val sessionId = SessionId.generate()
                val taskId = TaskId("task-1")
                val now = Instant.now()
                val step2 = TaskStep(TaskStepId("step-2"), taskId, "Second", false, 1, now)
                val step0 = TaskStep(TaskStepId("step-0"), taskId, "First", false, 0, now)
                val step3 = TaskStep(TaskStepId("step-3"), taskId, "Third", true, 2, now)
                val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step2, step0, step3))

                val context = memory.toPromptContext()
                val lines = context.lines()

                val firstStepLine = lines.indexOfFirst { it.contains("1. [ ] First") }
                val secondStepLine = lines.indexOfFirst { it.contains("2. [ ] Second") }
                val thirdStepLine = lines.indexOfFirst { it.contains("3. [x] Third") }

                (firstStepLine >= 0) shouldBe true
                (secondStepLine >= 0) shouldBe true
                (thirdStepLine >= 0) shouldBe true
                (firstStepLine < secondStepLine) shouldBe true
                (secondStepLine < thirdStepLine) shouldBe true
            }

            "should omit Steps section when no steps present" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.forTaskLevel(sessionId, emptyList())

                val context = memory.toPromptContext()

                context.shouldNotContain("Steps:")
            }

            "should combine summary, steps, and messages in prompt context" {
                val sessionId = SessionId.generate()
                val taskId = TaskId("task-1")
                val now = Instant.now()
                val step = TaskStep(TaskStepId("step-1"), taskId, "Do something", true, 0, now)

                val message = Message.create(sessionId, MessageRole.USER, "Hello")
                val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step))
                    .updateSummary("Previous summary")
                    .addMessage(message)

                val context = memory.toPromptContext()

                context.shouldContain("Context Summary:")
                context.shouldContain("Previous summary")
                context.shouldContain("Steps:")
                context.shouldContain("1. [x] Do something")
                context.shouldContain("Recent Messages:")
                context.shouldContain("[USER] Hello")
            }
        }

        "TaskDescription" - {

            "should create WorkingMemory with taskDescription" {
                val sessionId = SessionId.generate()
                val description = "This is a detailed task description"
                val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), description)

                memory.taskDescription shouldBe description
            }

            "should create WorkingMemory with null taskDescription by default" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.forTaskLevel(sessionId)

                memory.taskDescription shouldBe null
            }

            "should include taskDescription in prompt context" {
                val sessionId = SessionId.generate()
                val description = "Implement user authentication with OAuth2"
                val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), description)

                val context = memory.toPromptContext()

                context.shouldContain("Task Description:")
                context.shouldContain(description)
            }

            "should omit Task Description section when description is null" {
                val sessionId = SessionId.generate()
                val memory = WorkingMemory.forTaskLevel(sessionId, emptyList(), null)

                val context = memory.toPromptContext()

                context.shouldNotContain("Task Description:")
            }

            "should combine taskDescription with steps and summary in prompt context" {
                val sessionId = SessionId.generate()
                val taskId = TaskId("task-1")
                val now = Instant.now()
                val description = "Build a REST API"
                val step = TaskStep(TaskStepId("step-1"), taskId, "Create endpoints", false, 0, now)

                val memory = WorkingMemory.forTaskLevel(sessionId, listOf(step), description)
                    .updateSummary("Previous work on API")

                val context = memory.toPromptContext()

                context.shouldContain("Task Description:")
                context.shouldContain(description)
                context.shouldContain("Context Summary:")
                context.shouldContain("Steps:")
            }
        }
    }
})
