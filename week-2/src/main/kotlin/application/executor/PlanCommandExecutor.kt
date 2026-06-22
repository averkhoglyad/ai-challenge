package io.averkhogliad.ai.challenge.week2.application.executor

import io.averkhogliad.ai.challenge.week2.application.InvariantService
import io.averkhogliad.ai.challenge.week2.domain.Prompt
import io.averkhogliad.ai.challenge.week2.domain.TaskResult
import io.averkhogliad.ai.challenge.week2.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week2.domain.model.*
import io.averkhogliad.ai.challenge.week2.domain.service.CommandEngine
import io.averkhogliad.ai.challenge.week2.domain.service.FactRepository
import io.averkhogliad.ai.challenge.week2.domain.service.LlmPort
import io.averkhogliad.ai.challenge.week2.domain.service.TaskRepository
import java.time.Instant

/**
 * Executor для команды `:plan` — планирование шагов выполнения задачи.
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация бизнес-операции
 * - **FSM-based** — использует CommandEngine для управления состоянием
 * - **Single Responsibility** — отвечает только за планирование
 *
 * ## Этапы выполнения
 * 1. **PLANNING** — проверка открытой задачи, запрос description (если нет), сбор фактов из LTM
 * 2. **EXECUTION** — формирование промпта, запрос к LLM, парсинг ответа
 * 3. **VALIDATION** — показ шагов пользователю, ожидание подтверждения (y/n/edit)
 * 4. **DONE** — сохранение шагов в WM, завершение команды
 *
 * ## Использование
 * ```kotlin
 * val executor = PlanCommandExecutor(taskRepository, factRepository, commandEngine, llmPort)
 * executor.execute("plan", currentTaskId)
 * ```
 */
class PlanCommandExecutor(
    private val taskRepository: TaskRepository,
    private val factRepository: FactRepository,
    private val commandEngine: CommandEngine,
    private val llmPort: LlmPort? = null,
    private val invariantService: InvariantService? = null
) {

    val commandName: String = "plan"

    /**
     * Запускает команду планирования.
     *
     * @param currentTaskId ID текущей открытой задачи (null если задача не открыта)
     * @return результат операции
     */
    suspend fun execute(currentTaskId: Int?): String {
        // Проверка наличия открытой задачи
        if (currentTaskId == null) {
            return "Ошибка: команда ':plan' требует открытой задачи. Используйте ':open <taskId>' для открытия задачи."
        }

        val taskId = TaskId(currentTaskId.toString())
        val task = taskRepository.findById(taskId)
            ?: return "Ошибка: задача с ID '$currentTaskId' не найдена"

        // Проверяем, что задача открыта
        if (!task.isOpen()) {
            return "Ошибка: задача '${task.title}' не открыта. Используйте ':open $currentTaskId' для открытия задачи."
        }

        // Запускаем FSM
        commandEngine.startCommand(commandName, "Проверка задачи и сбор контекста...")
        commandEngine.putContext("taskId", currentTaskId.toString())
        commandEngine.putContext("taskTitle", task.title)

        // Проверяем наличие description
        if (!task.hasDescription()) {
            // Переходим к шагу запроса description
            commandEngine.advanceStep("Description задачи пуст. Пожалуйста, опишите задачу подробно:")
            commandEngine.putContext("needsDescription", "true")
            return "Задача '${task.title}' открыта.\n\nDescription задачи пуст. Пожалуйста, опишите задачу подробно:"
        }

        // Description есть — сохраняем его и собираем факты из LTM
        commandEngine.putContext("description", task.description ?: "")
        commandEngine.advanceStep("Сбор релевантных фактов из LTM...")

        // Загружаем инварианты и сохраняем в контекст FSM
        val invariants = invariantService?.list() ?: emptyList()
        if (invariants.isNotEmpty()) {
            val invariantsText = buildInvariantsBlock(invariants)
            commandEngine.putContext("invariants", invariantsText)
            commandEngine.putContext("invariantsCount", invariants.size.toString())
        } else {
            commandEngine.putContext("invariants", "")
            commandEngine.putContext("invariantsCount", "0")
        }

        // Собираем факты из LTM (поиск по названию задачи)
        val relevantFacts = collectRelevantFacts(task.title, task.description)

        if (relevantFacts.isNotEmpty()) {
            val factsSummary = relevantFacts.joinToString("\n") { "• ${it.content}" }
            commandEngine.putContext("relevantFacts", factsSummary)
            commandEngine.putContext("factsCount", relevantFacts.size.toString())
        } else {
            commandEngine.putContext("relevantFacts", "")
            commandEngine.putContext("factsCount", "0")
        }

        // Переходим к этапу EXECUTION
        commandEngine.advanceToStage(CommandStage.EXECUTION, "Формирование промпта для LLM...")

        return buildPlanningReadyMessage(task.title, task.description, relevantFacts, invariants.size)
    }

    /**
     * Обрабатывает ввод description на этапе PLANNING.
     *
     * @param userInput ввод пользователя (description задачи)
     * @return результат операции
     */
    suspend fun handleDescriptionInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"

        if (state.currentStage != CommandStage.PLANNING) {
            return "Ошибка: команда ':plan' не ожидает ввода description"
        }

        // Валидация ввода
        if (userInput.isBlank()) {
            return "Ошибка: description не может быть пустым. Пожалуйста, опишите задачу подробно:"
        }

        // Сохраняем description в контекст
        commandEngine.putContext("description", userInput)
        commandEngine.advanceStep("Сбор релевантных фактов из LTM...")

        // Загружаем инварианты и сохраняем в контекст FSM
        val invariants = invariantService?.list() ?: emptyList()
        if (invariants.isNotEmpty()) {
            val invariantsText = buildInvariantsBlock(invariants)
            commandEngine.putContext("invariants", invariantsText)
            commandEngine.putContext("invariantsCount", invariants.size.toString())
        } else {
            commandEngine.putContext("invariants", "")
            commandEngine.putContext("invariantsCount", "0")
        }

        // Собираем факты из LTM
        val taskTitle = commandEngine.getContext("taskTitle") ?: ""
        val relevantFacts = collectRelevantFacts(taskTitle, userInput)

        if (relevantFacts.isNotEmpty()) {
            val factsSummary = relevantFacts.joinToString("\n") { "• ${it.content}" }
            commandEngine.putContext("relevantFacts", factsSummary)
            commandEngine.putContext("factsCount", relevantFacts.size.toString())
        } else {
            commandEngine.putContext("relevantFacts", "")
            commandEngine.putContext("factsCount", "0")
        }

        // Переходим к этапу EXECUTION
        commandEngine.advanceToStage(CommandStage.EXECUTION, "Формирование промпта для LLM...")

        return buildPlanningReadyMessage(taskTitle, userInput, relevantFacts, invariants.size)
    }

    /**
     * Собирает релевантные факты из LTM на основе названия и описания задачи.
     *
     * @param taskTitle название задачи
     * @param taskDescription описание задачи (может быть null)
     * @return список релевантных фактов
     */
    private suspend fun collectRelevantFacts(taskTitle: String, taskDescription: String?): List<Fact> {
        // Извлекаем ключевые слова из названия и описания
        val keywords = extractKeywords(taskTitle, taskDescription)

        if (keywords.isEmpty()) {
            // Если ключевых слов нет, возвращаем все факты (не более 5)
            return factRepository.findAll().take(5)
        }

        // Ищем факты по каждому ключевому слову
        val allFacts = mutableSetOf<Fact>()
        for (keyword in keywords) {
            val facts = factRepository.search(keyword)
            allFacts.addAll(facts)
        }

        // Ограничиваем количество фактов
        return allFacts.toList().take(10)
    }

    /**
     * Извлекает ключевые слова из текста задачи.
     *
     * @param title название задачи
     * @param description описание задачи
     * @return список ключевых слов
     */
    private fun extractKeywords(title: String, description: String?): List<String> {
        val stopWords = setOf(
            "и", "в", "на", "с", "по", "для", "от", "до", "за", "над",
            "the", "a", "an", "in", "on", "at", "to", "for", "of", "with",
            "and", "or", "but", "is", "are", "was", "were", "be", "been",
            "implement", "create", "add", "make", "build", "write", "update"
        )

        val words = mutableListOf<String>()

        // Извлекаем из названия
        words.addAll(
            title.split(Regex("[\\s,.;:!?]+"))
            .map { it.lowercase() }
            .filter { it.length > 3 && it !in stopWords })

        // Извлекаем из описания
        if (description != null) {
            words.addAll(
                description.split(Regex("[\\s,.;:!?]+"))
                .map { it.lowercase() }
                .filter { it.length > 3 && it !in stopWords })
        }

        // Убираем дубликаты и ограничиваем количество
        return words.distinct().take(5)
    }

    /**
     * Строит сообщение о готовности к планированию.
     *
     * @param taskTitle название задачи
     * @param description описание задачи
     * @param facts список релевантных фактов
     * @return готовое сообщение
     */
    private fun buildPlanningReadyMessage(
        taskTitle: String,
        description: String?,
        facts: List<Fact>,
        invariantsCount: Int = 0
    ): String {
        val sb = StringBuilder()
        sb.appendLine("✅ Этап PLANNING завершён для задачи '$taskTitle'.")
        sb.appendLine()
        sb.appendLine("📋 Контекст собран:")
        sb.appendLine("• Description: ${description ?: "(не указан)"}")
        sb.appendLine("• Релевантных фактов из LTM: ${facts.size}")
        sb.appendLine("• Активных инвариантов: $invariantsCount")

        if (facts.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📚 Релевантные факты:")
            facts.take(5).forEach { fact ->
                sb.appendLine("  • ${fact.content}")
            }
            if (facts.size > 5) {
                sb.appendLine("  ... и ещё ${facts.size - 5} фактов")
            }
        }

        sb.appendLine()
        sb.appendLine("🚀 Переход к этапу EXECUTION: формирование промпта для LLM...")

        return sb.toString()
    }

    /**
     * Выполняет этап EXECUTION: формирует промпт, отправляет запрос к LLM, парсит ответ.
     *
     * @return результат операции с шагами или сообщение об ошибке
     */
    suspend fun executeExecution(): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"

        if (state.currentStage != CommandStage.EXECUTION) {
            return "Ошибка: команда ':plan' не на этапе EXECUTION"
        }

        // Проверяем наличие LLM
        if (llmPort == null) {
            return "Ошибка: LLM не настроен. Невозможно выполнить планирование без LLM."
        }

        // Получаем контекст из FSM
        val taskTitle = commandEngine.getContext("taskTitle") ?: ""
        val description = commandEngine.getContext("description") ?: ""
        val relevantFacts = commandEngine.getContext("relevantFacts") ?: ""
        val invariants = commandEngine.getContext("invariants") ?: ""

        // Формируем промпт для LLM
        val prompt = buildPlanningPrompt(taskTitle, description, relevantFacts, invariants)

        // Отправляем запрос к LLM
        val config = TaskExecutionConfig(
            temperature = 0.7,
            maxTokens = 2000
        )

        val result = try {
            llmPort.chat(Prompt(prompt), config)
        } catch (e: Exception) {
            // US-ROLLBACK-1: Сохраняем ошибку в контекст FSM и предлагаем откат
            commandEngine.putContext("executionError", e.message ?: "Неизвестная ошибка")
            return buildExecutionErrorMessage(e.message ?: "Неизвестная ошибка")
        }

        // Обрабатываем результат
        return when (result) {
            is TaskResult.Success -> {
                // Проверяем, не содержит ли ответ отказ из-за инвариантов
                if (invariants.isNotEmpty() && isInvariantRefusal(result.content)) {
                    // Конфликт с инвариантами — завершаем FSM с ошибкой
                    commandEngine.completeCommand()
                    val conflictingRule = extractConflictingRule(result.content)
                    buildInvariantConflictMessage(taskTitle, conflictingRule)
                } else {
                    val steps = parseStepsFromLlmResponse(result.content)
                    if (steps.isEmpty()) {
                        // US-ROLLBACK-1: Пустой ответ LLM — сохраняем ошибку и предлагаем откат
                        commandEngine.putContext("executionError", "LLM не вернула список шагов")
                        buildExecutionErrorMessage("LLM не вернула список шагов")
                    } else {
                        // Сохраняем шаги в контекст FSM
                        val stepsJson = steps.joinToString("\n") { it }
                        commandEngine.putContext("generatedSteps", stepsJson)
                        commandEngine.putContext("stepsCount", steps.size.toString())

                        // Переходим к этапу VALIDATION
                        commandEngine.advanceToStage(CommandStage.VALIDATION, "Ожидание подтверждения пользователя...")

                        buildValidationMessage(steps)
                    }
                }
            }

            is TaskResult.Error -> {
                "Ошибка LLM: ${result.message}"
            }

            is TaskResult.Partial -> {
                "Ошибка: получен частичный результат от LLM"
            }
        }
    }

    /**
     * Формирует промпт для LLM для генерации шагов планирования.
     *
     * @param taskTitle название задачи
     * @param description описание задачи
     * @param relevantFacts релевантные факты из LTM
     * @param invariantsText текстовый блок инвариантов (может быть пустым)
     * @return промпт для LLM
     */
    private fun buildPlanningPrompt(
        taskTitle: String,
        description: String,
        relevantFacts: String,
        invariantsText: String = ""
    ): String {
        return buildString {
            // Блок инвариантов — всегда первый, выше всех инструкций
            if (invariantsText.isNotEmpty()) {
                appendLine(invariantsText)
                appendLine()
            }

            appendLine("Ты — помощник по планированию задач. Твоя задача — разбить задачу на конкретные выполнимые шаги.")
            appendLine()

            // Правила обработки инвариантов при планировании
            if (invariantsText.isNotEmpty()) {
                appendLine("=== ПРАВИЛА ОБРАБОТКИ ИНВАРИАНТОВ ПРИ ПЛАНИРОВАНИИ ===")
                appendLine("ЖЁСТКИЕ ПРАВИЛА (ИНВАРИАНТЫ) — это ограничения, которые ты НЕ ИМЕЕШЬ ПРАВА НАРУШАТЬ ни при каких обстоятельствах.")
                appendLine()
                appendLine("Перед генерацией шагов ВСЕГДА проверяй:")
                appendLine("1. Противоречит ли САМА ЗАДАЧА какому-либо инварианту?")
                appendLine("2. Если задача САМА ПО СЕБЕ нарушает инвариант (например, «Миграция на MongoDB» при инварианте «Только PostgreSQL»):")
                appendLine("   — НЕ генерируй шаги для этой задачи")
                appendLine("   — Вместо списка шагов напиши ТОЛЬКО сообщение об отказе в формате:")
                appendLine("     ❌ Нарушение инварианта: [укажи нарушенный инвариант]")
                appendLine("     Задача «[название]» противоречит инварианту: [процитируй правило].")
                appendLine("     💡 Альтернатива: [предложи разрешённую альтернативу]")
                appendLine("3. Если задача НЕ нарушает инварианты — генерируй шаги как обычно, но не предлагай шагов, нарушающих инварианты.")
                appendLine()
            }

            appendLine("## Задача:")
            appendLine("Название: $taskTitle")
            appendLine("Описание: $description")
            appendLine()
            if (relevantFacts.isNotEmpty()) {
                appendLine("## Релевантные факты из базы знаний:")
                appendLine(relevantFacts)
                appendLine()
            }
            appendLine("## Инструкция:")
            appendLine("Создай список конкретных шагов для выполнения этой задачи. Каждый шаг должен быть:")
            appendLine("- Конкретным и выполнимым")
            appendLine("- Атомарным (одно действие на шаг)")
            appendLine("- Понятным без дополнительного контекста")
            appendLine()
            appendLine("Формат ответа: пронумерованный список, каждый шаг с новой строки.")
            appendLine("Пример:")
            appendLine("1. Первый шаг")
            appendLine("2. Второй шаг")
            appendLine("3. Третий шаг")
            appendLine()

            if (invariantsText.isNotEmpty()) {
                appendLine("ВАЖНО: Если задача сама по себе нарушает инварианты — НЕ генерируй шаги, а верни только отказ с ❌ и 💡.")
                appendLine()
            }

            appendLine("Сгенерируй список шагов:")
        }
    }

    /**
     * Парсит ответ LLM в список шагов.
     *
     * @param response ответ от LLM
     * @return список шагов (строки)
     */
    private fun parseStepsFromLlmResponse(response: String): List<String> {
        val steps = mutableListOf<String>()
        val lines = response.lines()

        for (line in lines) {
            val trimmed = line.trim()
            // Пропускаем пустые строки
            if (trimmed.isEmpty()) continue

            // Ищем строки, начинающиеся с цифры и точки (1., 2., etc.)
            val match = Regex("""^\d+[\.\)]\s*(.+)""").find(trimmed)
            if (match != null) {
                val stepText = match.groupValues[1].trim()
                if (stepText.isNotEmpty()) {
                    steps.add(stepText)
                }
            } else if (trimmed.startsWith("-") || trimmed.startsWith("*")) {
                // Альтернативный формат: списки с - или *
                val stepText = trimmed.removePrefix("-").removePrefix("*").trim()
                if (stepText.isNotEmpty()) {
                    steps.add(stepText)
                }
            }
        }

        return steps
    }

    /**
     * Строит сообщение для этапа VALIDATION с списком шагов.
     *
     * @param steps список шагов
     * @return сообщение для пользователя
     */
    private fun buildValidationMessage(steps: List<String>): String {
        val sb = StringBuilder()
        sb.appendLine("✅ Этап EXECUTION завершён. LLM сгенерировала ${steps.size} шагов.")
        sb.appendLine()
        sb.appendLine("📋 Предлагаемые шаги:")
        steps.forEachIndexed { index, step ->
            sb.appendLine("${index + 1}. $step")
        }
        sb.appendLine()
        sb.appendLine("🔍 Переход к этапу VALIDATION.")
        sb.appendLine("Подтвердите план (y), отмените (n) или отредактируйте (edit):")

        return sb.toString()
    }

    /**
     * Обрабатывает ввод пользователя на этапе VALIDATION.
     *
     * Поддерживаемые команды:
     * - `y` / `yes` — подтвердить план и перейти к этапу DONE
     * - `n` / `no` — отменить планирование и завершить команду
     * - `edit` — перейти в режим редактирования шагов
     *
     * @param userInput ввод пользователя (y/n/edit)
     * @return результат операции
     */
    suspend fun handleValidationInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"

        if (state.currentStage != CommandStage.VALIDATION) {
            return "Ошибка: команда ':plan' не на этапе VALIDATION"
        }

        val normalizedInput = userInput.trim().lowercase()

        return when (normalizedInput) {
            "y", "yes" -> handleValidationConfirm()
            "n", "no" -> handleValidationCancel()
            "edit" -> handleValidationEdit()
            else -> {
                val sb = StringBuilder()
                sb.appendLine("❌ Неизвестная команда: '$userInput'")
                sb.appendLine()
                sb.appendLine("Доступные команды:")
                sb.appendLine("  y     — подтвердить план")
                sb.appendLine("  n     — отменить планирование")
                sb.appendLine("  edit  — редактировать шаги")
                sb.appendLine()
                sb.appendLine("Пожалуйста, введите y, n или edit:")
                sb.toString()
            }
        }
    }

    /**
     * Обрабатывает подтверждение плана (y/yes).
     * Переходит к этапу DONE для сохранения шагов.
     *
     * @return результат операции
     */
    private suspend fun handleValidationConfirm(): String {
        // Получаем шаги из контекста
        val stepsJson = commandEngine.getContext("generatedSteps")
            ?: return "Ошибка: шаги не найдены в контексте"

        val steps = stepsJson.split("\n").filter { it.isNotBlank() }

        if (steps.isEmpty()) {
            return "Ошибка: список шагов пуст"
        }

        // Переходим к этапу DONE
        commandEngine.advanceToStage(CommandStage.DONE, "Сохранение шагов в рабочую память...")

        return executeDone(steps)
    }

    /**
     * Обрабатывает отмену планирования (n/no).
     * Завершает команду без сохранения шагов.
     *
     * @return результат операции
     */
    private fun handleValidationCancel(): String {
        commandEngine.completeCommand()
        return "❌ Планирование отменено. Шаги не были сохранены."
    }

    /**
     * Обрабатывает запрос на редактирование шагов (edit).
     * Переводит пользователя в режим ввода изменённых шагов.
     *
     * @return результат операции с инструкциями по редактированию
     */
    private fun handleValidationEdit(): String {
        val stepsJson = commandEngine.getContext("generatedSteps")
            ?: return "Ошибка: шаги не найдены в контексте"

        val steps = stepsJson.split("\n").filter { it.isNotBlank() }

        val sb = StringBuilder()
        sb.appendLine("📝 Режим редактирования шагов.")
        sb.appendLine()
        sb.appendLine("Текущие шаги:")
        steps.forEachIndexed { index, step ->
            sb.appendLine("${index + 1}. $step")
        }
        sb.appendLine()
        sb.appendLine("Введите новые шаги в том же формате (каждый шаг с новой строки):")
        sb.appendLine("• Пронумерованный список: 1. ..., 2. ..., 3. ...")
        sb.appendLine("• Или маркированный список: - ..., - ..., - ...")
        sb.appendLine()
        sb.appendLine("Для отмены редактирования введите 'cancel'.")
        sb.appendLine("Для подтверждения текущих шагов введите 'done'.")

        // Устанавливаем флаг режима редактирования
        commandEngine.putContext("editMode", "true")

        return sb.toString()
    }

    /**
     * Обрабатывает ввод пользователя на этапе VALIDATION.
     * Автоматически определяет, вызвать ли handleValidationInput() или handleEditInput(),
     * в зависимости от состояния FSM (editMode).
     *
     * @param userInput ввод пользователя
     * @return результат операции
     */
    suspend fun handleUserInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"

        return when (state.currentStage) {
            CommandStage.PLANNING -> {
                // На этапе PLANNING — обрабатываем ввод description
                handleDescriptionInput(userInput)
            }

            CommandStage.EXECUTION -> {
                // На этапе EXECUTION — запускаем выполнение
                executeExecution()
            }

            CommandStage.VALIDATION -> {
                val editMode = commandEngine.getContext("editMode")
                if (editMode == "true") {
                    handleEditInput(userInput)
                } else {
                    handleValidationInput(userInput)
                }
            }

            CommandStage.DONE -> {
                "Ошибка: команда ':plan' уже завершена"
            }

            CommandStage.TERMINATED -> {
                "Ошибка: команда ':plan' была прервана (TERMINATED)"
            }
        }
    }

    /**
     * Обрабатывает ввод новых шагов в режиме редактирования.
     *
     * @param userInput ввод пользователя (список шагов)
     * @return результат операции
     */
    suspend fun handleEditInput(userInput: String): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"

        if (state.currentStage != CommandStage.VALIDATION) {
            return "Ошибка: команда ':plan' не на этапе VALIDATION"
        }

        val editMode = commandEngine.getContext("editMode")
        if (editMode != "true") {
            return "Ошибка: режим редактирования не активен"
        }

        val normalizedInput = userInput.trim().lowercase()

        // Отмена редактирования
        if (normalizedInput == "cancel") {
            commandEngine.putContext("editMode", "false")
            val stepsJson = commandEngine.getContext("generatedSteps") ?: ""
            val steps = stepsJson.split("\n").filter { it.isNotBlank() }
            return buildValidationMessage(steps)
        }

        // Подтверждение редактирования (используем текущие шаги)
        if (normalizedInput == "done") {
            commandEngine.putContext("editMode", "false")
            return handleValidationConfirm()
        }

        // Парсим новые шаги из ввода
        val newSteps = parseStepsFromLlmResponse(userInput)

        if (newSteps.isEmpty()) {
            return "Ошибка: не удалось распознать шаги. Попробуйте ещё раз или введите 'cancel' для отмены."
        }

        // Сохраняем новые шаги в контекст
        val newStepsJson = newSteps.joinToString("\n") { it }
        commandEngine.putContext("generatedSteps", newStepsJson)
        commandEngine.putContext("stepsCount", newSteps.size.toString())
        commandEngine.putContext("editMode", "false")

        // Показываем обновлённые шаги для подтверждения
        return buildValidationMessage(newSteps)
    }

    /**
     * Формирует текстовый блок [INVARIANTS] для вставки в промпт.
     *
     * @param invariants список инвариантов
     * @return текстовая строка блока инвариантов
     */
    private fun buildInvariantsBlock(invariants: List<Invariant>): String {
        val sb = StringBuilder()
        sb.appendLine("[INVARIANTS - DO NOT VIOLATE]")
        invariants.forEachIndexed { index, inv ->
            sb.appendLine("${index + 1}. ${inv.rule}")
        }
        return sb.toString()
    }

    /**
     * Проверяет, содержит ли ответ LLM отказ из-за инвариантов.
     * Распознаёт маркер ❌ в ответе.
     *
     * @param response ответ от LLM
     * @return true, если ответ содержит отказ по инвариантам
     */
    private fun isInvariantRefusal(response: String): Boolean {
        return response.contains("❌") && (
                response.contains("Нарушение инварианта") ||
                        response.contains("нарушение инварианта") ||
                        response.contains("противоречит инварианту")
                )
    }

    /**
     * Извлекает текст нарушенного инварианта из ответа LLM с отказом.
     *
     * @param response ответ от LLM с отказом
     * @return текст нарушенного правила или "неизвестный инвариант"
     */
    private fun extractConflictingRule(response: String): String {
        // Ищем строку после "инвариант:" или "инварианту:"
        val patterns = listOf(
            Regex("""инварианту:\s*(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE),
            Regex("""инвариант:\s*(.+?)(?:\.|$)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(response)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return "неизвестный инвариант"
    }

    /**
     * Строит сообщение о конфликте задачи с инвариантами.
     *
     * @param taskTitle название задачи
     * @param conflictingRule текст конфликтующего инварианта
     * @return сообщение для пользователя
     */
    private fun buildInvariantConflictMessage(taskTitle: String, conflictingRule: String): String {
        val sb = StringBuilder()
        sb.appendLine("⚠️ Обнаружен конфликт с инвариантом!")
        sb.appendLine()
        sb.appendLine("Задача «$taskTitle» противоречит инварианту:")
        sb.appendLine("\"$conflictingRule\"")
        sb.appendLine()
        sb.appendLine("Я не могу составить план для этой задачи, пока инвариант активен.")
        sb.appendLine()
        sb.appendLine("Варианты:")
        sb.appendLine("1. Изменить задачу через :edit или :describe")
        sb.appendLine("2. Удалить инвариант через :invariant remove <id>")
        return sb.toString()
    }

    /**
     * Строит сообщение об ошибке выполнения с информацией о возможности отката.
     *
     * US-ROLLBACK-1: Сообщает пользователю о доступном откате в PLANNING.
     *
     * @param errorMessage сообщение об ошибке
     * @return сообщение для пользователя
     */
    private fun buildExecutionErrorMessage(errorMessage: String): String {
        val sb = StringBuilder()
        sb.appendLine("Ошибка: $errorMessage")
        sb.appendLine()
        sb.appendLine("Доступные действия:")
        sb.appendLine("  :goto PLANNING  — откатиться на этап планирования (контекст сохранится)")
        sb.appendLine("  :goto           — посмотреть карту состояний")
        sb.appendLine("  :abort          — прервать команду")
        return sb.toString()
    }

    /**
     * Выполняет этап DONE: сохраняет шаги в рабочую память и завершает команду.
     *
     * @param steps список шагов для сохранения
     * @return результат операции
     */
    private suspend fun executeDone(steps: List<String>): String {
        val state = commandEngine.getActiveState()
            ?: return "Ошибка: команда ':plan' не активна"

        if (state.currentStage != CommandStage.DONE) {
            return "Ошибка: команда ':plan' не на этапе DONE"
        }

        // Получаем ID задачи из контекста
        val taskIdStr = commandEngine.getContext("taskId")
            ?: return "Ошибка: ID задачи не найден в контексте"

        val taskTitle = commandEngine.getContext("taskTitle") ?: "задачи"
        val taskId = TaskId(taskIdStr)

        // Создаём TaskStep объекты
        val taskSteps = steps.mapIndexed { index, stepText ->
            TaskStep(
                id = TaskStepId("step-${index + 1}"),
                taskId = taskId,
                text = stepText,
                isCompleted = false,
                order = index + 1,
                createdAt = Instant.now()
            )
        }

        // Сохраняем шаги в TaskRepository
        try {
            taskRepository.saveSteps(taskId, taskSteps)
        } catch (e: Exception) {
            return "Ошибка при сохранении шагов: ${e.message}"
        }

        // Сохраняем шаги в контекст для последующей обработки в CliApplication
        commandEngine.putContext("finalSteps", steps.joinToString("\n") { it })
        commandEngine.putContext("stepsCount", steps.size.toString())

        // Завершаем команду
        commandEngine.completeCommand()

        val sb = StringBuilder()
        sb.appendLine("✅ Этап DONE завершён.")
        sb.appendLine()
        sb.appendLine("📋 План для '$taskTitle' сохранён:")
        steps.forEachIndexed { index, step ->
            sb.appendLine("  ${index + 1}. $step")
        }
        sb.appendLine()
        sb.appendLine("💾 Шаги сохранены в базу данных. Всего шагов: ${steps.size}")
        sb.appendLine("🎯 Команда ':plan' завершена успешно.")

        return sb.toString()
    }
}
