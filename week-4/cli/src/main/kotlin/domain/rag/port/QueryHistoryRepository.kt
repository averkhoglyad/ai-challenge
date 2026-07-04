package io.averkhogliad.ai.challenge.week4.cli.domain.rag.port

import io.averkhogliad.ai.challenge.week4.cli.domain.rag.model.QueryHistoryEntry

/**
 * Порт репозитория истории RAG-запросов.
 *
 * Определяет контракт между application-слоем (QueryHistoryService) и
 * infrastructure-слоем (SqliteQueryHistoryRepository).
 *
 * Принцип инверсии зависимостей (DIP): domain определяет интерфейс,
 * infrastructure его реализует.
 *
 * Все методы suspend для поддержки корутин.
 */
interface QueryHistoryRepository {

    /**
     * Сохраняет новую запись в историю.
     *
     * @param entry запись для сохранения
     * @return автоинкрементный ID сохранённой записи
     */
    suspend fun save(entry: QueryHistoryEntry): Long

    /**
     * Возвращает последние N записей, отсортированные по убыванию timestamp.
     *
     * @param limit максимальное количество возвращаемых записей
     */
    suspend fun getLast(limit: Int): List<QueryHistoryEntry>

    /**
     * Возвращает запись по ID или null, если запись не найдена.
     */
    suspend fun getById(id: Long): QueryHistoryEntry?

    /**
     * Удаляет все записи из истории.
     */
    suspend fun deleteAll()

    /**
     * Возвращает общее количество записей в истории.
     */
    suspend fun count(): Int
}
