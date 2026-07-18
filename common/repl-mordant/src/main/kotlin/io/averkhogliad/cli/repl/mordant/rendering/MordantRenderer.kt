package io.averkhogliad.cli.repl.mordant.rendering

import com.github.ajalt.mordant.terminal.Terminal
import io.averkhogliad.cli.repl.core.Renderer

abstract class MordantRenderer<in T>(
    protected val terminal: Terminal
) : Renderer<T>
