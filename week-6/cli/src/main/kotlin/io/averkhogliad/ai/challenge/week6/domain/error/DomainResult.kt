package io.averkhogliad.ai.challenge.week6.domain.error

sealed class DomainResult<out T> {
    data class Success<T>(val value: T) : DomainResult<T>()
    data class Failure(val error: DomainError) : DomainResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure

    fun getOrNull(): T? = when (this) {
        is Success -> value
        is Failure -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw IllegalStateException(error.message)
    }

    @Suppress("UNCHECKED_CAST")
    fun <R> map(transform: (T) -> R): DomainResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this as DomainResult<R>
    }

    fun getOrElse(default: (DomainError) -> @UnsafeVariance T): T = when (this) {
        is Success -> value
        is Failure -> default(error)
    }
}

fun <T> DomainResult<T>.asFailure(): DomainResult<Nothing> {
    check(this is DomainResult.Failure) { "Expected failure, got success" }
    return DomainResult.Failure((this as DomainResult.Failure).error)
}
