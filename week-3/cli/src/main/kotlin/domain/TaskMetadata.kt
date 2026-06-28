package io.averkhogliad.ai.challenge.week3.cli.domain

import io.averkhogliad.ai.challenge.week3.cli.domain.model.TaskId

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
