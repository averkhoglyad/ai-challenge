package io.averkhogliad.ai.challenge.week2.it

import io.averkhogliad.ai.challenge.week2.application.service.TodoTaskService
import io.averkhogliad.ai.challenge.week2.domain.model.TaskStatus
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteDatabase
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.SqliteTaskRepository
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files

class TaskManagementIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var repository: SqliteTaskRepository
    lateinit var todoTaskService: TodoTaskService

    beforeEach {
        tempDbFile = Files.createTempFile("test-task-mgmt-it-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        repository = SqliteTaskRepository(database)
        todoTaskService = TodoTaskService(repository)
    }

    afterEach {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    "create task" - {

        "adds a new task and persists to database" {
            runTest {
                // when
                val createdTask = todoTaskService.addTask("Купить молоко")

                // then
                createdTask.id.shouldNotBeNull()
                createdTask.title shouldBe "Купить молоко"
                createdTask.status shouldBe TaskStatus.OPEN

                val foundTask = repository.findById(createdTask.id)
                foundTask.shouldNotBeNull()
                foundTask.id shouldBe createdTask.id
                foundTask.title shouldBe "Купить молоко"
            }
        }
    }

    "list tasks" - {

        "lists all tasks with different statuses" {
            runTest {
                // given
                val task1 = todoTaskService.addTask("Задача 1")
                val task2 = todoTaskService.addTask("Задача 2")
                val task3 = todoTaskService.addTask("Задача 3")

                todoTaskService.openTask(task2.id)
                todoTaskService.closeTask(null)

                todoTaskService.cancelTask(task3.id)

                // when
                val allTasks = todoTaskService.listTasks()

                // then
                allTasks shouldHaveSize 3
                allTasks.any { it.id == task1.id && it.status == TaskStatus.OPEN } shouldBe true
                allTasks.any { it.id == task2.id && it.status == TaskStatus.CLOSED } shouldBe true
                allTasks.any { it.id == task3.id && it.status == TaskStatus.CANCELLED } shouldBe true
            }
        }
    }

    "edit task" - {

        "edits task title by id" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Старое название")
                val taskId = createdTask.id

                // when
                val updatedTask = todoTaskService.editTask(taskId, "Новое название")

                // then
                updatedTask.title shouldBe "Новое название"
                updatedTask.id shouldBe taskId

                val foundTask = repository.findById(taskId)
                foundTask.shouldNotBeNull()
                foundTask.title shouldBe "Новое название"
            }
        }
    }

    "drop task" - {

        "drops task by id, removes from database" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Задача для удаления")
                val taskId = createdTask.id
                repository.exists(taskId) shouldBe true

                // when
                todoTaskService.dropTask(taskId)

                // then
                repository.findById(taskId).shouldBeNull()
                repository.exists(taskId) shouldBe false
            }
        }
    }

    "open task" - {

        "sets currentTaskId and returns opened task" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Задача для открытия")
                val taskId = createdTask.id
                todoTaskService.currentTaskId.shouldBeNull()

                // when
                val openedTask = todoTaskService.openTask(taskId)

                // then
                todoTaskService.currentTaskId shouldBe taskId
                openedTask.id shouldBe taskId
            }
        }
    }

    "close task" - {

        "changes status to CLOSED and clears currentTaskId" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Задача для закрытия")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)

                createdTask.status shouldBe TaskStatus.OPEN
                todoTaskService.currentTaskId shouldBe taskId

                // when
                val closedTask = todoTaskService.closeTask(null)

                // then
                closedTask.status shouldBe TaskStatus.CLOSED
                todoTaskService.currentTaskId.shouldBeNull()

                val foundTask = repository.findById(taskId)
                foundTask.shouldNotBeNull()
                foundTask.status shouldBe TaskStatus.CLOSED
            }
        }
    }

    "cancel task" - {

        "changes status to CANCELLED" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Задача для отмены")
                val taskId = createdTask.id
                createdTask.status shouldBe TaskStatus.OPEN

                // when
                val cancelledTask = todoTaskService.cancelTask(taskId)

                // then
                cancelledTask.status shouldBe TaskStatus.CANCELLED

                val foundTask = repository.findById(taskId)
                foundTask.shouldNotBeNull()
                foundTask.status shouldBe TaskStatus.CANCELLED
            }
        }
    }

    "back command" - {

        "clears currentTaskId" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Задача для возврата")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)
                todoTaskService.currentTaskId shouldBe taskId

                // when
                todoTaskService.back()

                // then
                todoTaskService.currentTaskId.shouldBeNull()
            }
        }
    }

    "contextual commands" - {

        "edit without id uses currentTaskId" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Старое название")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)
                todoTaskService.currentTaskId shouldBe taskId

                // when
                val updatedTask = todoTaskService.editTask(null, "Новое название")

                // then
                updatedTask.title shouldBe "Новое название"
                updatedTask.id shouldBe taskId

                val foundTask = repository.findById(taskId)
                foundTask.shouldNotBeNull()
                foundTask.title shouldBe "Новое название"
            }
        }

        "close without id uses currentTaskId" {
            runTest {
                // given
                val createdTask = todoTaskService.addTask("Задача для контекстного закрытия")
                val taskId = createdTask.id
                todoTaskService.openTask(taskId)

                todoTaskService.currentTaskId shouldBe taskId
                createdTask.status shouldBe TaskStatus.OPEN

                // when
                val closedTask = todoTaskService.closeTask(null)

                // then
                closedTask.status shouldBe TaskStatus.CLOSED
                closedTask.id shouldBe taskId
                todoTaskService.currentTaskId.shouldBeNull()

                val foundTask = repository.findById(taskId)
                foundTask.shouldNotBeNull()
                foundTask.status shouldBe TaskStatus.CLOSED
            }
        }
    }

    "full workflow" - {

        "create, open, edit, close, list — complete lifecycle" {
            runTest {
                // 1. create
                val createdTask = todoTaskService.addTask("Задача 1")
                val taskId = createdTask.id
                createdTask.status shouldBe TaskStatus.OPEN

                // 2. open
                val openedTask = todoTaskService.openTask(taskId)
                todoTaskService.currentTaskId shouldBe taskId
                openedTask.id shouldBe taskId

                // 3. edit contextually
                val updatedTask = todoTaskService.editTask(null, "Обновленное название")
                updatedTask.title shouldBe "Обновленное название"
                updatedTask.id shouldBe taskId

                // 4. close contextually
                val closedTask = todoTaskService.closeTask(null)
                closedTask.status shouldBe TaskStatus.CLOSED
                closedTask.id shouldBe taskId
                todoTaskService.currentTaskId.shouldBeNull()

                // 5. list
                val allTasks = todoTaskService.listTasks()
                allTasks shouldHaveSize 1

                // 6. final state
                val finalTask = allTasks.first()
                finalTask.id shouldBe taskId
                finalTask.title shouldBe "Обновленное название"
                finalTask.status shouldBe TaskStatus.CLOSED
            }
        }
    }
})
