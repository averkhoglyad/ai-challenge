package io.averkhogliad.ai.challenge.week4.cli.application.chat

import io.averkhogliad.ai.challenge.week4.cli.application.rag.RagQueryProcessor
import io.averkhogliad.ai.challenge.week4.cli.domain.config.TaskExecutionConfig
import io.averkhogliad.ai.challenge.week4.cli.domain.model.*
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagAnswer
import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.RagSessionState
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatNameGenerator
import io.averkhogliad.ai.challenge.week4.cli.domain.service.ChatSessionRepository
import io.averkhogliad.ai.challenge.week4.cli.domain.service.TaskStateExtractor
import java.time.Instant
import java.util.*

// ═══════════════════════════════════════════════════════════════════════════════
// Модель результата
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Результат одного хода диалога в [ChatExecutor].
 *
 * @property session обновлённая чат-сессия после обработки хода
 * @property answer текст ответа ассистента
 * @property citations цитаты из источников (если RAG был успешен)
 * @property stateDelta дельта изменений TaskState (null, если извлечение не выполнялось или упало)
 * @property taskStateUpdated был ли TaskState фактически изменён в этом ходе
 */
data class ChatResult(
    val session: ChatSession,
    val answer: String,
    val citations: List<ChatSource>,
    val stateDelta: TaskStateDelta?,
    val taskStateUpdated: Boolean,
    val saveSucceeded: Boolean = true
)

// ═══════════════════════════════════════════════════════════════════════════════
// ChatExecutor
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Оркестратор одного хода диалога — pipeline с двумя LLM-вызовами.
 *
 * ## Pipeline
 * ```
 * UserInput → загрузить чат → TaskStateExtractor (LLM #1: дельта) →
 * применить дельту к TaskState → ChatPromptBuilder → RagQueryProcessor (LLM #2: ответ) →
 * сохранить → ChatResult
 * ```
 *
 * ## Graceful Degradation
 * - **Extractor упал** → пропускаем обновление памяти, продолжаем с текущим TaskState
 * - **NameGenerator упал** → оставляем текущее имя (автоименование не срабатывает)
 * - **RAG упал** → возвращаем fallback-ответ ("Я не знаю")
 *
 * ## Автоименование
 * После первого обмена (user + assistant), если имя не сгенерировано и
 * [ChatConfig.autoNameEnabled] — вызывается [ChatNameGenerator].
 *
 * ## Архитектурная роль
 * - **Application Layer** — оркестрация, координация портов
 * - **Не зависит** от UI и infrastructure
 * - **Зависит только** от domain-портов и моделей
 *
 * @property taskStateExtractor порт извлечения дельты TaskState (LLM #1)
 * @property ragQueryProcessor существующий RAG-процессор (LLM #2)
 * @property chatSessionRepository порт персистентности чат-сессий
 * @property chatPromptBuilder построитель итогового промпта
 * @property chatNameGenerator порт автоименования чатов
 * @property config конфигурация чата
 */
class ChatExecutor(
    private val taskStateExtractor: TaskStateExtractor,
    private val ragQueryProcessor: RagQueryProcessor,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatSessionManager: ChatSessionManager,
    private val chatPromptBuilder: ChatPromptBuilder,
    private val chatNameGenerator: ChatNameGenerator,
    private val config: ChatConfig
) {
    companion object {
        private const val TAG = "ChatExecutor"
    }

    /**
     * Выполняет полный pipeline обработки одного пользовательского сообщения.
     *
     * @param userInput текст сообщения пользователя
     * @param sessionId идентификатор текущей чат-сессии
     * @param executionConfig конфигурация выполнения (temperature, maxTokens, modelId)
     * @param ragState состояние RAG-сессии (включён ли RAG, topK, пороги)
     * @return [ChatResult] с ответом, цитатами и обновлённой сессией
     */
    suspend fun execute(
        userInput: String,
        sessionId: UUID,
        executionConfig: TaskExecutionConfig,
        ragState: RagSessionState = RagSessionState()
    ): ChatResult {
        // 1. Загрузить сессию
        val session = loadSession(sessionId)
        val userMessage = ChatMessage.User(
            id = UUID.randomUUID(),
            sessionId = sessionId,
            text = userInput,
            createdAt = Instant.now()
        )
        var currentSession = session.addMessage(userMessage)

        // 2. Если extraction enabled → вызвать TaskStateExtractor
        var stateDelta: TaskStateDelta? = null
        var taskStateUpdated = false

        if (config.taskStateExtractionEnabled) {
            val extractionResult = runCatching {
                taskStateExtractor.extract(currentSession.taskState, listOf(userMessage))
            }

            extractionResult
                .onSuccess { result ->
                    result.onSuccess { delta ->
                        stateDelta = delta
                        if (delta !is TaskStateDelta.NoChanges) {
                            val newState = currentSession.taskState.applyDelta(
                                delta,
                                maxTerms = config.taskStateMaxTerms,
                                maxConstraints = config.taskStateMaxConstraints,
                                maxClarifiedFacts = config.maxClarifiedFacts
                            )
                            currentSession = currentSession.updateTaskState(newState)
                            taskStateUpdated = true
                            System.err.println("[$TAG] TaskState updated: delta=$delta")
                        }
                    }.onFailure { error ->
                        System.err.println("[$TAG] TaskStateExtractor returned error: ${error.message}")
                    }
                }
                .onFailure { error ->
                    System.err.println("[$TAG] TaskStateExtractor failed (graceful degradation): ${error.message}")
                }
        }

        // 3. Построить промпт через ChatPromptBuilder
        val history = currentSession.getRecentMessages()
        val prompt = chatPromptBuilder.build(
            taskState = currentSession.taskState,
            history = history,
            ragContext = emptyList(), // RAG-контекст строится внутри RagQueryProcessor
            question = userInput
        )

        // 4. Вызвать RagQueryProcessor
        val ragAnswer: RagAnswer = try {
            ragQueryProcessor.process(
                question = prompt,
                ragState = ragState,
                config = executionConfig
            )
        } catch (e: Exception) {
            System.err.println("[$TAG] RagQueryProcessor failed: ${e.message}")
            RagAnswer(
                answer = "Я не знаю",
                ragEnabled = ragState.enabled,
                fallbackToPlain = true
            )
        }

        val answerText = ragAnswer.answer
        val chatSources = ragAnswer.citations.mapIndexed { index, citation ->
            ChatSource(
                citationNumber = index + 1,
                documentId = citation.chunkId,
                documentName = citation.source,
                relevance = citation.relevanceScore
            )
        }

        // 5. Добавить assistant сообщение
        val assistantMessage = ChatMessage.Assistant(
            id = UUID.randomUUID(),
            sessionId = sessionId,
            text = answerText,
            citations = ragAnswer.citations.mapIndexed { index, _ -> index + 1 },
            sources = chatSources,
            createdAt = Instant.now()
        )
        currentSession = currentSession.addMessage(assistantMessage)

        // 6. Автоименование после первого обмена
        currentSession = chatSessionManager.maybeAutoName(currentSession)

        // 7. Сохранить сессию
        val saveSucceeded = saveSession(currentSession)

        return ChatResult(
            session = currentSession,
            answer = answerText,
            citations = chatSources,
            stateDelta = stateDelta,
            taskStateUpdated = taskStateUpdated,
            saveSucceeded = saveSucceeded
        )
    }

    /**
     * Загружает сессию по идентификатору.
     */
    private suspend fun loadSession(sessionId: UUID): ChatSession {
        val result = chatSessionRepository.loadById(sessionId)
        return result
            .onFailure { error ->
                System.err.println("[$TAG] Failed to load session $sessionId: ${error.message}")
            }
            .getOrThrow()
            ?: throw IllegalStateException("Chat session not found: $sessionId")
    }

    /**
     * Сохраняет сессию. Возвращает true при успехе, false при ошибке.
     */
    private suspend fun saveSession(session: ChatSession): Boolean {
        return chatSessionRepository.save(session)
            .onFailure { error ->
                System.err.println("[$TAG] Failed to save session ${session.metadata.id}: ${error.message}")
            }
            .isSuccess
    }
}
