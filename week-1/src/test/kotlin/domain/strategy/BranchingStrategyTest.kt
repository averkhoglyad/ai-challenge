package io.averkhogliad.ai.challenge.week1.domain.strategy

import io.averkhogliad.ai.challenge.week1.domain.model.BranchId
import io.averkhogliad.ai.challenge.week1.domain.model.Dialog
import io.averkhogliad.ai.challenge.week1.domain.model.DialogId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BranchingStrategyTest {

    private val strategy = BranchingStrategy { BranchingConfig() }

    private fun createDialogWithMessages(count: Int): Dialog {
        var dialog = Dialog.create(DialogId("test-dialog"), "Test Dialog")
        repeat(count) { i ->
            dialog = dialog.addUserMessage("Message ${i + 1}")
        }
        return dialog
    }

    @Test
    fun `should have correct name and description`() {
        assertEquals("Branching", strategy.name)
        assertTrue(strategy.description.contains("точки сохранения"))
    }

    @Test
    fun `processUserMessage should add message to current branch`() = runTest {
        val dialog = createDialogWithMessages(3)
        val config = ContextManagementConfig()

        val result = strategy.processUserMessage(dialog, "New message", config)

        // Should have message in current branch
        val currentBranch = strategy.getCurrentBranch()
        assertTrue(currentBranch.messages.isNotEmpty())
    }

    @Test
    fun `prepareContext should return messages from current branch`() = runTest {
        val dialog = createDialogWithMessages(5)
        val config = ContextManagementConfig()

        // Process some messages
        strategy.processUserMessage(dialog, "Message 1", config)
        strategy.processUserMessage(dialog, "Message 2", config)

        val context = strategy.prepareContext(dialog, "System prompt", config)

        // System prompt + branch messages
        assertTrue(context.messages.size >= 1)
        assertEquals("System prompt", context.messages[0].content)
    }

    @Test
    fun `createCheckpoint should create new checkpoint`() {
        val dialog = createDialogWithMessages(5)

        val checkpoint = strategy.createCheckpoint(dialog, 5)

        assertNotNull(checkpoint)
        assertEquals(5, checkpoint.messageIndex)
        assertEquals(dialog.id, checkpoint.dialogId)
    }

    @Test
    fun `listCheckpoints should return all checkpoints`() {
        val dialog = createDialogWithMessages(10)

        strategy.createCheckpoint(dialog, 5)
        strategy.createCheckpoint(dialog, 10)

        val checkpoints = strategy.listCheckpoints()
        assertEquals(2, checkpoints.size)
    }

    @Test
    fun `createBranch should create new branch from checkpoint`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)

        val newBranch = strategy.createBranch("test-branch", checkpoint.id)

        assertNotNull(newBranch)
        assertEquals("test-branch", newBranch.name)
        assertEquals(checkpoint.id, newBranch.parentCheckpointId)
    }

    @Test
    fun `switchBranch should change current branch`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        val newBranch = strategy.createBranch("test-branch", checkpoint.id)

        strategy.switchBranch(newBranch.id)

        assertEquals(newBranch.id, strategy.getCurrentBranch().id)
        assertTrue(strategy.getCurrentBranch().isActive)
    }

    @Test
    fun `switchBranch should throw exception for unknown branch`() {
        val unknownId = BranchId("unknown-branch")

        assertThrows<IllegalArgumentException> {
            strategy.switchBranch(unknownId)
        }
    }

    @Test
    fun `listBranches should return all branches`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        strategy.createBranch("branch-1", checkpoint.id)
        strategy.createBranch("branch-2", checkpoint.id)

        val branches = strategy.listBranches()

        // Main branch + 2 created branches
        assertTrue(branches.size >= 3)
    }

    @Test
    fun `deleteBranch should remove branch`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        val newBranch = strategy.createBranch("to-delete", checkpoint.id)

        val result = strategy.deleteBranch(newBranch.id)

        assertTrue(result)
        assertTrue(strategy.listBranches().none { it.id == newBranch.id })
    }

    @Test
    fun `deleteBranch should not delete main branch`() {
        val result = strategy.deleteBranch(BranchId("main"))

        assertTrue(!result)
    }

    @Test
    fun `deleteBranch should not delete current active branch`() {
        val dialog = createDialogWithMessages(5)
        val checkpoint = strategy.createCheckpoint(dialog, 5)
        val newBranch = strategy.createBranch("active-branch", checkpoint.id)
        strategy.switchBranch(newBranch.id)

        val result = strategy.deleteBranch(newBranch.id)

        assertTrue(!result)
    }

    @Test
    fun `prepareContext metadata should contain strategy info`() = runTest {
        val dialog = createDialogWithMessages(5)
        val config = ContextManagementConfig()

        val context = strategy.prepareContext(dialog, "System prompt", config)

        assertEquals("branching", context.metadata["strategy"])
        assertNotNull(context.metadata["currentBranch"])
        assertNotNull(context.metadata["totalBranches"])
        assertNotNull(context.metadata["totalCheckpoints"])
    }
}
