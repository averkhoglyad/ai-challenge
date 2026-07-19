package io.averkhogliad.ai.challenge.week6.domain.port

import io.averkhogliad.ai.challenge.week6.domain.error.DomainResult

interface AppStateRepository {
    suspend fun getValue(key: String): DomainResult<String?>
    suspend fun setValue(key: String, value: String): DomainResult<Unit>
    suspend fun removeKey(key: String): DomainResult<Unit>
}
