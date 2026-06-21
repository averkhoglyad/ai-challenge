package io.averkhogliad.ai.challenge.week2.domain

/**
 * Метаданные учебной задачи.
 *
 * Описывает задачу, не завися от её реализации или системы UI.
 * Используется для построения меню, справки и навигации.
 *
 * @property id уникальный идентификатор задачи
 * @property title человекочитаемое название задачи
 * @property description описание того, что делает задача
 * @property availableCommands список специфичных команд, доступных внутри задачи
 */
data class TaskMetadata(
    val id: TaskId,
    val title: String,
    val description: String,
    val availableCommands: List<String> = emptyList()
)
