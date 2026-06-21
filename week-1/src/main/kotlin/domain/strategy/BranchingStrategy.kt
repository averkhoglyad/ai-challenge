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
 * ## Управление состоянием
 * Состояние стратегии может передаваться извне через параметр [state] в методах
 * [processUserMessage] и [prepareContext]. Если `state == null`, используется
 * внутреннее состояние для обратной совместимости.
 * Обновлённое состояние всегда возвращается в метаданных результата под ключом
 * [StrategyMetadataKeys.STRATEGY_STATE].
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
 * @property topicChangeDetector детектор смены темы диалога
 */
class BranchingStrategy(
    private val configProvider: () -> BranchingConfig,
    private val topicChangeDetector: TopicChangeDetector = TopicChangeDetector()
) : ContextManagementStrategy {

    override val name: String = "Branching"
    override val description: String = "Создаёт точки сохранения и ветки диалога. " +
            "Позволяет исследовать разные направления."

    // Внутреннее состояние — сохранено для обратной совместимости.
    // При использовании параметра state в processUserMessage/prepareContext
    // внутреннее состояние синхронизируется с переданным.
    private var currentBranch: DialogBranch = DialogBranch.createMain(DialogId("temp"))
    private val checkpoints = mutableListOf<Checkpoint>()
    private val branches = mutableMapOf<BranchId, DialogBranch>()

    init {
        branches[currentBranch.id] = currentBranch
    }

    // ═══════════════════════════════════════════════════════════════
    // processUserMessage
    // ═══════════════════════════════════════════════════════════════

    override suspend fun processUserMessage(
        dialog: Dialog,
        userMessage: String,
        config: ContextManagementConfig,
        state: StrategyState?
    ): StrategyActionResult {
        require(userMessage.isNotBlank()) { "userMessage cannot be blank" }

        val branchingConfig = config.branching

        // Извлекаем состояние: приоритет — переданный state, fallback — внутреннее состояние
        val currentState = extractOrCreateState(state, dialog.id)
        val actions = mutableListOf<StrategyAction>()
        var updatedState = currentState

        // 1. Авто-детект смены темы через TopicChangeDetector
        if (branchingConfig.autoDetectTopicChange && updatedState.currentBranch.messages.size >= 2) {
            val topicChanged = topicChangeDetector.detectTopicChange(
                userMessage = userMessage,
                recentMessages = updatedState.currentBranch.messages,
                sensitivity = branchingConfig.topicChangeSensitivity,
                contextSize = branchingConfig.topicContextSize
            )
            if (topicChanged) {
                val checkpoint = doCreateCheckpoint(updatedState, dialog, dialog.messages.size)
                updatedState = updatedState.copy(
                    checkpoints = updatedState.checkpoints + checkpoint
                )
                actions.add(StrategyAction.CheckpointCreated(checkpoint.id.value))

                val autoBranchName = "auto-branch-${updatedState.branches.size}"
                val newBranch = doCreateBranch(updatedState, autoBranchName, checkpoint.id)
                updatedState = updatedState.copy(
                    branches = updatedState.branches + (newBranch.id to newBranch),
                    currentBranch = newBranch.activate()
                )
                actions.add(StrategyAction.BranchCreated(autoBranchName))
                actions.add(StrategyAction.BranchSwitched(autoBranchName))
            }
        }

        // 2. Периодический чекпоинт
        val messageIndex = dialog.messages.size
        val shouldCreatePeriodicCheckpoint = messageIndex > 0 &&
                messageIndex % branchingConfig.checkpointInterval == 0
        val alreadyCreatedCheckpoint = actions.any { it is StrategyAction.CheckpointCreated }

        if (shouldCreatePeriodicCheckpoint && !alreadyCreatedCheckpoint) {
            val checkpoint = doCreateCheckpoint(updatedState, dialog, messageIndex)
            updatedState = updatedState.copy(
                checkpoints = updatedState.checkpoints + checkpoint
            )
            actions.add(StrategyAction.CheckpointCreated(checkpoint.id.value))
        }

        // 3. Добавляем сообщение в текущую ветку
        val userMsg = ChatMessage.user(userMessage)
        val updatedBranch = updatedState.currentBranch.addMessage(userMsg)
        updatedState = updatedState.copy(
            currentBranch = updatedBranch,
            branches = updatedState.branches + (updatedBranch.id to updatedBranch)
        )

        // Синхронизируем внутреннее состояние для обратной совместимости
        syncInternalState(updatedState)

        return StrategyActionResult(
            actionsPerformed = actions,
            metadata = mapOf(
                StrategyMetadataKeys.CURRENT_BRANCH to updatedState.currentBranch.name,
                StrategyMetadataKeys.TOTAL_BRANCHES to updatedState.branches.size,
                StrategyMetadataKeys.TOTAL_CHECKPOINTS to updatedState.checkpoints.size,
                StrategyMetadataKeys.STRATEGY_STATE to updatedState
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // prepareContext
    // ═══════════════════════════════════════════════════════════════

    override suspend fun prepareContext(
        dialog: Dialog,
        systemPrompt: String,
        config: ContextManagementConfig,
        state: StrategyState?
    ): PreparedContext {
        val currentState = extractOrCreateState(state, dialog.id)

        val messages = mutableListOf<ChatMessage>()
        messages.add(ChatMessage.system(systemPrompt))
        messages.addAll(currentState.currentBranch.messages)

        return PreparedContext.fromMessages(
            messages = messages,
            metadata = mapOf(
                StrategyMetadataKeys.STRATEGY to "branching",
                StrategyMetadataKeys.CURRENT_BRANCH to currentState.currentBranch.name,
                StrategyMetadataKeys.BRANCH_MESSAGE_COUNT to currentState.currentBranch.messages.size,
                StrategyMetadataKeys.TOTAL_BRANCHES to currentState.branches.size,
                StrategyMetadataKeys.TOTAL_CHECKPOINTS to currentState.checkpoints.size,
                StrategyMetadataKeys.STRATEGY_STATE to currentState
            )
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Публичные методы — обратная совместимость (без state параметра)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Создаёт чекпоинт от текущего состояния диалога.
     */
    fun createCheckpoint(dialog: Dialog, messageIndex: Int): Checkpoint {
        val currentState = buildInternalState(dialog.id)
        val checkpoint = doCreateCheckpoint(currentState, dialog, messageIndex)
        checkpoints.add(checkpoint)
        return checkpoint
    }

    /**
     * Создаёт новую ветку от указанного чекпоинта.
     */
    fun createBranch(name: String, checkpointId: CheckpointId): DialogBranch {
        val currentState = buildInternalState(DialogId("temp"))
        val newBranch = doCreateBranch(currentState, name, checkpointId)
        branches[newBranch.id] = newBranch
        return newBranch
    }

    /**
     * Переключается на указанную ветку.
     */
    fun switchBranch(branchId: BranchId): DialogBranch {
        val currentState = buildInternalState(DialogId("temp"))
        val updatedState = doSwitchBranch(currentState, branchId)
        syncInternalState(updatedState)
        return updatedState.currentBranch
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
     * Удаляет ветку (кроме главной и текущей активной).
     */
    fun deleteBranch(branchId: BranchId): Boolean {
        if (branchId == BranchId("main")) return false
        if (currentBranch.id == branchId) return false
        return branches.remove(branchId) != null
    }

    // ═══════════════════════════════════════════════════════════════
    // Приватные stateless-методы (работают с переданным состоянием)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Создаёт чекпоинт (stateless-версия).
     */
    private fun doCreateCheckpoint(
        state: StrategyState.BranchingState,
        dialog: Dialog,
        messageIndex: Int
    ): Checkpoint {
        return Checkpoint(
            id = CheckpointId(UUID.randomUUID().toString()),
            dialogId = dialog.id,
            messageIndex = messageIndex,
            messagesSnapshot = state.currentBranch.messages.toList(),
            factsSnapshot = state.currentBranch.factsStore.facts.mapValues { it.value.value },
            createdAt = Instant.now()
        )
    }

    /**
     * Создаёт новую ветку от чекпоинта (stateless-версия).
     */
    private fun doCreateBranch(
        state: StrategyState.BranchingState,
        name: String,
        checkpointId: CheckpointId
    ): DialogBranch {
        val checkpoint = state.checkpoints.find { it.id == checkpointId }
            ?: throw IllegalArgumentException("Checkpoint not found: ${checkpointId.value}")

        return DialogBranch.createFromCheckpoint(
            id = BranchId(UUID.randomUUID().toString()),
            name = name,
            dialogId = checkpoint.dialogId,
            checkpoint = checkpoint
        )
    }

    /**
     * Переключает активную ветку (stateless-версия).
     */
    private fun doSwitchBranch(
        state: StrategyState.BranchingState,
        branchId: BranchId
    ): StrategyState.BranchingState {
        val branch = state.branches[branchId]
            ?: throw IllegalArgumentException("Branch not found: ${branchId.value}")

        val deactivatedCurrent = state.currentBranch.deactivate()
        val activatedBranch = branch.activate()

        val updatedBranches = state.branches +
                (deactivatedCurrent.id to deactivatedCurrent) +
                (activatedBranch.id to activatedBranch)

        return state.copy(
            currentBranch = activatedBranch,
            branches = updatedBranches
        )
    }

    // ═══════════════════════════════════════════════════════════════
    // Вспомогательные методы
    // ═══════════════════════════════════════════════════════════════

    /**
     * Извлекает или создаёт состояние стратегии.
     */
    private fun extractOrCreateState(
        state: StrategyState?,
        dialogId: DialogId
    ): StrategyState.BranchingState {
        return (state as? StrategyState.BranchingState) ?: buildInternalState(dialogId)
    }

    /**
     * Строит состояние на основе внутренних mutable-полей (для обратной совместимости).
     */
    private fun buildInternalState(dialogId: DialogId): StrategyState.BranchingState {
        if (currentBranch.dialogId.value == "temp" && currentBranch.dialogId != dialogId) {
            return StrategyState.BranchingState.createInitial(dialogId)
        }
        return StrategyState.BranchingState(
            currentBranch = currentBranch,
            checkpoints = checkpoints.toList(),
            branches = branches.toMap()
        )
    }

    /**
     * Синхронизирует внутреннее mutable-состояние с переданным (для обратной совместимости).
     */
    private fun syncInternalState(state: StrategyState.BranchingState) {
        currentBranch = state.currentBranch
        checkpoints.clear()
        checkpoints.addAll(state.checkpoints)
        branches.clear()
        branches.putAll(state.branches)
    }
}
