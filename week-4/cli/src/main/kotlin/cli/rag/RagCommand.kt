package io.averkhogliad.ai.challenge.week4.cli.cli.rag

/**
 * Типизированное представление RAG-команд.
 *
 * Sealed interface обеспечивает исчерпывающую обработку в when-выражениях.
 */
sealed interface RagCommand {
    /** Переключить RAG on/off (toggle) */
    data object Toggle : RagCommand

    /** Показать текущее состояние RAG */
    data object Status : RagCommand

    /** Показать список доступных индексов */
    data object List : RagCommand
}
