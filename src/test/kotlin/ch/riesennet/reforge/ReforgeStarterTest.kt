package ch.riesennet.reforge

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ReforgeStarterTest {

    private val starter = ReforgeStarter()

    // --- parseArgs tests ---

    @Test
    fun `parseArgs with project and config paths`() {
        val args = starter.parseArgs(listOf("/path/to/project", "/path/to/config.yaml"))

        assertEquals("/path/to/project", args.projectPath)
        assertEquals("/path/to/config.yaml", args.configPath)
        assertFalse(args.dryRun)
    }

    @Test
    fun `parseArgs with dry-run flag`() {
        val args = starter.parseArgs(listOf("/project", "/config.yaml", "--dry-run"))

        assertEquals("/project", args.projectPath)
        assertEquals("/config.yaml", args.configPath)
        assertTrue(args.dryRun)
    }

    @Test
    fun `parseArgs with dry-run flag in middle`() {
        // dry-run detection uses contains(), so position doesn't matter for the flag
        // but project and config are positional
        val args = starter.parseArgs(listOf("/project", "/config.yaml", "--dry-run"))
        assertTrue(args.dryRun)
    }

    @Test
    fun `parseArgs throws with missing arguments`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            starter.parseArgs(listOf("/project"))
        }
        assertEquals("Missing required arguments", ex.message)
    }

    @Test
    fun `parseArgs throws with empty arguments`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            starter.parseArgs(emptyList())
        }
        assertEquals("Missing required arguments", ex.message)
    }

    // --- groupIntoBatches tests ---

    @Test
    fun `groupIntoBatches with empty list`() {
        val batches = starter.groupIntoBatches(emptyList())
        assertTrue(batches.isEmpty())
    }

    @Test
    fun `groupIntoBatches groups consecutive same-type ops`() {
        val ops = listOf(
            RawOperation("move", mapOf("target" to "a")),
            RawOperation("move", mapOf("target" to "b")),
            RawOperation("move", mapOf("target" to "c"))
        )

        val batches = starter.groupIntoBatches(ops)

        assertEquals(1, batches.size)
        assertEquals("move", batches[0].type)
        assertEquals(3, batches[0].entries.size)
    }

    @Test
    fun `groupIntoBatches splits different types into separate batches`() {
        val ops = listOf(
            RawOperation("move", mapOf("target" to "a")),
            RawOperation("extract-interface", mapOf("class" to "b")),
            RawOperation("replace-dependency", mapOf("in" to "c"))
        )

        val batches = starter.groupIntoBatches(ops)

        assertEquals(3, batches.size)
        assertEquals("move", batches[0].type)
        assertEquals("extract-interface", batches[1].type)
        assertEquals("replace-dependency", batches[2].type)
        assertEquals(1, batches[0].entries.size)
        assertEquals(1, batches[1].entries.size)
        assertEquals(1, batches[2].entries.size)
    }

    @Test
    fun `groupIntoBatches handles alternating types`() {
        val ops = listOf(
            RawOperation("move", mapOf("a" to "1")),
            RawOperation("extract-interface", mapOf("b" to "2")),
            RawOperation("move", mapOf("c" to "3"))
        )

        val batches = starter.groupIntoBatches(ops)

        assertEquals(3, batches.size)
        assertEquals("move", batches[0].type)
        assertEquals("extract-interface", batches[1].type)
        assertEquals("move", batches[2].type)
    }

    @Test
    fun `groupIntoBatches single operation`() {
        val ops = listOf(RawOperation("move", mapOf("target" to "a")))

        val batches = starter.groupIntoBatches(ops)

        assertEquals(1, batches.size)
        assertEquals("move", batches[0].type)
        assertEquals(1, batches[0].entries.size)
    }

    @Test
    fun `groupIntoBatches preserves operation order within batch`() {
        val ops = listOf(
            RawOperation("move", mapOf("target" to "first")),
            RawOperation("move", mapOf("target" to "second")),
            RawOperation("move", mapOf("target" to "third"))
        )

        val batches = starter.groupIntoBatches(ops)
        val entries = batches[0].entries

        assertEquals("first", entries[0].fields["target"])
        assertEquals("second", entries[1].fields["target"])
        assertEquals("third", entries[2].fields["target"])
    }
}
