package io.averkhogliad.cli.repl

import io.averkhogliad.cli.repl.core.CommandEffect
import io.averkhogliad.cli.repl.engine.QuitHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class QuitHandlerTest {

    @Test
    fun `quit returns Exit effect`() = runTest {
        val handler = QuitHandler()
        val effect = handler.execute("/quit")
        assertEquals(CommandEffect.Exit, effect)
    }
}
