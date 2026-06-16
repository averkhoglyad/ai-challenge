package io.averkhogliad.ai.challenge.week1.infrastructure.persistence

import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import io.averkhogliad.ai.challenge.week1.domain.model.DialogSummary
import io.averkhogliad.ai.challenge.week1.domain.service.DialogRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory реализация [DialogRepository].
 *
 * Хранит диалоги в потокобезопасной [ConcurrentHashMap].
 * Используется для Task 1 — все диалоги живут только в памяти
 * и не персистируются между запусками приложения.
 *
 * ## Архитектурные решения
 * - **In-memory** — нет persistence, идеально для stateless Task 1
 * - **Thread-safe** — [ConcurrentHashMap] гарантирует безопасность при параллельном доступе
 * - **Легковесная** — минимальный overhead, подходит для демонстрации
 *
 * ## Отличие от [SqliteDialogRepository]
 * - **SqliteDialogRepository** — персистентное хранение (Task 2+)
 * - **InMemoryDialogRepository** — временное хранение (только Task 1)
 */
class InMemoryDialogRepository : DialogRepository {

    private val dialogs = ConcurrentHashMap<String, Dialog>()

    override suspend fun save(dialog: Dialog) {
        dialogs[dialog.id.value] = dialog
    }

    override suspend fun findById(id: DialogId): Dialog? {
        return dialogs[id.value]
    }

    override suspend fun findAll(): List<DialogSummary> {
        return dialogs.values
            .sortedByDescending { it.updatedAt }
            .map { DialogSummary.fromDialog(it) }
    }

    override suspend fun delete(id: DialogId) {
        dialogs.remove(id.value)
    }
}
