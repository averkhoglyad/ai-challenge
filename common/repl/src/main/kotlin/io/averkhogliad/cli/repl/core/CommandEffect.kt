package io.averkhogliad.cli.repl.core

import kotlinx.coroutines.flow.Flow

sealed interface CommandEffect {
    data class Print(val message: String, val isError: Boolean = false) : CommandEffect
    data class Navigate(val targetContextName: String) : CommandEffect
    data object GoBack : CommandEffect
    data object Exit : CommandEffect
    data object None : CommandEffect
    data class StreamOutput(val contentFlow: Flow<String>) : CommandEffect

    data class Confirm(
        val message: String,
        val onConfirm: suspend () -> CommandEffect,
        val onCancel: suspend () -> CommandEffect = { None }
    ) : CommandEffect

    data class EnterMultilineMode(
        val prompt: String,
        val onComplete: suspend (String) -> CommandEffect
    ) : CommandEffect

    data class DisplayDomainError<T : DomainError>(val error: T) : CommandEffect
    data class DisplaySystemError(
        val message: String,
        val cause: Throwable? = null
    ) : CommandEffect
}
