package io.averkhogliad.cli.repl.mordant.common

import com.github.ajalt.mordant.rendering.TextColors
import com.github.ajalt.mordant.rendering.TextStyles
import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.cli.repl.core.DomainError
import io.averkhogliad.cli.repl.mordant.rendering.MordantRenderer

class ErrorMessageRenderer(
    terminal: Terminal
) : MordantRenderer<DomainError>(terminal) {

    override fun render(data: DomainError): String {
        val icon = TextColors.red("\u2717")
        val message = TextStyles.bold(data.message)
        return "$icon $message"
    }
}
