package io.averkhogliad.ai.challenge.week2.it

import io.averkhogliad.ai.challenge.week2.application.DialogService
import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.application.ProfileService
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.SessionLevel
import io.averkhogliad.ai.challenge.week2.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week2.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week2.domain.service.MemoryService
import io.averkhogliad.ai.challenge.week2.domain.service.PromptBuilder
import io.averkhogliad.ai.challenge.week2.infrastructure.persistence.*
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.io.File
import java.nio.file.Files

/**
 * Интеграционные тесты для сквозного потока: профиль → PromptBuilder → DialogService → промпт LLM.
 *
 * Проверяет end-to-end взаимодействие:
 * - [SqliteProfileRepository] + [ProfileService] — управление профилями (настоящая БД)
 * - [SqliteDialogSessionRepository] + [MemoryService] — хранение сессий (настоящая БД)
 * - [DialogService] + [PromptBuilder] — формирование промпта с профилем
 * - MockK для [LlmPort] — единственная внешняя зависимость
 */
class ProfilePromptIT : FreeSpec({

    lateinit var tempDbFile: File
    lateinit var database: SqliteDatabase
    lateinit var profileRepository: SqliteProfileRepository
    lateinit var profileService: ProfileService
    lateinit var sessionRepository: SqliteDialogSessionRepository
    lateinit var memoryService: MemoryService
    lateinit var promptBuilder: PromptBuilder
    lateinit var invariantService: InvariantService

    beforeEach {
        tempDbFile = Files.createTempFile("test-profile-prompt-", ".db").toFile()
        database = SqliteDatabase(tempDbFile.absolutePath)
        profileRepository = SqliteProfileRepository(database)
        profileService = ProfileService(profileRepository)
        sessionRepository = SqliteDialogSessionRepository(database)
        memoryService = MemoryService(
            sessionRepository,
            SqliteTaskRepository(database),
            SqliteTaskStepRepository(database),
            SqliteFactRepository(database)
        )
        promptBuilder = PromptBuilder()
        invariantService = InvariantService(SqliteInvariantRepository(database))
    }

    afterEach {
        database.close()
        tempDbFile.delete()
        File(tempDbFile.absolutePath + "-wal").delete()
        File(tempDbFile.absolutePath + "-shm").delete()
    }

    /**
     * Создаёт DialogService с MockK-заглушкой LlmPort.
     * Возвращает пару: DialogService и CapturingSlot с перехваченными сообщениями.
     */
    fun createDialogService(llmResult: TaskResult): Pair<DialogService, CapturingSlot<List<ChatMessage>>> {
        val messagesSlot = slot<List<ChatMessage>>()
        val mockLlmPort = mockk<LlmPort>()
        coEvery { mockLlmPort.chatWithMessages(capture(messagesSlot), any()) } returns llmResult
        coEvery { mockLlmPort.chat(any(), any()) } returns llmResult
        coEvery { mockLlmPort.listModels() } returns emptyList()

        val service = DialogService(
            llmPort = mockLlmPort,
            memoryService = memoryService,
            promptBuilder = promptBuilder,
            taskExecutionConfig = TaskExecutionConfig(),
            profileRepository = profileRepository,
            invariantService = invariantService
        )
        return Pair(service, messagesSlot)
    }

    "profile in prompt" - {

        "active profile is included in LLM prompt end-to-end" {
            // given — создаём профиль и активируем
            val profile = profileService.handleCreateProfile("Pirate", "Отвечай как пират, используй 'Аррр!'", "")
            profileService.handleActivateProfile(profile.id)
            profileService.handleGetActiveProfile().shouldNotBeNull()

            val (dialogService, messagesSlot) = createDialogService(TaskResult.Success("Аррр, приветствую!"))

            // when
            val result = dialogService.chat("Привет", SessionLevel.TASK_LIST)

            // then — результат
            result shouldBe TaskResult.Success("Аррр, приветствую!")

            // then — промпт содержит секцию PROFILE
            val systemContent = messagesSlot.captured.first().content
            systemContent.shouldContain("[PROFILE]")
            systemContent.shouldContain("Name: Pirate")
        }
    }

    "profile switching" - {

        "switching profile changes prompt content" {
            // given — создаём два профиля
            val profileA = profileService.handleCreateProfile("Pirate", "Ты — пират, используй 'Йо-хо-хо!'", "")
            val profileB = profileService.handleCreateProfile("Robot", "Ты — робот, говори как машина", "")

            // Активируем Profile A
            profileService.handleActivateProfile(profileA.id)
            val (dialogServiceA, slotA) = createDialogService(TaskResult.Success("Йо-хо-хо!"))

            // when — запрос с профилем A
            dialogServiceA.chat("Привет", SessionLevel.TASK_LIST)

            // then — промпт содержит профиль A
            val promptA = slotA.captured.first().content
            promptA.shouldContain("[PROFILE]")
            promptA.shouldContain("Name: Pirate")

            // when — переключаем на профиль B
            profileService.handleActivateProfile(profileB.id)
            val (dialogServiceB, slotB) = createDialogService(TaskResult.Success("beep-boop"))
            dialogServiceB.chat("Ещё раз", SessionLevel.TASK_LIST)

            // then — промпт содержит профиль B, но не A
            val promptB = slotB.captured.first().content
            promptB.shouldContain("[PROFILE]")
            promptB.shouldContain("Name: Robot")
            promptB.contains("Name: Pirate").shouldBeFalse()
        }
    }

    "delete inactive" - {

        "deleting inactive profile does not remove PROFILE section" {
            // given — создаём два профиля
            val firstProfile = profileService.handleCreateProfile("TempProfile", "Временный контекст для теста", "")
            val secondProfile = profileService.handleCreateProfile("SecondProfile", "Второй профиль", "")

            // Активируем первый
            profileService.handleActivateProfile(firstProfile.id)
            val (dialogService1, slot1) = createDialogService(TaskResult.Success("Ok"))
            dialogService1.chat("Тест", SessionLevel.TASK_LIST)

            val promptBefore = slot1.captured.first().content
            promptBefore.shouldContain("[PROFILE]")
            promptBefore.shouldContain("Name: TempProfile")

            // when — переключаемся на второй и удаляем первый
            profileService.handleActivateProfile(secondProfile.id)
            profileService.handleDeleteProfile("TempProfile")

            // then — PROFILE секция всё ещё есть (активен второй)
            val (dialogService2, slot2) = createDialogService(TaskResult.Success("Ok again"))
            dialogService2.chat("Тест после удаления", SessionLevel.TASK_LIST)

            val promptAfter = slot2.captured.first().content
            promptAfter.shouldContain("[PROFILE]")
            promptAfter.shouldContain("Name: SecondProfile")
        }
    }

    "empty profile" - {

        "profile with empty description and instructions is handled correctly" {
            // given
            val profile = profileService.handleCreateProfile("MinimalProfile", "Минимальное содержимое профиля", "")
            profileService.handleActivateProfile(profile.id)
            profileService.handleGetActiveProfile().shouldNotBeNull()
            profile.description shouldBe "Минимальное содержимое профиля"
            profile.instructions shouldBe ""

            val (dialogService, messagesSlot) = createDialogService(TaskResult.Success("Ответ на запрос"))

            // when
            val result = dialogService.chat("Запрос с минимальным профилем", SessionLevel.TASK_LIST)

            // then
            result shouldBe TaskResult.Success("Ответ на запрос")
            val systemContent = messagesSlot.captured.first().content

            systemContent.shouldContain("[PROFILE]")
            systemContent.shouldContain("Name: MinimalProfile")
            systemContent.shouldContain("Description: ")
            systemContent.shouldContain("Instructions: ")
            systemContent.shouldContain(PromptBuilder.SYSTEM_INSTRUCTION)
        }
    }
})
