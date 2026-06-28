package io.averkhogliad.ai.challenge.week3.cli.application.cache

import io.averkhogliad.ai.challenge.week3.cli.application.InvariantService
import io.averkhogliad.ai.challenge.week3.cli.domain.model.Invariant
import io.averkhogliad.ai.challenge.week3.cli.domain.service.InvariantRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Декоратор [InvariantService], добавляющий кэширование результатов [list].
 *
 * ## Архитектурная роль
 * - **Application Layer** — decorator над [InvariantService]
 * - Наследует [InvariantService] для сохранения совместимости типов
 *
 * ## Стратегия кэширования
 * - **Cache-Aside**: результаты [list] кэшируются при первом вызове
 * - **Инвалидация при записи**: кэш сбрасывается при [add] и успешном [remove]
 * - **Потокобезопасность**: [Mutex] + [@Volatile] для корректной работы в многопоточной среде
 * - [count] не кэшируется (операция O(1))
 *
 * ## Производительность
 * - Первый вызов [list] выполняет запрос к БД и кэширует результат
 * - Последующие вызовы [list] возвращают кэшированные данные без обращения к БД
 * - [add] и [remove] инвалидируют кэш, следующий [list] загружает свежие данные
 *
 * @param invariantRepository репозиторий инвариантов (пробрасывается в [InvariantService])
 */
class CachingInvariantService(
    invariantRepository: InvariantRepository
) : InvariantService(invariantRepository) {
    private val cacheMutex = Mutex()

    @Volatile
    private var cachedList: List<Invariant>? = null

    @Volatile
    private var cacheValid: Boolean = false

    /**
     * Добавляет новый инвариант и инвалидирует кэш.
     *
     * @param rule текст правила
     * @return сохранённый инвариант с присвоенным ID
     */
    override suspend fun add(rule: String): Invariant {
        val result = super.add(rule)
        invalidateCache()
        return result
    }

    /**
     * Возвращает список всех инвариантов с кэшированием.
     *
     * При первом вызове или после инвалидации кэша выполняет запрос к БД.
     * Последующие вызовы возвращают кэшированную копию.
     */
    override suspend fun list(): List<Invariant> {
        // Быстрая проверка без блокировки
        val snapshot = cachedList
        if (snapshot != null && cacheValid) {
            return snapshot
        }

        // Медленный путь — берём блокировку и перепроверяем
        return cacheMutex.withLock {
            val doubleCheck = cachedList
            if (doubleCheck != null && cacheValid) {
                return@withLock doubleCheck
            }
            val fresh = super.list()
            cachedList = fresh
            cacheValid = true
            fresh
        }
    }

    /**
     * Удаляет инвариант по ID и инвалидирует кэш при успешном удалении.
     *
     * @param id идентификатор инварианта
     * @return true, если инвариант был удалён
     */
    override suspend fun remove(id: Int): Boolean {
        val result = super.remove(id)
        if (result) {
            invalidateCache()
        }
        return result
    }

    /**
     * Возвращает количество инвариантов (без кэширования — операция O(1)).
     */
    override suspend fun count(): Int {
        return super.count()
    }

    /**
     * Инвалидирует кэш. Вызывается после мутирующих операций.
     *
     * Не требует блокировки, так как запись в [@Volatile] поле атомарна,
     * а восстановление кэша защищено [Mutex] в [list].
     */
    private fun invalidateCache() {
        cacheValid = false
    }
}
