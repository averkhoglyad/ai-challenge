package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.*
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import java.time.Instant
import java.util.*

/**
 * Стратегия Branching (Ветвление).
 *
 * Создаёт точки сохранения (чекпоинты) в диалоге и позволяет создавать
 * независимые ветки от этих точек. Поддерживает переключение между ветками.
 *
 * ## Принцип работы
 * 1. Автоматически создаёт чекпоинты каждые N сообщений
 * 2. Позволяет вручную создавать чекпоинты через команду
 * 3. Позволяет создавать ветки от любого чекпоинта
 * 4. Позволяет переключаться между ветками
 *
 * ## Преимущества
 * - Возможность исследовать разные направления диалога
 * - Сохранение важных точек контекста
 * - Гибкость управления историей
 *
 * ## Недостатки
 * - Сложность реализации
 * - Требует управления состоянием веток
 *
 * @property configProvider провайдер конфигурации ветвления
 */
class BranchingStrategy(
    private val configProvider: () -> BranchingConfig
) : ContextManagementStrategy {

    override val name: String = "Branching"
    override val description: String = "Создаёт точки сохранения и ветки диалога. " +
            "Позволяет исследовать разные направления."

    // Текущая активная ветка
    private var currentBranch: DialogBranch = DialogBranch.createMain(DialogId("temp"))

    // История чекпоинтов
    private val checkpoints = mutableListOf<Checkpoint>()

    // Все ветки диалога
    private val branches = mutableMapOf<BranchId, DialogBranch>()

    init {
        // Инициализируем главную ветку
        branches[currentBranch.id] = currentBranch
    }

    override suspend fun processUserMessage(
        dialog: Dialog,
        userMessage: String,
        config: ContextManagementConfig
    ): StrategyActionResult {
        val branchingConfig = config.branching
        val messageIndex = dialog.messages.size

        // Проверяем, нужно ли создать автоматический чекпоинт
        val shouldCreateCheckpoint = messageIndex > 0 &&
                messageIndex % branchingConfig.checkpointInterval == 0

        val actions = mutableListOf<StrategyAction>()

        if (shouldCreateCheckpoint) {
            val checkpoint = createCheckpoint(dialog, messageIndex)
            actions.add(StrategyAction.CheckpointCreated(checkpoint.id.value))
        }

        // Добавляем сообщение в текущую ветку
        val userMsg = ChatMessage.user(userMessage)
        currentBranch = currentBranch.addMessage(userMsg)
        branches[currentBranch.id] = currentBranch

        return StrategyActionResult(
            actionsPerformed = actions,
            metadata = mapOf(
                "currentBranch" to currentBranch.name,
                "totalBranches" to branches.size,
                "totalCheckpoints" to checkpoints.size
            )
        )
    }

    override suspend fun prepareContext(
        dialog: Dialog,
        systemPrompt: String,
        config: ContextManagementConfig
    ): PreparedContext {
        // Используем сообщения из текущей ветки
        val messages = mutableListOf<ChatMessage>()

        // System prompt
        messages.add(ChatMessage.system(systemPrompt))

        // Сообщения из текущей ветки
        messages.addAll(currentBranch.messages)

        return PreparedContext.fromMessages(
            messages = messages,
            metadata = mapOf(
                "strategy" to "branching",
                "currentBranch" to currentBranch.name,
                "branchMessageCount" to currentBranch.messages.size,
                "totalBranches" to branches.size,
                "totalCheckpoints" to checkpoints.size
            )
        )
    }

    /**
     * Создаёт чекпоинт от текущего состояния диалога.
     */
    fun createCheckpoint(dialog: Dialog, messageIndex: Int): Checkpoint {
        val checkpoint = Checkpoint(
            id = CheckpointId(UUID.randomUUID().toString()),
            dialogId = dialog.id,
            messageIndex = messageIndex,
            messagesSnapshot = currentBranch.messages.toList(),
            factsSnapshot = currentBranch.factsStore.facts.mapValues { it.value.value },
            createdAt = Instant.now()
        )
        checkpoints.add(checkpoint)
        return checkpoint
    }

    /**
     * Создаёт новую ветку от указанного чекпоинта.
     */
    fun createBranch(name: String, checkpointId: CheckpointId): DialogBranch {
        val checkpoint = checkpoints.find { it.id == checkpointId }
            ?: throw IllegalArgumentException("Checkpoint not found: ${checkpointId.value}")

        val newBranch = DialogBranch.createFromCheckpoint(
            id = BranchId(UUID.randomUUID().toString()),
            name = name,
            dialogId = checkpoint.dialogId,
            checkpoint = checkpoint
        )

        branches[newBranch.id] = newBranch
        return newBranch
    }

    /**
     * Переключается на указанную ветку.
     */
    fun switchBranch(branchId: BranchId): DialogBranch {
        val branch = branches[branchId]
            ?: throw IllegalArgumentException("Branch not found: ${branchId.value}")

        // Деактивируем текущую ветку
        currentBranch = currentBranch.deactivate()
        branches[currentBranch.id] = currentBranch

        // Активируем новую ветку
        currentBranch = branch.activate()
        branches[currentBranch.id] = currentBranch

        return currentBranch
    }

    /**
     * Получает список всех веток.
     */
    fun listBranches(): List<DialogBranch> = branches.values.toList()

    /**
     * Получает список всех чекпоинтов.
     */
    fun listCheckpoints(): List<Checkpoint> = checkpoints.toList()

    /**
     * Получает текущую активную ветку.
     */
    fun getCurrentBranch(): DialogBranch = currentBranch

    /**
     * Удаляет ветку (кроме главной).
     */
    fun deleteBranch(branchId: BranchId): Boolean {
        if (branchId == BranchId("main")) {
            return false // Нельзя удалить главную ветку
        }

        if (currentBranch.id == branchId) {
            return false // Нельзя удалить текущую активную ветку
        }

        branches.remove(branchId)
        return true
    }
}
