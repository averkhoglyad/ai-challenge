package io.averkhogliad.cli.repl.core

interface Renderer<in T> {
    fun render(data: T): String
}
