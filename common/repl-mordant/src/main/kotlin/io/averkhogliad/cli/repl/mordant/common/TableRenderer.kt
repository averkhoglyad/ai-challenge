package io.averkhogliad.cli.repl.mordant.common

import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.cli.repl.mordant.rendering.MordantRenderer

abstract class TableRenderer<T>(
    terminal: Terminal
) : MordantRenderer<T>(terminal) {

    abstract fun headers(): List<String>

    abstract fun rows(data: T): List<List<String>>

    override fun render(data: T): String {
        val renderedTable = table {
            header {
                row(*headers().toTypedArray())
            }
            body {
                rows(data).forEach { rowData ->
                    row(*rowData.toTypedArray())
                }
            }
        }
        return terminal.render(renderedTable)
    }
}
