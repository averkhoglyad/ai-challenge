package io.averkhogliad.ai.challenge.week2.cli.handlers

import io.averkhogliad.ai.challenge.week2.application.service.LtmService
import io.averkhogliad.ai.challenge.week2.cli.CliRenderer
import io.averkhogliad.ai.challenge.week2.cli.CliState
import io.averkhogliad.ai.challenge.week2.cli.commands.Command

class LtmCommandHandler(
    private val ltmService: LtmService,
    private val renderer: CliRenderer,
) {

    suspend fun handleSaveFact(command: Command.SaveFact, state: CliState): CliState =
        handleLtmError(state) {
            val savedFact = ltmService.saveFact(command.content)
            renderer.renderFactSaved(savedFact)
            state
        }

    suspend fun handleListFacts(state: CliState): CliState =
        handleLtmError(state) {
            val facts = ltmService.listFacts()
            renderer.renderFactList(facts)
            state
        }

    suspend fun handleForgetFact(command: Command.ForgetFact, state: CliState): CliState =
        handleLtmError(state) {
            val deleted = ltmService.forgetFact(command.factId)
            if (deleted) {
                renderer.renderFactForgotten(command.factId)
            } else {
                renderer.renderFactNotFound(command.factId)
            }
            state
        }

    suspend fun handleSearchFacts(command: Command.SearchFacts, state: CliState): CliState =
        handleLtmError(state) {
            val facts = ltmService.searchFacts(command.query)
            if (facts.isEmpty()) {
                renderer.renderFactSearchEmpty(command.query)
            } else {
                renderer.renderFactSearchResults(facts, command.query)
            }
            state
        }

    private inline fun handleLtmError(state: CliState, action: () -> CliState): CliState =
        try {
            action()
        } catch (e: Exception) {
            renderer.renderError(e.message ?: UNKNOWN_ERROR_MESSAGE)
            state
        }

    companion object {
        private const val UNKNOWN_ERROR_MESSAGE = "Unknown error"
    }
}
