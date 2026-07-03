package io.averkhogliad.ai.challenge.week4.cli.infrastructure.tool

import io.averkhogliad.ai.challenge.week4.cli.domain.model.Task

/**
 * Сериализует задачу в JSON-строку для передачи LLM.
 */
fun Task.toJson(): String = buildString {
    appendLine("{")
    appendLine("""  "id": "$id",""")
    appendLine("""  "title": "${title.escapeJson()}",""")
    append("""  "description": """)
    if (description != null) append("\"${description.escapeJson()}\"") else append("null")
    appendLine(",")
    appendLine("""  "status": "$status",""")
    append("""  "eventId": """)
    if (eventId != null) append("\"$eventId\"") else append("null")
    appendLine(",")
    append("""  "dueDate": """)
    if (dueDate != null) append("\"$dueDate\"") else append("null")
    appendLine(",")
    appendLine("""  "createdAt": "$createdAt",""")
    appendLine("""  "updatedAt": "$updatedAt"""")
    appendLine("}")
}

internal fun String.escapeJson(): String = this
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r")
    .replace("\t", "\\t")
