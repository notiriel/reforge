package ch.riesennet.reforge

import ch.riesennet.reforge.operation.OperationRegistry
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class OperationRegistryTest {

    @Test
    fun `get returns MoveOperation for move type`() {
        val op = OperationRegistry.get("move")
        assertEquals("move", op.type)
    }

    @Test
    fun `get returns ExtractInterfaceOperation for extract-interface type`() {
        val op = OperationRegistry.get("extract-interface")
        assertEquals("extract-interface", op.type)
    }

    @Test
    fun `get returns ReplaceDependencyOperation for replace-dependency type`() {
        val op = OperationRegistry.get("replace-dependency")
        assertEquals("replace-dependency", op.type)
    }

    @Test
    fun `get throws for unknown type with helpful message`() {
        val ex = assertThrows(IllegalArgumentException::class.java) {
            OperationRegistry.get("unknown-op")
        }
        assertTrue(ex.message!!.contains("Unknown operation type: 'unknown-op'"))
        assertTrue(ex.message!!.contains("move"))
        assertTrue(ex.message!!.contains("extract-interface"))
        assertTrue(ex.message!!.contains("replace-dependency"))
    }

    @Test
    fun `knownTypes returns all three operation types`() {
        val types = OperationRegistry.knownTypes()
        assertEquals(setOf("move", "extract-interface", "replace-dependency"), types)
    }
}
