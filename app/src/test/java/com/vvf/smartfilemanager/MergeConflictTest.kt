package com.vvf.smartfilemanager

import com.vvf.smartfilemanager.data.*
import org.junit.Assert.*
import org.junit.Test

class MergeConflictTest {

    @Test
    fun testInitialConflictsLoading() {
        val files = listOf(
            ConflictFile(
                id = "conf_1",
                name = "Theme.kt",
                path = "app/src/main/java/com/vvf/smartfilemanager/ui/theme/Theme.kt",
                blocks = listOf(
                    ConflictBlock(
                        id = "block_1",
                        lineStart = 15,
                        currentCode = "Purple80",
                        incomingCode = "ColorSkywalker",
                        description = "Brand colors"
                    )
                )
            )
        )

        assertEquals(1, files.size)
        val firstFile = files[0]
        assertEquals("Theme.kt", firstFile.name)
        assertFalse(firstFile.isFullyResolved)
        assertNull(firstFile.blocks[0].resolutionChoice)
    }

    @Test
    fun testResolveConflictOurs() {
        val file = ConflictFile(
            id = "conf_1",
            name = "Theme.kt",
            path = "app/src/main/java/com/vvf/smartfilemanager/ui/theme/Theme.kt",
            blocks = listOf(
                ConflictBlock(
                    id = "block_1",
                    lineStart = 15,
                    currentCode = "Purple80",
                    incomingCode = "ColorSkywalker",
                    description = "Brand colors"
                )
            )
        )

        val updatedBlocks = file.blocks.map { block ->
            if (block.id == "block_1") {
                block.copy(resolutionChoice = "ours", resolvedCode = block.currentCode)
            } else block
        }
        val updatedFile = file.copy(blocks = updatedBlocks, isFullyResolved = updatedBlocks.all { it.resolutionChoice != null })

        assertTrue(updatedFile.isFullyResolved)
        assertEquals("ours", updatedFile.blocks[0].resolutionChoice)
        assertEquals("Purple80", updatedFile.blocks[0].resolvedCode)
    }

    @Test
    fun testResolveConflictTheirs() {
        val file = ConflictFile(
            id = "conf_1",
            name = "Theme.kt",
            path = "app/src/main/java/com/vvf/smartfilemanager/ui/theme/Theme.kt",
            blocks = listOf(
                ConflictBlock(
                    id = "block_1",
                    lineStart = 15,
                    currentCode = "Purple80",
                    incomingCode = "ColorSkywalker",
                    description = "Brand colors"
                )
            )
        )

        val updatedBlocks = file.blocks.map { block ->
            if (block.id == "block_1") {
                block.copy(resolutionChoice = "theirs", resolvedCode = block.incomingCode)
            } else block
        }
        val updatedFile = file.copy(blocks = updatedBlocks, isFullyResolved = updatedBlocks.all { it.resolutionChoice != null })

        assertTrue(updatedFile.isFullyResolved)
        assertEquals("theirs", updatedFile.blocks[0].resolutionChoice)
        assertEquals("ColorSkywalker", updatedFile.blocks[0].resolvedCode)
    }
}
