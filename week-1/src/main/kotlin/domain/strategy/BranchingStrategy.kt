package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.*
import io.averkhogliad.ai.challenge.week1.domain.service.ChatMessage
import io.averkhogliad.ai.challenge.week1.domain.service.ChatRole
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

        val actions = mutableListOf<StrategyAction>()

        // 1. Авто-детект смены темы (если включен и есть достаточно истории)
        if (branchingConfig.autoDetectTopicChange && currentBranch.messages.size >= 2) {
            val topicChanged = detectTopicChange(
                userMessage = userMessage,
                recentMessages = currentBranch.messages,
                sensitivity = branchingConfig.topicChangeSensitivity,
                contextSize = branchingConfig.topicContextSize
            )
            if (topicChanged) {
                // Создаём чекпоинт и авто-ветку
                val checkpoint = createCheckpoint(dialog, messageIndex)
                actions.add(StrategyAction.CheckpointCreated(checkpoint.id.value))

                val autoBranchName = "auto-branch-${branches.size}"
                val newBranch = createBranch(autoBranchName, checkpoint.id)
                // Переключаемся на новую ветку
                switchBranch(newBranch.id)
                actions.add(StrategyAction.BranchCreated(autoBranchName))
                actions.add(StrategyAction.BranchSwitched(autoBranchName))
            }
        }

        // 2. Периодический чекпоинт (каждые N сообщений)
        val shouldCreatePeriodicCheckpoint = messageIndex > 0 &&
                messageIndex % branchingConfig.checkpointInterval == 0
        // Не создаём периодический чекпоинт, если только что создали от смены темы
        val alreadyCreatedCheckpoint = actions.any { it is StrategyAction.CheckpointCreated }

        if (shouldCreatePeriodicCheckpoint && !alreadyCreatedCheckpoint) {
            val checkpoint = createCheckpoint(dialog, messageIndex)
            actions.add(StrategyAction.CheckpointCreated(checkpoint.id.value))
        }

        // 3. Добавляем сообщение в текущую ветку
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

    // ═══════════════════════════════════════════════════════════════
    // Topic change detection (авто-детект смены темы)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Определяет, изменилась ли тема диалога.
     *
     * Алгоритм: извлекает значимые ключевые слова из N последних сообщений
     * и из нового сообщения, затем сравнивает пересечение множеств.
     * Если доля пересечения ниже [sensitivity] — тема считается изменившейся.
     *
     * @param userMessage новое сообщение пользователя
     * @param recentMessages последние сообщения в текущей ветке
     * @param sensitivity порог чувствительности (0.0–1.0). Чем ниже — тем легче срабатывает.
     * @param contextSize сколько последних сообщений анализировать
     * @return true если тема изменилась
     */
    private fun detectTopicChange(
        userMessage: String,
        recentMessages: List<ChatMessage>,
        sensitivity: Double,
        contextSize: Int
    ): Boolean {
        // Берём последние contextSize сообщений пользователя (не system/assistant)
        val recentUserMessages = recentMessages
            .filter { it.role == ChatRole.USER }
            .takeLast(contextSize)
            .map { it.content }

        if (recentUserMessages.isEmpty()) return false

        // Извлекаем ключевые слова из истории
        val recentKeywords = recentUserMessages
            .flatMap { extractKeywords(it) }
            .toSet()

        // Извлекаем ключевые слова из нового сообщения
        val newKeywords = extractKeywords(userMessage).toSet()

        if (recentKeywords.isEmpty() || newKeywords.isEmpty()) return false

        // Вычисляем коэффициент Жаккара (Jaccard similarity)
        val intersection = recentKeywords.intersect(newKeywords).size
        val union = recentKeywords.union(newKeywords).size
        val jaccard = intersection.toDouble() / union.toDouble()

        // Смена темы = низкое пересечение
        return jaccard < sensitivity
    }

    /**
     * Извлекает значимые ключевые слова из сообщения.
     *
     * - Приводит к нижнему регистру
     * - Удаляет стоп-слова (предлоги, союзы, артикли)
     * - Отбрасывает короткие слова (< 3 символов)
     */
    private fun extractKeywords(text: String): List<String> {
        val stopWords = setOf(
            // Русские стоп-слова
            "и", "в", "на", "с", "по", "к", "из", "от", "для", "не", "то", "что",
            "как", "это", "так", "а", "но", "о", "же", "за", "бы", "у", "до",
            "да", "нет", "или", "если", "мы", "вы", "ты", "он", "она", "они",
            "мне", "меня", "его", "её", "им", "их", "вам", "вас", "тебе", "тебя",
            "всё", "все", "ещё", "уже", "там", "тут", "где", "кто", "когда",
            "можно", "надо", "нужно", "очень", "более", "также", "только",
            "который", "которая", "которое", "которые", "быть", "будет",
            "есть", "был", "была", "было", "были", "чем", "того", "этом", "этого",
            "под", "над", "при", "без", "через", "перед", "между", "около",
            // Английские стоп-слова
            "the", "a", "an", "is", "are", "was", "were", "be", "been", "being",
            "have", "has", "had", "do", "does", "did", "will", "would", "could",
            "should", "may", "might", "can", "shall", "to", "of", "in", "for",
            "on", "with", "at", "by", "from", "as", "into", "through", "during",
            "before", "after", "above", "below", "between", "out", "off", "over",
            "under", "again", "further", "then", "once", "here", "there", "when",
            "where", "why", "how", "all", "both", "each", "few", "more", "most",
            "other", "some", "such", "no", "nor", "not", "only", "own", "same",
            "so", "than", "too", "very", "just", "because", "but", "and", "or",
            "if", "while", "it", "its", "my", "your", "his", "her", "our", "their",
            "me", "him", "us", "them", "this", "that", "these", "those", "what",
            "which", "who", "whom", "about", "up", "down", "any", "let", "need",
            "now", "also", "much", "well", "still", "new"
        )

        return text.lowercase()
            .replace(Regex("[^a-zа-яё0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in stopWords }
    }
}
